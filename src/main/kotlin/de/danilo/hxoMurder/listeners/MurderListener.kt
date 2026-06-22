package de.danilo.hxoMurder.listeners

import de.danilo.hxoMurder.ConfigManager
import de.danilo.hxoMurder.MurderGame
import de.danilo.hxoMurder.SpawnPoint
import de.danilo.hxoMurder.commands.MurderCommand
import de.danilo.hxoMurder.gui.BowSkinGui
import de.danilo.hxoMurder.gui.KnifeSkinGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Trident

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
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
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class MurderListener(private val plugin: JavaPlugin) : Listener {

    val spawnSetterPlayers = mutableMapOf<UUID, Int>() // UUID → nächste Spawn-Nummer

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.inventory.clear()
        if (MurderGame.showingBeacons) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                for (spawn in MurderGame.spawnPoints.values) {
                    for ((loc, material) in MurderGame.fakeBeaconBlocks(spawn.location)) {
                        event.player.sendBlockChange(loc, material.createBlockData())
                    }
                }
            }, 5L)
        }
        if (!MurderGame.running) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                event.player.gameMode = GameMode.ADVENTURE
                if (MurderGame.lobbySpawn != null) teleportToLobby(event.player)
            }, 10L)
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (player.uniqueId in MurderGame.alivePlayers) event.isCancelled = true
    }

    @EventHandler
    fun onItemDamage(event: PlayerItemDamageEvent) {
        if (event.player.uniqueId in MurderGame.alivePlayers) event.isCancelled = true
    }

    // ── Einfrieren beim Rundenstart ──────────────────────────────────────────
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!MurderGame.running) return
        val player = event.player

        if (player.uniqueId in MurderGame.frozenPlayers) {
            val from = event.from
            val to   = event.to
            if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
                event.setTo(from)
            }
            return
        }

        // Track whether a player has moved far enough from a dropped bow to be allowed to pick it up
        if (MurderGame.playersNearBowOnDrop.isEmpty()) return
        val playerUuid = player.uniqueId
        for ((bowId, restrictedPlayers) in MurderGame.playersNearBowOnDrop) {
            if (playerUuid !in restrictedPlayers) continue
            val dropLoc = MurderGame.bowDropLocations[bowId] ?: continue
            if (player.world != dropLoc.world) continue
            if (player.location.distanceSquared(dropLoc) > 16.0) {
                restrictedPlayers.remove(playerUuid)
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        event.deathMessage(null)

        if (!MurderGame.running) return
        val dead = event.entity
        if (dead.uniqueId !in MurderGame.alivePlayers) return

        // Always cancel arrow cooldown when detective dies
        if (dead.uniqueId == MurderGame.detectiveId) {
            MurderGame.arrowCooldownTask?.cancel()
            MurderGame.arrowCooldownTask = null
        }

        // Don't drop bow when this death ends the round (items are cleared by endInternal)
        val innocentsAfterDeath = MurderGame.alivePlayers.count { it != MurderGame.murderId && it != dead.uniqueId }
        val isGameEnding = dead.uniqueId == MurderGame.murderId || innocentsAfterDeath == 0

        if (!isGameEnding) {
            val bowItem = dead.inventory.getItem(1)
            if (bowItem != null && (bowItem.type == Material.BOW || bowItem.type == Material.TRIDENT)) {
                val dropped = dead.world.dropItemNaturally(dead.location, bowItem)
                dropped.pickupDelay = 0
                MurderGame.droppedDetectiveBows.add(dropped.uniqueId)
                // Track drop location; nearby players must move away before pickup
                MurderGame.bowDropLocations[dropped.uniqueId] = dead.location.clone()
                dead.world.players
                    .filter {
                        it.uniqueId in MurderGame.alivePlayers &&
                        it.uniqueId != dead.uniqueId &&
                        it.world == dead.world &&
                        it.location.distanceSquared(dead.location) <= 16.0
                    }
                    .forEach { MurderGame.playersNearBowOnDrop.getOrPut(dropped.uniqueId) { mutableSetOf() }.add(it.uniqueId) }
            }
        }

        event.drops.clear()
        event.droppedExp = 0

        MurderGame.alivePlayers.remove(dead.uniqueId)
        MurderGame.frozenPlayers.remove(dead.uniqueId)

        if (dead.uniqueId == MurderGame.murderId) {
            endInternal(draw = false, murderWins = false)
            return
        }

        if (MurderGame.alivePlayers.count { it != MurderGame.murderId } == 0) {
            endInternal(draw = false, murderWins = true)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!MurderGame.running) return
        val player = event.player
        if (player.uniqueId !in MurderGame.alivePlayers) return

        MurderGame.alivePlayers.remove(player.uniqueId)
        MurderGame.frozenPlayers.remove(player.uniqueId)

        if (player.uniqueId == MurderGame.detectiveId) {
            MurderGame.arrowCooldownTask?.cancel()
            MurderGame.arrowCooldownTask = null
        }

        for (restricted in MurderGame.playersNearBowOnDrop.values) restricted.remove(player.uniqueId)

        if (player.uniqueId == MurderGame.murderId) {
            endInternal(draw = false, murderWins = false)
            return
        }

        if (MurderGame.alivePlayers.count { it != MurderGame.murderId } == 0) {
            endInternal(draw = false, murderWins = true)
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (!MurderGame.running) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                player.gameMode = GameMode.SPECTATOR
            }, 1L)
            return
        }
        if (player.uniqueId !in MurderGame.alivePlayers) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("§c[Murder] §fDu wurdest eliminiert – beobachte als Zuschauer!")
            }, 1L)
        }
    }

    // ── Spawn-Setzer Wand ────────────────────────────────────────────────────
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

    // ── Bogen-Spannen während Freeze blockieren ───────────────────────────────
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Spawn-Setzer Wand (funktioniert auch wenn kein Spiel läuft)
        val player = event.player
        if (player.uniqueId in spawnSetterPlayers &&
            (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            val hand = player.inventory.itemInMainHand
            if (hand.type == Material.WOODEN_HOE && hand.itemMeta?.hasDisplayName() == true) {
                event.isCancelled = true
                val nextNum = spawnSetterPlayers[player.uniqueId]!!
                if (nextNum > 10) {
                    player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED)
                        .append(Component.text("Maximal 10 Spawnpunkte!", NamedTextColor.RED)))
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
                player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED)
                    .append(Component.text("Spawn #$nextNum gesetzt!", NamedTextColor.GREEN)))
                return
            }
        }

        if (!MurderGame.running) return
        if (player.uniqueId !in MurderGame.frozenPlayers) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val handType = event.player.inventory.itemInMainHand.type
        if (handType != Material.BOW && handType != Material.TRIDENT) return
        event.isCancelled = true
        event.player.updateInventory()
    }

    // ── Bogen ────────────────────────────────────────────────────────────────
    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.player.uniqueId in MurderGame.alivePlayers) event.isCancelled = true
    }

    @EventHandler
    fun onBowShoot(event: EntityShootBowEvent) {
        if (!MurderGame.running) return
        val player = event.entity as? Player ?: return
        if (player.uniqueId in MurderGame.frozenPlayers) {
            event.isCancelled = true
            return
        }
        if (player.uniqueId !in MurderGame.alivePlayers || player.uniqueId == MurderGame.murderId) {
            event.isCancelled = true
            return
        }
        if (player.uniqueId == MurderGame.detectiveId && MurderGame.detectiveArrowReady) {
            startArrowCooldown(player.uniqueId)
        }
    }

    // ── Dreizack-Wurf ─────────────────────────────────────────────────────────
    @EventHandler
    fun onTridentLaunch(event: ProjectileLaunchEvent) {
        if (!MurderGame.running) return
        val trident = event.entity as? Trident ?: return
        val player = trident.shooter as? Player ?: return
        if (player.uniqueId in MurderGame.frozenPlayers) {
            event.isCancelled = true
            return
        }
        if (player.uniqueId !in MurderGame.alivePlayers || player.uniqueId == MurderGame.murderId) {
            event.isCancelled = true
            return
        }
        if (player.uniqueId == MurderGame.detectiveId && !MurderGame.detectiveArrowReady) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!MurderGame.running) return
        val victim = event.entity as? Player ?: return
        if (victim.uniqueId !in MurderGame.alivePlayers) return

        val damager = event.damager

        // Mörder trifft jemanden mit Nahkampfwaffe → One-Shot
        if (damager is Player && damager.uniqueId == MurderGame.murderId) {
            if (damager.inventory.itemInMainHand.type in KnifeSkinGui.WEAPON_MATERIALS) {
                event.damage = 1000.0
            }
            return
        }

        // Pfeil oder Dreizack trifft jemanden
        if (damager is Arrow || damager is Trident) {
            val shooter = (damager as Projectile).shooter as? Player
            if (shooter == null || shooter.uniqueId !in MurderGame.alivePlayers || shooter.uniqueId == MurderGame.murderId) {
                event.isCancelled = true
                return
            }
            when {
                victim.uniqueId == MurderGame.murderId -> {
                    event.damage = 1000.0
                }
                shooter.uniqueId == MurderGame.detectiveId -> {
                    event.damage = 1000.0
                }
                else -> {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        if (!MurderGame.running) return
        val projectile = event.entity
        if (projectile !is Arrow && projectile !is Trident) return
        val shooter = projectile.shooter as? Player ?: return

        if (shooter.uniqueId != MurderGame.detectiveId) {
            if (projectile is Trident && event.hitEntity == null) projectile.remove()
            else if (projectile !is Trident) plugin.server.scheduler.runTaskLater(plugin, Runnable { projectile.remove() }, 2L)
            return
        }

        // Detektiv-Projektil: Fehlschuss → kein neuer Pfeil
        val hitMurderer = (event.hitEntity as? Player)?.uniqueId == MurderGame.murderId
        if (!hitMurderer) {
            MurderGame.arrowCooldownTask?.cancel()
            MurderGame.arrowCooldownTask = null
            // detectiveArrowReady bleibt false – kein Pfeil mehr
            plugin.server.getPlayer(shooter.uniqueId)?.sendActionBar(
                Component.text("Danebengschossen! ", NamedTextColor.RED)
                    .append(Component.text("Kein Pfeil mehr.", NamedTextColor.DARK_RED))
            )
        }
        if (projectile is Trident) projectile.remove()
        else plugin.server.scheduler.runTaskLater(plugin, Runnable { projectile.remove() }, 2L)
    }

    @EventHandler
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (!MurderGame.running) return
        val player = event.entity as? Player ?: return
        val itemId = event.item.uniqueId

        // Detektiv-Pfeil-Cooldown
        if (player.uniqueId == MurderGame.detectiveId &&
            event.item.itemStack.type == Material.ARROW &&
            !MurderGame.detectiveArrowReady
        ) {
            event.isCancelled = true
            return
        }

        // Fallen gelassener Detektiv-Bogen
        if (itemId in MurderGame.droppedDetectiveBows) {
            // Mörder darf nie aufheben
            if (player.uniqueId == MurderGame.murderId) {
                event.isCancelled = true
                return
            }
            // Detektiv nur während der 5-Sek-Freeze-Strafe blockieren
            if (player.uniqueId == MurderGame.detectiveId && player.uniqueId in MurderGame.frozenPlayers) {
                event.isCancelled = true
                return
            }
            // Muss sich erst vom Abwurfpunkt entfernt haben
            if (player.uniqueId in (MurderGame.playersNearBowOnDrop[itemId] ?: emptySet<UUID>())) {
                event.isCancelled = true
                return
            }
            // Pickup abfangen und Waffe mit korrektem Skin in feste Slots legen
            val skinIndex = MurderGame.playerBowSkinIndex[MurderGame.detectiveId] ?: 0
            val skin = BowSkinGui.SKINS.getOrElse(skinIndex) { BowSkinGui.SKINS[0] }
            val bowItem = BowSkinGui.makeBowItem(skin)
            event.isCancelled = true
            MurderGame.droppedDetectiveBows.remove(itemId)
            MurderGame.bowDropLocations.remove(itemId)
            MurderGame.playersNearBowOnDrop.remove(itemId)
            event.item.remove()
            player.inventory.setItem(1, bowItem)
            player.inventory.setItem(2, ItemStack(Material.ARROW, 1))
            if (player.uniqueId != MurderGame.detectiveId) {
                player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED).append(Component.text("Du hast den Bogen aufgehoben! Pfeil erhalten.", NamedTextColor.GREEN)))
            }
        }
    }

    // ── Pfeil-Cooldown für Detektiv ───────────────────────────────────────────
    private fun startArrowCooldown(detectiveUuid: UUID) {
        MurderGame.detectiveArrowReady = false
        MurderGame.arrowCooldownTask?.cancel()

        val skinIndex = MurderGame.playerBowSkinIndex[detectiveUuid] ?: 0
        val skin = BowSkinGui.SKINS.getOrElse(skinIndex) { BowSkinGui.SKINS[0] }
        val isTrident = skin.weaponMaterial == Material.TRIDENT
        val weaponName = if (isTrident) "Dreizack" else "Pfeil"

        var secondsLeft = 10
        MurderGame.arrowCooldownTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!MurderGame.running) {
                MurderGame.arrowCooldownTask?.cancel()
                MurderGame.arrowCooldownTask = null
                return@Runnable
            }
            val detective = plugin.server.getPlayer(detectiveUuid) ?: return@Runnable

            if (secondsLeft <= 0) {
                MurderGame.arrowCooldownTask?.cancel()
                MurderGame.arrowCooldownTask = null
                MurderGame.detectiveArrowReady = true
                if (isTrident) {
                    // Dreizack in Slot 1 zurückgeben
                    detective.inventory.setItem(1, BowSkinGui.makeBowItem(skin))
                } else {
                    // Pfeil in Slot 2 geben, sofern der Bogen noch in Slot 1 liegt
                    if (detective.inventory.getItem(1)?.type == Material.BOW) {
                        detective.inventory.setItem(2, ItemStack(Material.ARROW, 1))
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
    fun stopGame(sender: org.bukkit.command.CommandSender) {
        if (!MurderGame.running) {
            sender.sendMessage("§c[Murder] §fKein Spiel läuft gerade!")
            return
        }
        endInternal(draw = true)
    }

    private fun endInternal(draw: Boolean, murderWins: Boolean = false) {
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

        for (player in plugin.server.onlinePlayers) {
            player.inventory.clear()
        }
        for (world in plugin.server.worlds) {
            world.entities.filterIsInstance<org.bukkit.entity.Item>().forEach { it.remove() }
        }

        MurderGame.reset()

        plugin.server.broadcast(Component.text("[Murder] ", NamedTextColor.GRAY).append(Component.text("Lobby in ", NamedTextColor.WHITE)).append(Component.text("5 ", NamedTextColor.YELLOW)).append(Component.text("Sekunden...", NamedTextColor.WHITE)))
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val lobby = MurderGame.lobbySpawn
            if (lobby != null) {
                for (player in plugin.server.onlinePlayers) {
                    player.gameMode = GameMode.ADVENTURE
                    teleportToLobby(player)
                }
            }
        }, 100L)
    }

    private fun teleportToLobby(player: Player) {
        val lobby = MurderGame.lobbySpawn ?: return
        val dx = (-1..1).random().toDouble()
        val dz = (-1..1).random().toDouble()
        val target = lobby.clone()
        target.x += dx
        target.z += dz
        player.teleport(target)
    }
}
