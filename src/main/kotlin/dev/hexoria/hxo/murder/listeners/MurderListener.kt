package dev.hexoria.hxo.murder.listeners

import dev.hexoria.hxo.murder.ConfigManager
import dev.hexoria.hxo.murder.GoldPoint
import dev.hexoria.hxo.murder.MurderGame
import dev.hexoria.hxo.murder.SpawnPoint
import dev.hexoria.hxo.murder.gui.ArrowSkinGui
import dev.hexoria.hxo.murder.gui.BowSkinGui
import dev.hexoria.hxo.murder.gui.KnifeSkinGui
import dev.hexoria.hxo.murder.gui.MurderMainGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.roundToInt

class MurderListener(private val plugin: JavaPlugin) : Listener {

    val spawnSetterPlayers = mutableMapOf<UUID, Int>()
    val goldSetterPlayers  = mutableMapOf<UUID, Int>()

    private val goldDisplayKey = NamespacedKey(plugin, "gold_display")
    private val goldBowKey     = NamespacedKey(plugin, "gold_bow")
    private val lobbyMenuKey   = NamespacedKey(plugin, "lobby_menu")

    companion object {
        private const val MAX_ACTIVE_GOLD   = 15
        private const val GOLD_GOAL         = 8
        private const val MIN_AUTO_PLAYERS  = 4
        private const val PRE_GAME_SECONDS  = 10
    }

    // ── Item-Factories ───────────────────────────────────────────────────────

    private fun makeWorldGoldItem(): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET)
        val meta = item.itemMeta
        meta.displayName(Component.text("✦ Gold", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
        meta.persistentDataContainer.set(goldDisplayKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    private fun makeGoldDisplayItem(count: Int): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET, count.coerceIn(1, 64))
        val meta = item.itemMeta
        meta.displayName(
            Component.text("✦ Gold $count/$GOLD_GOAL", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true)
        )
        meta.persistentDataContainer.set(goldDisplayKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    private fun makeGoldBowItem(playerUuid: UUID): ItemStack {
        val arrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[playerUuid] ?: 0) { ArrowSkinGui.SKINS[0] }
        val item = ItemStack(Material.BOW)
        val meta = item.itemMeta
        meta.displayName(Component.text("✦ ${arrowSkin.displayName} [Gold-Bogen]", arrowSkin.color).decoration(TextDecoration.ITALIC, false))
        meta.persistentDataContainer.set(goldBowKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    private fun isGoldDisplayItem(item: ItemStack) =
        item.type == Material.GOLD_NUGGET &&
        item.itemMeta?.persistentDataContainer?.has(goldDisplayKey) == true

    private fun makeLobbyItem(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta
        meta.displayName(Component.text("◆ Menu", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
        meta.lore(listOf(Component.text("Rechtsklick zum Öffnen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        meta.persistentDataContainer.set(lobbyMenuKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    private fun giveLobbyItem(player: Player) {
        val existing = player.inventory.getItem(8)
        if (existing == null || existing.itemMeta?.persistentDataContainer?.has(lobbyMenuKey) != true) {
            player.inventory.setItem(8, makeLobbyItem())
        }
    }

    // ── Gewichtete Rollenauswahl ──────────────────────────────────────────────

    private fun weightedPick(players: List<Player>, weightFn: (Player) -> Double): Player {
        val weights = players.map { weightFn(it) }
        val total   = weights.sum()
        var rand    = Math.random() * total
        for ((i, w) in weights.withIndex()) {
            rand -= w
            if (rand <= 0) return players[i]
        }
        return players.last()
    }

    private fun murderWeight(p: Player, n: Int): Double {
        val g = MurderGame.playerTotalGames.getOrDefault(p.uniqueId, 0)
        val k = MurderGame.playerMurderCount.getOrDefault(p.uniqueId, 0)
        return if (g == 0) 1.0 else maxOf(0.1, 1.0 - k.toDouble() * n / g)
    }

    private fun detectiveWeight(p: Player, n: Int): Double {
        val g = MurderGame.playerTotalGames.getOrDefault(p.uniqueId, 0)
        val k = MurderGame.playerDetectiveCount.getOrDefault(p.uniqueId, 0)
        return if (g == 0) 1.0 else maxOf(0.1, 1.0 - k.toDouble() * n / g)
    }

    /** Returns (murderPct, detectivePct, innocentPct) rounded for display. */
    private fun rolePercents(player: Player, allLobby: List<Player>): Triple<Int, Int, Int> {
        val n = allLobby.size
        if (n < 2) return Triple(0, 0, 100)
        val mWeights  = allLobby.map { murderWeight(it, n) };    val mTotal = mWeights.sum()
        val dWeights  = allLobby.map { detectiveWeight(it, n) }; val dTotal = dWeights.sum()
        val idx = allLobby.indexOf(player)
        val mProb = mWeights[idx] / mTotal
        val dProb = dWeights[idx] / dTotal * (1.0 - mProb)
        val iProb = maxOf(0.0, 1.0 - mProb - dProb)
        return Triple((mProb * 100).roundToInt(), (dProb * 100).roundToInt(), (iProb * 100).roundToInt())
    }

    // ── Auto-Start ────────────────────────────────────────────────────────────

    fun checkAutoStart() {
        if (MurderGame.running) return
        val needed  = maxOf(MIN_AUTO_PLAYERS, MurderGame.configuredMaxPlayers)
        val lobby   = plugin.server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }
        if (lobby.size >= needed && MurderGame.preGameTask == null) {
            startPreGameCountdown(lobby)
        } else if (lobby.size < needed && MurderGame.preGameTask != null) {
            cancelPreGameCountdown()
            for (p in plugin.server.onlinePlayers) {
                p.sendActionBar(Component.text("Zu wenige Spieler – Countdown abgebrochen!", NamedTextColor.RED))
            }
        }
    }

    private fun startPreGameCountdown(initialLobby: List<Player>) {
        var secondsLeft = PRE_GAME_SECONDS
        MurderGame.preGameTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val lobby = plugin.server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }
            val needed = maxOf(MIN_AUTO_PLAYERS, MurderGame.configuredMaxPlayers)
            if (lobby.size < needed || MurderGame.running) {
                cancelPreGameCountdown()
                for (p in plugin.server.onlinePlayers) {
                    p.sendActionBar(Component.text("Zu wenige Spieler – Countdown abgebrochen!", NamedTextColor.RED))
                }
                return@Runnable
            }
            if (secondsLeft <= 0) {
                cancelPreGameCountdown()
                startGame(plugin.server.consoleSender, null)
                return@Runnable
            }
            for (p in lobby) {
                val (mPct, dPct, iPct) = rolePercents(p, lobby)
                p.sendActionBar(
                    Component.text("§c§lMörder: $mPct%  §a§lDetektiv: $dPct%  §7§lUnschuldig: $iPct%  §e§l➤ ${secondsLeft}s")
                )
            }
            secondsLeft--
        }, 0L, 20L)
    }

    private fun cancelPreGameCountdown() {
        MurderGame.preGameTask?.cancel()
        MurderGame.preGameTask = null
    }

    // ── Spiel starten (öffentlich, auch von MurderCommand aufrufbar) ──────────

    fun startGame(sender: CommandSender, maxSpieler: Int?) {
        if (MurderGame.running) { sender.sendMessage("§c[Murder] §fEs läuft bereits ein Spiel!"); return }
        if (MurderGame.spawnPoints.isEmpty()) { sender.sendMessage("§c[Murder] §fKeine Spawnpunkte gesetzt!"); return }

        cancelPreGameCountdown()

        val allePlayers = plugin.server.onlinePlayers
            .filter { it.gameMode != GameMode.SPECTATOR }
            .toMutableList()
        if (allePlayers.size < 2) {
            sender.sendMessage("§c[Murder] §fMindestens §e2 Spieler §fwerden benötigt! (Lobby: ${allePlayers.size})")
            return
        }

        val shuffledSpawns = MurderGame.spawnPoints.values.toMutableList().also { it.shuffle() }
        val limit         = maxSpieler ?: MurderGame.configuredMaxPlayers
        val maxTeilnehmer = minOf(limit, shuffledSpawns.size, allePlayers.size)
        if (maxTeilnehmer < 2) { sender.sendMessage("§c[Murder] §fNicht genug Spawnpunkte für mindestens 2 Spieler!"); return }

        val teilnehmer = allePlayers.shuffled().take(maxTeilnehmer)
        val zuschauer  = allePlayers.drop(maxTeilnehmer)

        // Gewichtete Rollenauswahl
        val murder    = weightedPick(teilnehmer) { murderWeight(it, teilnehmer.size) }
        val remaining = teilnehmer.filter { it != murder }
        val detective = weightedPick(remaining) { detectiveWeight(it, remaining.size) }
        val innocents = teilnehmer.filter { it != murder && it != detective }

        MurderGame.murderId    = murder.uniqueId
        MurderGame.detectiveId = detective.uniqueId
        MurderGame.alivePlayers.addAll(teilnehmer.map { it.uniqueId })
        MurderGame.allGamePlayers.addAll(teilnehmer.map { it.uniqueId })
        MurderGame.running = true
        MurderGame.detectiveArrowReady = true

        val shuffledForTp = shuffledSpawns.toMutableList().also { it.shuffle() }
        teilnehmer.forEachIndexed { i, player ->
            player.teleport(shuffledForTp[i].location)
            player.gameMode = GameMode.ADVENTURE
            player.inventory.clear()
            player.foodLevel = 20; player.saturation = 20f
            player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false))
        }
        MurderGame.frozenPlayers.addAll(teilnehmer.map { it.uniqueId })
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (MurderGame.running) MurderGame.frozenPlayers.removeAll(teilnehmer.map { it.uniqueId }.toSet())
        }, 100L)

        // ── Messer-Countdown (sichtbar für ALLE) ─────────────────────────────
        val murderWeapon  = MurderGame.playerKnifeSkins[murder.uniqueId] ?: Material.IRON_SWORD
        val murdererUuid  = murder.uniqueId
        var knifeCountdown = 5
        murder.sendMessage("§c§l[Murder] §r§fDu bist der §c§lMörder§r§f! Töte alle anderen!")
        MurderGame.knifeCountdownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!MurderGame.running) { MurderGame.knifeCountdownTask?.cancel(); MurderGame.knifeCountdownTask = null; return@Runnable }
            val murderer = plugin.server.getPlayer(murdererUuid)
            if (knifeCountdown > 0) {
                for (uuid in MurderGame.alivePlayers) {
                    val p = plugin.server.getPlayer(uuid) ?: continue
                    if (p.uniqueId == murdererUuid) {
                        p.sendActionBar(Component.text("Messer in ", NamedTextColor.RED)
                            .append(Component.text("$knifeCountdown", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                            .append(Component.text("...", NamedTextColor.RED)))
                    } else {
                        p.sendActionBar(Component.text("Der Mörder erhält sein Messer in ", NamedTextColor.YELLOW)
                            .append(Component.text("$knifeCountdown", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                            .append(Component.text("s!", NamedTextColor.YELLOW)))
                    }
                }
                knifeCountdown--
            } else {
                MurderGame.knifeCountdownTask?.cancel(); MurderGame.knifeCountdownTask = null
                murderer?.inventory?.setItem(1, ItemStack(murderWeapon))
                for (uuid in MurderGame.alivePlayers) {
                    val p = plugin.server.getPlayer(uuid) ?: continue
                    if (p.uniqueId == murdererUuid) {
                        p.sendActionBar(Component.text("Messer erhalten!", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                    } else {
                        p.sendActionBar(Component.text("Das Spiel hat begonnen! Vorsicht vor dem Mörder!", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                    }
                }
            }
        }, 100L, 20L)

        // ── Detektiv-Ausrüstung ───────────────────────────────────────────────
        val bowSkin   = BowSkinGui.SKINS.getOrElse(MurderGame.playerBowSkinIndex[detective.uniqueId] ?: 0) { BowSkinGui.SKINS[0] }
        val arrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[detective.uniqueId] ?: 0) { ArrowSkinGui.SKINS[0] }
        detective.inventory.setItem(1, BowSkinGui.makeBowItem(bowSkin))
        detective.inventory.setItem(2, ArrowSkinGui.makeArrowItem(arrowSkin))
        detective.sendMessage("§a§l[Murder] §r§fDu bist der §a§lDetektiv§r§f! Jeder Schuss = One-Shot. Cooldown: 10s")

        // ── Mörder-Bogen (ab Start im Inv, nicht durch Gold) ─────────────────
        val murderBowSkin = BowSkinGui.SKINS.getOrElse(MurderGame.playerBowSkinIndex[murder.uniqueId] ?: 0) { BowSkinGui.SKINS[0] }
        murder.inventory.setItem(2, BowSkinGui.makeBowItem(murderBowSkin))
        if (murderBowSkin.weaponMaterial != Material.TRIDENT) {
            val murderArrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[murder.uniqueId] ?: 0) { ArrowSkinGui.SKINS[0] }
            murder.inventory.setItem(3, ArrowSkinGui.makeArrowItem(murderArrowSkin))
        }
        innocents.forEach { it.sendMessage("§7§l[Murder] §r§fDu bist §7§lunschuldig§r§f. Überlebe!") }
        zuschauer.forEach { it.gameMode = GameMode.SPECTATOR; it.sendMessage("§c[Murder] §fDie Runde ist voll, bitte warte!") }

        val roleReceivers = plugin.server.onlinePlayers.filter { it.hasPermission("hxo-murder-see-roles") }
        val sep = Component.text("                              ", NamedTextColor.DARK_GRAY).decorate(TextDecoration.STRIKETHROUGH)
        roleReceivers.forEach { p ->
            p.sendMessage(sep)
            p.sendMessage(Component.text("Mörder: ", NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(murder.name, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
            p.sendMessage(Component.text("Detektiv: ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD).append(Component.text(detective.name, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
            if (innocents.isNotEmpty()) p.sendMessage(Component.text("Unschuldige: ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(innocents.joinToString(", ") { it.name }, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
            p.sendMessage(sep)
        }

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (MurderGame.running) initGoldRound()
        }, 100L)
    }

    // ── Gold-Runde initialisieren ─────────────────────────────────────────────

    fun initGoldRound() {
        if (MurderGame.goldPoints.isEmpty()) return
        val keys = MurderGame.goldPoints.keys.toMutableList().also { it.shuffle() }
        MurderGame.goldPointPool.addAll(keys)
        repeat(minOf(MAX_ACTIVE_GOLD, MurderGame.goldPointPool.size)) { spawnNextGold() }
    }

    private fun spawnNextGold() {
        while (MurderGame.goldPointPool.isNotEmpty()) {
            val key  = MurderGame.goldPointPool.removeFirst()
            if (key in MurderGame.activeGoldPointIndices) continue
            val gold = MurderGame.goldPoints[key] ?: continue
            val world = gold.location.world ?: continue
            val entity = world.dropItem(gold.location.clone(), makeWorldGoldItem())
            entity.pickupDelay = 10
            entity.velocity = Vector(0.0, 0.0, 0.0)
            entity.isCustomNameVisible = false
            MurderGame.activeGoldDrops[entity.uniqueId] = key
            MurderGame.activeGoldPointIndices.add(key)
            break
        }
    }

    // ── Gold-Wand ────────────────────────────────────────────────────────────

    fun giveGoldWand(player: Player) {
        val next = (MurderGame.goldPoints.keys.maxOrNull() ?: 0) + 1
        val wand = ItemStack(Material.GOLDEN_HOE)
        val meta = wand.itemMeta
        meta.displayName(Component.text("Goldpunkt-Setzer [Nächster: Gold #$next]", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
        meta.lore(listOf(Component.text("Rechtsklick → Goldpunkt setzen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        wand.itemMeta = meta
        player.inventory.addItem(wand)
        goldSetterPlayers[player.uniqueId] = next
        player.sendMessage(Component.text("[Murder] ", NamedTextColor.GOLD)
            .append(Component.text("Goldpunkt-Setzer erhalten! Rechtsklick zum Setzen (ab Gold #$next).", NamedTextColor.GREEN)))
    }

    // ── Gold-Bogen vergeben ───────────────────────────────────────────────────

    private fun giveGoldBow(player: Player) {
        MurderGame.playerHasGoldBow.add(player.uniqueId)
        MurderGame.goldBowPlayers.add(player.uniqueId)
        player.inventory.setItem(4, null)
        val arrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[player.uniqueId] ?: 0) { ArrowSkinGui.SKINS[0] }
        player.inventory.addItem(makeGoldBowItem(player.uniqueId))
        player.inventory.setItem(3, ArrowSkinGui.makeArrowItem(arrowSkin))
        player.sendMessage(Component.text("[Murder] ", NamedTextColor.GOLD)
            .append(Component.text("Du hast 8 Gold gesammelt und einen Bogen erhalten!", NamedTextColor.YELLOW)))
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
    }

    // ── Gold-Aufnahme ─────────────────────────────────────────────────────────

    private fun handleGoldPickup(player: Player, itemEntity: org.bukkit.entity.Item, goldKey: Int) {
        val slot4 = player.inventory.getItem(4)
        val hasGoldDisplay = slot4 != null && isGoldDisplayItem(slot4)

        if (!hasGoldDisplay && slot4 != null) {
            player.inventory.setItem(4, null)
            val leftover = player.inventory.addItem(slot4)
            if (leftover.isNotEmpty()) {
                player.inventory.setItem(4, slot4)
                player.world.dropItemNaturally(itemEntity.location, makeWorldGoldItem())
                itemEntity.remove()
                MurderGame.activeGoldDrops.remove(itemEntity.uniqueId)
                MurderGame.activeGoldPointIndices.remove(goldKey)
                player.sendActionBar(Component.text("Inventar voll! Gold gedroppt.", NamedTextColor.RED))
                spawnNextGoldIfNeeded()
                return
            }
        }

        MurderGame.activeGoldDrops.remove(itemEntity.uniqueId)
        MurderGame.activeGoldPointIndices.remove(goldKey)
        itemEntity.remove()

        val count = (MurderGame.playerGoldCount[player.uniqueId] ?: 0) + 1
        MurderGame.playerGoldCount[player.uniqueId] = count
        player.inventory.setItem(4, makeGoldDisplayItem(count))

        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
        player.spawnParticle(Particle.HAPPY_VILLAGER, player.location.clone().add(0.0, 1.0, 0.0), 12, 0.4, 0.4, 0.4, 0.0)
        player.sendActionBar(Component.text("Gold: $count/$GOLD_GOAL", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))

        if (count >= GOLD_GOAL && player.uniqueId !in MurderGame.playerHasGoldBow) {
            giveGoldBow(player)
        }
        spawnNextGoldIfNeeded()
    }

    private fun spawnNextGoldIfNeeded() {
        if (MurderGame.activeGoldDrops.size < MAX_ACTIVE_GOLD) spawnNextGold()
    }

    // ── Pfeil-Cooldown für Gold-Bogen ─────────────────────────────────────────

    private fun startGoldBowArrowCooldown(playerUuid: UUID) {
        MurderGame.goldBowArrowCooldownTasks[playerUuid]?.cancel()
        var secondsLeft = 10
        MurderGame.goldBowArrowCooldownTasks[playerUuid] = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!MurderGame.running) {
                MurderGame.goldBowArrowCooldownTasks[playerUuid]?.cancel()
                MurderGame.goldBowArrowCooldownTasks.remove(playerUuid)
                return@Runnable
            }
            val player = plugin.server.getPlayer(playerUuid) ?: return@Runnable
            if (secondsLeft <= 0) {
                MurderGame.goldBowArrowCooldownTasks[playerUuid]?.cancel()
                MurderGame.goldBowArrowCooldownTasks.remove(playerUuid)
                val arrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[playerUuid] ?: 0) { ArrowSkinGui.SKINS[0] }
                player.inventory.setItem(3, ArrowSkinGui.makeArrowItem(arrowSkin))
                player.sendActionBar(Component.text("Pfeil aufgeladen!", NamedTextColor.GREEN))
                return@Runnable
            }
            player.sendActionBar(
                Component.text("Neuer Pfeil in ", NamedTextColor.YELLOW)
                    .append(Component.text("$secondsLeft", NamedTextColor.RED))
                    .append(Component.text(" Sekunden", NamedTextColor.YELLOW))
            )
            secondsLeft--
        }, 0L, 20L)
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.inventory.clear()
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (MurderGame.showingBeacons) {
                for (spawn in MurderGame.spawnPoints.values)
                    for ((loc, mat) in MurderGame.fakeBeaconBlocks(spawn.location))
                        event.player.sendBlockChange(loc, mat.createBlockData())
            }
            if (MurderGame.showingGoldBeacons) {
                for (gold in MurderGame.goldPoints.values)
                    for ((loc, mat) in MurderGame.fakeGoldBeaconBlocks(gold.location))
                        event.player.sendBlockChange(loc, mat.createBlockData())
            }
        }, 5L)
        if (!MurderGame.running) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                event.player.gameMode = GameMode.ADVENTURE
                if (MurderGame.lobbySpawn != null) teleportToLobby(event.player)
                else giveLobbyItem(event.player)
                checkAutoStart()
            }, 15L)
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (player.uniqueId in MurderGame.alivePlayers) event.isCancelled = true
    }

    @EventHandler
    fun onFallDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (player.uniqueId in MurderGame.alivePlayers && event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemDamage(event: PlayerItemDamageEvent) {
        if (event.player.uniqueId in MurderGame.alivePlayers) event.isCancelled = true
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!MurderGame.running) return
        val player = event.player
        if (player.uniqueId in MurderGame.frozenPlayers) {
            val from = event.from; val to = event.to
            if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) event.setTo(from)
            return
        }
        if (MurderGame.playersNearBowOnDrop.isEmpty()) return
        for ((bowId, restricted) in MurderGame.playersNearBowOnDrop) {
            if (player.uniqueId !in restricted) continue
            val dropLoc = MurderGame.bowDropLocations[bowId] ?: continue
            if (player.world != dropLoc.world) continue
            if (player.location.distanceSquared(dropLoc) > 16.0) restricted.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        event.deathMessage(null)
        if (!MurderGame.running) return
        val dead = event.entity
        if (dead.uniqueId !in MurderGame.alivePlayers) return

        if (dead.uniqueId == MurderGame.detectiveId) {
            MurderGame.arrowCooldownTask?.cancel(); MurderGame.arrowCooldownTask = null
        }
        MurderGame.goldBowArrowCooldownTasks[dead.uniqueId]?.cancel()
        MurderGame.goldBowArrowCooldownTasks.remove(dead.uniqueId)
        MurderGame.goldBowPlayers.remove(dead.uniqueId)

        val innocentsAfter = MurderGame.alivePlayers.count { it != MurderGame.murderId && it != dead.uniqueId }
        val isGameEnding   = dead.uniqueId == MurderGame.murderId || innocentsAfter == 0

        // Nur der Detektiv lässt seinen Bogen fallen, nicht andere Spieler
        if (!isGameEnding && dead.uniqueId == MurderGame.detectiveId) {
            val bowItem = dead.inventory.getItem(1)
            if (bowItem != null && (bowItem.type == Material.BOW || bowItem.type == Material.TRIDENT)) {
                val dropped = dead.world.dropItemNaturally(dead.location, bowItem)
                dropped.pickupDelay = 0
                MurderGame.droppedDetectiveBows.add(dropped.uniqueId)
                MurderGame.bowDropLocations[dropped.uniqueId] = dead.location.clone()
                dead.world.players.filter {
                    it.uniqueId in MurderGame.alivePlayers && it.uniqueId != dead.uniqueId &&
                    it.world == dead.world && it.location.distanceSquared(dead.location) <= 16.0
                }.forEach { MurderGame.playersNearBowOnDrop.getOrPut(dropped.uniqueId) { mutableSetOf() }.add(it.uniqueId) }
            }
        }

        event.drops.clear(); event.droppedExp = 0
        MurderGame.alivePlayers.remove(dead.uniqueId)
        MurderGame.frozenPlayers.remove(dead.uniqueId)

        if (dead.uniqueId == MurderGame.murderId) { endInternal(draw = false, murderWins = false); return }
        if (MurderGame.alivePlayers.count { it != MurderGame.murderId } == 0) endInternal(draw = false, murderWins = true)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (!MurderGame.running) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable { checkAutoStart() }, 1L)
            return
        }
        if (player.uniqueId !in MurderGame.alivePlayers) return

        MurderGame.alivePlayers.remove(player.uniqueId)
        MurderGame.frozenPlayers.remove(player.uniqueId)

        if (player.uniqueId == MurderGame.detectiveId) {
            MurderGame.arrowCooldownTask?.cancel(); MurderGame.arrowCooldownTask = null
        }
        MurderGame.goldBowArrowCooldownTasks[player.uniqueId]?.cancel()
        MurderGame.goldBowArrowCooldownTasks.remove(player.uniqueId)
        MurderGame.goldBowPlayers.remove(player.uniqueId)
        for (restricted in MurderGame.playersNearBowOnDrop.values) restricted.remove(player.uniqueId)

        if (player.uniqueId == MurderGame.murderId) { endInternal(draw = false, murderWins = false); return }
        if (MurderGame.alivePlayers.count { it != MurderGame.murderId } == 0) endInternal(draw = false, murderWins = true)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (!MurderGame.running) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable { player.gameMode = GameMode.SPECTATOR }, 1L)
            return
        }
        if (player.uniqueId !in MurderGame.alivePlayers) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("§c[Murder] §fDu wurdest eliminiert – beobachte als Zuschauer!")
            }, 1L)
        }
    }

    // ── Spawn-Setzer Wand ─────────────────────────────────────────────────────

    fun giveSpawnWand(player: Player) {
        val next = (MurderGame.spawnPoints.keys.maxOrNull() ?: 0) + 1
        val wand = ItemStack(Material.WOODEN_HOE)
        val meta = wand.itemMeta
        meta.displayName(Component.text("Spawnpunkt-Setzer [Nächster: Spawn #$next]", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
        meta.lore(listOf(Component.text("Rechtsklick → Spawn setzen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        wand.itemMeta = meta
        player.inventory.addItem(wand)
        spawnSetterPlayers[player.uniqueId] = next
        player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED)
            .append(Component.text("Spawn-Setzer erhalten! Rechtsklick zum Setzen (ab Spawn #$next).", NamedTextColor.GREEN)))
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player

        // Spawn-Setzer Wand
        if (player.uniqueId in spawnSetterPlayers &&
            (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            val hand = player.inventory.itemInMainHand
            if (hand.type == Material.WOODEN_HOE && hand.itemMeta?.hasDisplayName() == true) {
                event.isCancelled = true
                val nextNum = spawnSetterPlayers[player.uniqueId]!!
                if (nextNum > 10) {
                    player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED).append(Component.text("Maximal 10 Spawnpunkte!", NamedTextColor.RED)))
                    return
                }
                MurderGame.spawnPoints[nextNum] = SpawnPoint(player.location.clone(), "Spawn #$nextNum")
                ConfigManager.saveAll()
                val next = nextNum + 1
                spawnSetterPlayers[player.uniqueId] = next
                val newMeta = hand.itemMeta!!
                newMeta.displayName(Component.text("Spawnpunkt-Setzer [Nächster: Spawn #$next]", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
                hand.itemMeta = newMeta
                player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED).append(Component.text("Spawn #$nextNum gesetzt!", NamedTextColor.GREEN)))
                return
            }
        }

        // Goldpunkt-Setzer Wand
        if (player.uniqueId in goldSetterPlayers &&
            (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            val hand = player.inventory.itemInMainHand
            if (hand.type == Material.GOLDEN_HOE && hand.itemMeta?.hasDisplayName() == true) {
                event.isCancelled = true
                val nextNum = goldSetterPlayers[player.uniqueId]!!
                if (nextNum > 100) {
                    player.sendMessage(Component.text("[Murder] ", NamedTextColor.GOLD).append(Component.text("Maximal 100 Goldpunkte!", NamedTextColor.RED)))
                    return
                }
                MurderGame.goldPoints[nextNum] = GoldPoint(player.location.clone(), "Gold #$nextNum")
                ConfigManager.saveAll()
                val next = nextNum + 1
                goldSetterPlayers[player.uniqueId] = next
                val newMeta = hand.itemMeta!!
                newMeta.displayName(Component.text("Goldpunkt-Setzer [Nächster: Gold #$next]", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
                hand.itemMeta = newMeta
                player.sendMessage(Component.text("[Murder] ", NamedTextColor.GOLD).append(Component.text("Goldpunkt #$nextNum gesetzt!", NamedTextColor.GREEN)))
                return
            }
        }

        // Lobby Menu Paper
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            val hand = player.inventory.itemInMainHand
            if (hand.type == Material.PAPER &&
                hand.itemMeta?.persistentDataContainer?.has(lobbyMenuKey) == true) {
                event.isCancelled = true
                MurderMainGui.open(player)
                return
            }
        }

        if (!MurderGame.running) return
        if (player.uniqueId !in MurderGame.frozenPlayers) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val handType = player.inventory.itemInMainHand.type
        if (handType != Material.BOW && handType != Material.TRIDENT) return
        event.isCancelled = true; player.updateInventory()
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val dropped = event.itemDrop.itemStack
        // Lobby-Paper nie droppbar
        if (dropped.itemMeta?.persistentDataContainer?.has(lobbyMenuKey) == true) {
            event.isCancelled = true
            return
        }
        if (event.player.uniqueId !in MurderGame.alivePlayers) return
        // Gold-Display-Item darf gedroppt werden; dabei Gold-Count zurücksetzen
        if (isGoldDisplayItem(dropped)) {
            MurderGame.playerGoldCount.remove(event.player.uniqueId)
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onBowShoot(event: EntityShootBowEvent) {
        if (!MurderGame.running) return
        val player = event.entity as? Player ?: return
        if (player.uniqueId in MurderGame.frozenPlayers) { event.isCancelled = true; return }
        if (player.uniqueId !in MurderGame.alivePlayers) { event.isCancelled = true; return }

        val isGoldBow = event.bow?.itemMeta?.persistentDataContainer?.has(goldBowKey) == true
        when {
            isGoldBow && player.uniqueId in MurderGame.goldBowPlayers -> startGoldBowArrowCooldown(player.uniqueId)
            !isGoldBow && player.uniqueId == MurderGame.detectiveId && MurderGame.detectiveArrowReady -> startArrowCooldown(player.uniqueId)
        }
    }

    @EventHandler
    fun onTridentLaunch(event: ProjectileLaunchEvent) {
        if (!MurderGame.running) return
        val trident = event.entity as? Trident ?: return
        val player  = trident.shooter as? Player ?: return
        if (player.uniqueId in MurderGame.frozenPlayers) { event.isCancelled = true; return }
        if (player.uniqueId !in MurderGame.alivePlayers) { event.isCancelled = true; return }
        if (player.uniqueId == MurderGame.detectiveId && !MurderGame.detectiveArrowReady) event.isCancelled = true
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!MurderGame.running) { event.isCancelled = true; return }
        val victim  = event.entity as? Player ?: return
        if (victim.uniqueId !in MurderGame.alivePlayers) return
        val damager = event.damager

        if (damager is Player && damager.uniqueId == MurderGame.murderId) {
            if (damager.inventory.itemInMainHand.type in KnifeSkinGui.WEAPON_MATERIALS) event.damage = 1000.0
            return
        }

        if (damager is Arrow || damager is Trident) {
            val shooter = (damager as Projectile).shooter as? Player
            if (shooter == null || shooter.uniqueId !in MurderGame.alivePlayers || shooter.uniqueId == MurderGame.murderId) {
                event.isCancelled = true; return
            }
            when {
                victim.uniqueId == MurderGame.murderId -> { event.isCancelled = false; event.damage = 1000.0 }
                shooter.uniqueId == MurderGame.detectiveId -> { event.isCancelled = false; event.damage = 1000.0 }
                shooter.uniqueId in MurderGame.goldBowPlayers -> { event.isCancelled = false; event.damage = 1000.0 }
                shooter.uniqueId == MurderGame.murderId -> { event.isCancelled = false; event.damage = 1000.0 }
                else -> event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        if (!MurderGame.running) return
        val projectile = event.entity
        if (projectile !is Arrow && projectile !is Trident) return
        val shooter = projectile.shooter as? Player ?: return

        // ── Gold-Bogen ───────────────────────────────────────────────────────
        if (shooter.uniqueId in MurderGame.goldBowPlayers) {
            val hitMurderer  = (event.hitEntity as? Player)?.uniqueId == MurderGame.murderId
            val hitInnocent  = event.hitEntity is Player && !hitMurderer
            when {
                hitInnocent -> {
                    // Unschuldigen getroffen → permanent kein Pfeil mehr
                    MurderGame.goldBowArrowCooldownTasks[shooter.uniqueId]?.cancel()
                    MurderGame.goldBowArrowCooldownTasks.remove(shooter.uniqueId)
                    plugin.server.getPlayer(shooter.uniqueId)?.sendActionBar(
                        Component.text("Unschuldigen getroffen! ", NamedTextColor.RED)
                            .append(Component.text("Kein Pfeil mehr.", NamedTextColor.DARK_RED))
                    )
                }
                !hitMurderer -> {
                    // Block/Luft verfehlt → Cooldown läuft weiter, Pfeil kommt zurück
                    plugin.server.getPlayer(shooter.uniqueId)?.sendActionBar(
                        Component.text("Danebengeschossen!", NamedTextColor.YELLOW)
                    )
                }
            }
            plugin.server.scheduler.runTaskLater(plugin, Runnable { projectile.remove() }, 2L)
            return
        }

        // ── Nicht-Detektiv (z.B. Unschuldiger mit Detektiv-Bogen) ────────────
        if (shooter.uniqueId != MurderGame.detectiveId) {
            if (projectile is Trident && event.hitEntity == null) projectile.remove()
            else if (projectile !is Trident) plugin.server.scheduler.runTaskLater(plugin, Runnable { projectile.remove() }, 2L)
            return
        }

        // ── Detektiv ─────────────────────────────────────────────────────────
        val hitMurderer = (event.hitEntity as? Player)?.uniqueId == MurderGame.murderId
        val hitInnocent = event.hitEntity is Player && !hitMurderer
        when {
            hitInnocent -> {
                MurderGame.arrowCooldownTask?.cancel()
                MurderGame.arrowCooldownTask = null
                val det = plugin.server.getPlayer(shooter.uniqueId)
                val detUuid = shooter.uniqueId
                // Detektiv für 5 Sekunden einfrieren
                MurderGame.frozenPlayers.add(detUuid)
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (MurderGame.running) MurderGame.frozenPlayers.remove(detUuid)
                }, 100L)
                // Bogen aus dem Inventar nehmen und droppen (nicht bei Dreizack im Flug)
                val bowSlot = det?.inventory?.let { inv ->
                    (0 until inv.size).firstOrNull { i ->
                        val item = inv.getItem(i)
                        item != null && (item.type == Material.BOW || item.type == Material.TRIDENT)
                    }
                }
                if (det != null && bowSlot != null) {
                    val bowItem = det.inventory.getItem(bowSlot)!!
                    det.inventory.setItem(bowSlot, null)
                    val dropLoc = (event.hitEntity as? Player)?.location ?: det.location
                    val droppedBow = dropLoc.world?.dropItem(dropLoc, bowItem)
                    if (droppedBow != null) {
                        droppedBow.pickupDelay = 200 // 10 Sekunden
                        droppedBow.velocity = Vector(0.0, 0.0, 0.0)
                        MurderGame.droppedDetectiveBows.add(droppedBow.uniqueId)
                        MurderGame.bowDropLocations[droppedBow.uniqueId] = dropLoc.clone()
                        dropLoc.world?.players?.filter {
                            it.uniqueId in MurderGame.alivePlayers && it.uniqueId != detUuid &&
                            it.location.distanceSquared(dropLoc) <= 16.0
                        }?.forEach {
                            MurderGame.playersNearBowOnDrop.getOrPut(droppedBow.uniqueId) { mutableSetOf() }.add(it.uniqueId)
                        }
                    }
                }
                det?.sendActionBar(
                    Component.text("Unschuldigen getroffen! ", NamedTextColor.RED)
                        .append(Component.text("Bogen verloren!", NamedTextColor.DARK_RED))
                )
            }
            !hitMurderer -> {
                // Block/Luft verfehlt → Cooldown läuft weiter, Pfeil kommt nach 10s zurück
                plugin.server.getPlayer(shooter.uniqueId)?.sendActionBar(
                    Component.text("Danebengeschossen!", NamedTextColor.YELLOW)
                )
                // Cooldown task bleibt aktiv – kein cancel
            }
        }
        if (projectile is Trident) projectile.remove()
        else plugin.server.scheduler.runTaskLater(plugin, Runnable { projectile.remove() }, 2L)
    }

    @EventHandler
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (!MurderGame.running) return
        val player = event.entity as? Player ?: return
        val itemId = event.item.uniqueId

        // Gold-Aufnahme (Welt-Gold von Goldpunkten)
        val goldKey = MurderGame.activeGoldDrops[itemId]
        if (goldKey != null) {
            event.isCancelled = true
            if (player.uniqueId != MurderGame.detectiveId && player.uniqueId != MurderGame.murderId) {
                handleGoldPickup(player, event.item, goldKey)
            }
            return
        }

        // Gedroppted Spieler-Gold (anderer Spieler hat seinen Gold-Stack fallengelassen)
        val droppedItem = event.item.itemStack
        if (isGoldDisplayItem(droppedItem) && player.uniqueId in MurderGame.alivePlayers &&
            player.uniqueId != MurderGame.detectiveId && player.uniqueId != MurderGame.murderId) {
            event.isCancelled = true
            val transferCount = droppedItem.amount
            event.item.remove()
            val prevCount = MurderGame.playerGoldCount[player.uniqueId] ?: 0
            val newCount = prevCount + transferCount
            MurderGame.playerGoldCount[player.uniqueId] = newCount
            player.inventory.setItem(4, makeGoldDisplayItem(newCount))
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.location.clone().add(0.0, 1.0, 0.0), 12, 0.4, 0.4, 0.4, 0.0)
            player.sendActionBar(Component.text("Gold: $newCount/$GOLD_GOAL", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
            if (newCount >= GOLD_GOAL && player.uniqueId !in MurderGame.playerHasGoldBow) {
                giveGoldBow(player)
            }
            return
        }

        // Detektiv-Pfeil-Cooldown
        if (player.uniqueId == MurderGame.detectiveId &&
            event.item.itemStack.type == Material.ARROW &&
            !MurderGame.detectiveArrowReady) {
            event.isCancelled = true; return
        }

        // Fallen gelassener Detektiv-Bogen
        if (itemId in MurderGame.droppedDetectiveBows) {
            if (player.uniqueId == MurderGame.murderId) { event.isCancelled = true; return }
            if (player.uniqueId == MurderGame.detectiveId && player.uniqueId in MurderGame.frozenPlayers) { event.isCancelled = true; return }
            if (player.uniqueId in (MurderGame.playersNearBowOnDrop[itemId] ?: emptySet<UUID>())) { event.isCancelled = true; return }

            val skinIndex = MurderGame.playerBowSkinIndex[MurderGame.detectiveId] ?: 0
            val skin      = BowSkinGui.SKINS.getOrElse(skinIndex) { BowSkinGui.SKINS[0] }
            val bowItem   = BowSkinGui.makeBowItem(skin)
            event.isCancelled = true
            MurderGame.droppedDetectiveBows.remove(itemId)
            MurderGame.bowDropLocations.remove(itemId)
            MurderGame.playersNearBowOnDrop.remove(itemId)
            event.item.remove()
            val arrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[player.uniqueId] ?: 0) { ArrowSkinGui.SKINS[0] }
            // Vorhandenen Bogen im selben Slot ersetzen; kein Bogen vorhanden → Slot 1
            var targetBowSlot = 1
            for (i in 0 until player.inventory.size) {
                val existing = player.inventory.getItem(i)
                if (existing != null && (existing.type == Material.BOW || existing.type == Material.TRIDENT)) {
                    targetBowSlot = i
                    player.inventory.setItem(i, null)
                    break
                }
            }
            player.inventory.setItem(targetBowSlot, bowItem)
            player.inventory.setItem((targetBowSlot + 1).coerceAtMost(8), ArrowSkinGui.makeArrowItem(arrowSkin))
            if (player.uniqueId != MurderGame.detectiveId) {
                player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED).append(Component.text("Du hast den Bogen aufgehoben! Pfeil erhalten.", NamedTextColor.GREEN)))
            }
        }
    }

    // ── Pfeil-Cooldown für Detektiv ───────────────────────────────────────────

    private fun startArrowCooldown(detectiveUuid: UUID) {
        MurderGame.detectiveArrowReady = false
        MurderGame.arrowCooldownTask?.cancel()

        val skinIndex  = MurderGame.playerBowSkinIndex[detectiveUuid] ?: 0
        val skin       = BowSkinGui.SKINS.getOrElse(skinIndex) { BowSkinGui.SKINS[0] }
        val isTrident  = skin.weaponMaterial == Material.TRIDENT
        val weaponName = if (isTrident) "Dreizack" else "Pfeil"

        var secondsLeft = 10
        MurderGame.arrowCooldownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!MurderGame.running) { MurderGame.arrowCooldownTask?.cancel(); MurderGame.arrowCooldownTask = null; return@Runnable }
            val detective = plugin.server.getPlayer(detectiveUuid) ?: return@Runnable

            if (secondsLeft <= 0) {
                MurderGame.arrowCooldownTask?.cancel(); MurderGame.arrowCooldownTask = null
                MurderGame.detectiveArrowReady = true
                if (isTrident) {
                    detective.inventory.setItem(1, BowSkinGui.makeBowItem(skin))
                } else {
                    if (detective.inventory.getItem(1)?.type == Material.BOW) {
                        val arrowSkin = ArrowSkinGui.SKINS.getOrElse(MurderGame.playerArrowSkinIndex[detectiveUuid] ?: 0) { ArrowSkinGui.SKINS[0] }
                        detective.inventory.setItem(2, ArrowSkinGui.makeArrowItem(arrowSkin))
                    }
                }
                detective.sendActionBar(Component.text("$weaponName aufgeladen!", NamedTextColor.GREEN))
                return@Runnable
            }
            detective.sendActionBar(
                Component.text("Neuer $weaponName in ", NamedTextColor.YELLOW)
                    .append(Component.text("$secondsLeft", NamedTextColor.RED))
                    .append(Component.text(" Sekunden", NamedTextColor.YELLOW))
            )
            secondsLeft--
        }, 0L, 20L)
    }

    // ── Spielende ─────────────────────────────────────────────────────────────

    fun stopGame(sender: CommandSender) {
        if (!MurderGame.running) { sender.sendMessage("§c[Murder] §fKein Spiel läuft gerade!"); return }
        endInternal(draw = true)
    }

    private fun endInternal(draw: Boolean, murderWins: Boolean = false) {
        // Rollenverlauf speichern
        MurderGame.murderId?.let { MurderGame.playerMurderCount[it] = (MurderGame.playerMurderCount[it] ?: 0) + 1 }
        MurderGame.detectiveId?.let { MurderGame.playerDetectiveCount[it] = (MurderGame.playerDetectiveCount[it] ?: 0) + 1 }
        for (uid in MurderGame.allGamePlayers) MurderGame.playerTotalGames[uid] = (MurderGame.playerTotalGames[uid] ?: 0) + 1

        val msg: Component = when {
            draw       -> Component.text("[Murder] ", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                            .append(Component.text("Das Spiel wurde gestoppt! ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                            .append(Component.text("Unentschieden!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
            murderWins -> Component.text("[Murder] ", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                            .append(Component.text("Der ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                            .append(Component.text("Mörder ", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                            .append(Component.text("hat gewonnen! Alle wurden getötet!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
            else       -> Component.text("[Murder] ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                            .append(Component.text("Die ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                            .append(Component.text("Unschuldigen ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                            .append(Component.text("haben gewonnen! Der Mörder wurde besiegt!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
        }
        plugin.server.broadcast(msg)

        for (player in plugin.server.onlinePlayers) player.inventory.clear()
        for (world in plugin.server.worlds) world.entities.filterIsInstance<org.bukkit.entity.Item>().forEach { it.remove() }

        MurderGame.reset()
        ConfigManager.saveAll()

        plugin.server.broadcast(Component.text("[Murder] ", NamedTextColor.GRAY).append(Component.text("Lobby in ")).append(Component.text("5 ", NamedTextColor.YELLOW)).append(Component.text("Sekunden...")))
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            for (player in plugin.server.onlinePlayers) {
                player.gameMode = GameMode.ADVENTURE
                if (MurderGame.lobbySpawn != null) teleportToLobby(player)
                else giveLobbyItem(player)
            }
            plugin.server.scheduler.runTaskLater(plugin, Runnable { checkAutoStart() }, 20L)
        }, 100L)
    }

    private fun teleportToLobby(player: Player) {
        val lobby = MurderGame.lobbySpawn ?: return
        val target = lobby.clone()
        target.x += (-1..1).random().toDouble()
        target.z += (-1..1).random().toDouble()
        player.teleport(target)
        giveLobbyItem(player)
    }
}
