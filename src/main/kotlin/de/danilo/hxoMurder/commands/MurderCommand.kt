package de.danilo.hxoMurder.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import de.danilo.hxoMurder.ConfigManager
import de.danilo.hxoMurder.MurderGame
import de.danilo.hxoMurder.SpawnPoint
import de.danilo.hxoMurder.gui.BowSkinGui
import de.danilo.hxoMurder.gui.KnifeSkinGui
import de.danilo.hxoMurder.gui.MurderMainGui
import de.danilo.hxoMurder.listeners.MurderListener
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object MurderCommand {

    fun register(commands: Commands, plugin: JavaPlugin, listener: MurderListener) {
        commands.register(
            Commands.literal("murder")
                .then(
                    Commands.literal("setSpawnpoint")
                        .executes { ctx ->
                            val player = ctx.source.executor as? Player
                            if (player == null) ctx.source.sender.sendMessage("§c[Murder] §fNur Spieler können den Setzer verwenden!")
                            else listener.giveSpawnWand(player)
                            Command.SINGLE_SUCCESS
                        }
                        .then(
                            Commands.argument("nummer", IntegerArgumentType.integer(1, 10))
                                .executes { ctx ->
                                    val nummer = IntegerArgumentType.getInteger(ctx, "nummer")
                                    setSpawn(ctx.source.executor as? Player, nummer, "Spawn #$nummer", ctx.source.sender)
                                    Command.SINGLE_SUCCESS
                                }
                                .then(
                                    Commands.argument("name", StringArgumentType.greedyString())
                                        .executes { ctx ->
                                            val nummer = IntegerArgumentType.getInteger(ctx, "nummer")
                                            setSpawn(ctx.source.executor as? Player, nummer, StringArgumentType.getString(ctx, "name"), ctx.source.sender)
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                        )
                )
                .then(
                    Commands.literal("deletespawnpoint")
                        .then(
                            Commands.argument("nummer", IntegerArgumentType.integer(1, 10))
                                .executes { ctx ->
                                    val nummer = IntegerArgumentType.getInteger(ctx, "nummer")
                                    val removed = MurderGame.spawnPoints.remove(nummer)
                                    if (removed != null) {
                                        ctx.source.sender.sendMessage("§a[Murder] §fSpawnpunkt §e#$nummer §f(§e${removed.name}§f) gelöscht.")
                                        ConfigManager.saveAll()
                                    } else {
                                        ctx.source.sender.sendMessage("§c[Murder] §fSpawnpunkt §e#$nummer §fexistiert nicht.")
                                    }
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
                .then(
                    Commands.literal("set-lobby")
                        .executes { ctx ->
                            val player = ctx.source.executor as? Player
                            if (player == null) {
                                ctx.source.sender.sendMessage("§c[Murder] §fNur Spieler können den Lobby-Spawn setzen!")
                            } else {
                                MurderGame.lobbySpawn = player.location.clone()
                                player.sendMessage("§a[Murder] §fLobby-Spawn wurde an deiner Position gesetzt.")
                                ConfigManager.saveAll()
                            }
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("spawn.show")
                        .executes { ctx ->
                            toggleBeacons(ctx.source.sender, plugin)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("start")
                        .then(
                            Commands.argument("maxSpieler", IntegerArgumentType.integer(2, 10))
                                .executes { ctx ->
                                    startGame(ctx.source.sender, plugin, IntegerArgumentType.getInteger(ctx, "maxSpieler"))
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .executes { ctx ->
                            startGame(ctx.source.sender, plugin, null)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("stop")
                        .executes { ctx ->
                            listener.stopGame(ctx.source.sender)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("info")
                        .executes { ctx ->
                            showInfo(ctx.source.sender, plugin)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("skin")
                        .then(
                            Commands.literal("knife")
                                .executes { ctx ->
                                    val player = ctx.source.executor as? Player
                                    if (player == null) {
                                        ctx.source.sender.sendMessage("§c[Murder] §fNur Spieler können den Skin wählen!")
                                    } else {
                                        KnifeSkinGui.open(player)
                                    }
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
                .executes { ctx ->
                    // Spieler bekommen das Hauptmenü, Konsole sieht die Hilfe
                    val player = ctx.source.executor as? Player
                    if (player != null) {
                        MurderMainGui.open(player)
                    } else {
                        showHelp(ctx.source.sender)
                    }
                    Command.SINGLE_SUCCESS
                }
                .build(),
            "Murder-Minigame Befehle"
        )
    }

    private fun setSpawn(player: Player?, nummer: Int, name: String, sender: CommandSender) {
        if (player == null) {
            sender.sendMessage("§c[Murder] §fNur Spieler können Spawnpunkte setzen!")
            return
        }
        MurderGame.spawnPoints[nummer] = SpawnPoint(player.location.clone(), name)
        player.sendMessage("§a[Murder] §fSpawnpunkt §e#$nummer §f(§e$name§f) gesetzt.")
        ConfigManager.saveAll()
    }

    fun toggleBeacons(sender: CommandSender, plugin: JavaPlugin) {
        if (MurderGame.showingBeacons) {
            for (spawn in MurderGame.spawnPoints.values) {
                for ((loc, _) in MurderGame.fakeBeaconBlocks(spawn.location)) {
                    val world = loc.world ?: continue
                    val realData = world.getBlockAt(loc).blockData
                    for (player in plugin.server.onlinePlayers) {
                        player.sendBlockChange(loc, realData)
                    }
                }
            }
            MurderGame.showingBeacons = false
            sender.sendMessage("§a[Murder] §fSpawn-Anzeige §cdeaktiviert§f.")
        } else {
            if (MurderGame.spawnPoints.isEmpty()) {
                sender.sendMessage("§c[Murder] §fKeine Spawnpunkte gesetzt!")
                return
            }
            for (spawn in MurderGame.spawnPoints.values) {
                for ((loc, material) in MurderGame.fakeBeaconBlocks(spawn.location)) {
                    val data = material.createBlockData()
                    for (player in plugin.server.onlinePlayers) {
                        player.sendBlockChange(loc, data)
                    }
                }
            }
            MurderGame.showingBeacons = true
            sender.sendMessage("§a[Murder] §fSpawn-Anzeige §aaktiviert§f.")
        }
    }

    private fun startGame(sender: CommandSender, plugin: JavaPlugin, maxSpieler: Int?) {
        if (MurderGame.running) {
            sender.sendMessage("§c[Murder] §fEs läuft bereits ein Spiel!")
            return
        }
        if (MurderGame.spawnPoints.isEmpty()) {
            sender.sendMessage("§c[Murder] §fKeine Spawnpunkte gesetzt!")
            return
        }

        val allePlayers = plugin.server.onlinePlayers.toMutableList()
        if (allePlayers.size < 2) {
            sender.sendMessage("§c[Murder] §fMindestens §e2 Spieler §fwerden benötigt! (Online: ${allePlayers.size})")
            return
        }

        allePlayers.shuffle()
        val shuffledSpawns = MurderGame.spawnPoints.values.toMutableList().also { it.shuffle() }
        val limit = maxSpieler ?: MurderGame.configuredMaxPlayers
        val maxTeilnehmer = minOf(limit, shuffledSpawns.size, allePlayers.size)

        if (maxTeilnehmer < 2) {
            sender.sendMessage("§c[Murder] §fNicht genug Spawnpunkte für mindestens 2 Spieler!")
            return
        }

        val teilnehmer = allePlayers.take(maxTeilnehmer)
        val zuschauer  = allePlayers.drop(maxTeilnehmer)

        val murder    = teilnehmer[0]
        val detective = teilnehmer[1]
        val innocents = teilnehmer.drop(2)

        MurderGame.murderId   = murder.uniqueId
        MurderGame.detectiveId = detective.uniqueId
        MurderGame.alivePlayers.addAll(teilnehmer.map { it.uniqueId })
        MurderGame.allGamePlayers.addAll(teilnehmer.map { it.uniqueId })
        MurderGame.running = true
        MurderGame.detectiveArrowReady = true

        // Spieler teleportieren, auf Adventure setzen, einfrieren
        teilnehmer.forEachIndexed { index, player ->
            player.teleport(shuffledSpawns[index].location)
            player.gameMode = GameMode.ADVENTURE
            player.inventory.clear()
            player.foodLevel = 20
            player.saturation = 20.0f
            // Dunkelheit für 5 Sekunden
            player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false))
        }

        // Alle Teilnehmer einfrieren
        MurderGame.frozenPlayers.addAll(teilnehmer.map { it.uniqueId })

        // Nach 5 Sekunden (100 Ticks) wieder freilassen
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (MurderGame.running) {
                MurderGame.frozenPlayers.removeAll(teilnehmer.map { it.uniqueId }.toSet())
            }
        }, 100L)

        // Waffe für Mörder erst nach 5 Sekunden (Freeze-Ende)
        val murderWeapon = MurderGame.playerKnifeSkins[murder.uniqueId] ?: Material.IRON_SWORD
        murder.sendMessage("§c§l[Murder] §r§fDu bist der §c§lMörder§r§f! Töte alle anderen!")
        val murdererUuid = murder.uniqueId
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!MurderGame.running) return@Runnable
            plugin.server.getPlayer(murdererUuid)?.inventory?.setItem(1, ItemStack(murderWeapon))
        }, 100L)

        // Bogen für Detektiv (gewählter Skin oder Standard)
        val bowSkinIndex = MurderGame.playerBowSkinIndex[detective.uniqueId] ?: 0
        val bowSkin = BowSkinGui.SKINS.getOrElse(bowSkinIndex) { BowSkinGui.SKINS[0] }
        detective.inventory.setItem(1, BowSkinGui.makeBowItem(bowSkin))
        detective.inventory.setItem(2, ItemStack(Material.ARROW, 1))
        detective.sendMessage("§a§l[Murder] §r§fDu bist der §a§lDetektiv§r§f! Jeder Schuss = One-Shot. Cooldown: 10s")

        innocents.forEach { it.sendMessage("§7§l[Murder] §r§fDu bist §7§lunschuldig§r§f. Überlebe!") }

        zuschauer.forEach { player ->
            player.gameMode = GameMode.SPECTATOR
            player.sendMessage("§c[Murder] §fDie Runde ist voll, bitte warte auf die nächste!")
        }

        val roleReceivers = plugin.server.onlinePlayers.filter { it.hasPermission("hxo-murder-see-roles") }
        val sep = Component.text("                              ", NamedTextColor.DARK_GRAY).decorate(TextDecoration.STRIKETHROUGH)
        roleReceivers.forEach { p ->
            p.sendMessage(sep)
            p.sendMessage(Component.text("Mörder: ", NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(murder.name, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
            p.sendMessage(Component.text("Detektiv: ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD).append(Component.text(detective.name, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
            if (innocents.isNotEmpty()) {
                p.sendMessage(Component.text("Unschuldige: ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(innocents.joinToString(", ") { it.name }, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
            }
            p.sendMessage(sep)
        }
    }

    private fun showInfo(sender: CommandSender, plugin: JavaPlugin) {
        val sep = Component.text("─────────────────────────────", NamedTextColor.DARK_GRAY)
        sender.sendMessage(sep)
        if (!MurderGame.running) {
            sender.sendMessage(Component.text("[Murder] ", NamedTextColor.GRAY)
                .append(Component.text("Kein Spiel aktiv.", NamedTextColor.WHITE)))
            sender.sendMessage(sep)
            return
        }

        val murdererName  = MurderGame.murderId?.let  { plugin.server.getPlayer(it)?.name ?: "Offline" } ?: "?"
        val detectiveName = MurderGame.detectiveId?.let { plugin.server.getPlayer(it)?.name ?: "Offline" } ?: "?"

        val aliveInnocents = MurderGame.alivePlayers
            .filter { it != MurderGame.murderId && it != MurderGame.detectiveId }
            .mapNotNull { plugin.server.getPlayer(it)?.name }

        sender.sendMessage(Component.text("[Murder] Aktuelle Runde", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        sender.sendMessage(Component.text("Mörder:   ", NamedTextColor.RED).decorate(TextDecoration.BOLD)
            .append(Component.text(murdererName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
        sender.sendMessage(Component.text("Detektiv: ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
            .append(Component.text(detectiveName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))

        val innocentLine = if (aliveInnocents.isEmpty()) "keine" else aliveInnocents.joinToString(", ")
        sender.sendMessage(Component.text("Lebend (Unschuldige): ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
            .append(Component.text(innocentLine, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))

        val total = MurderGame.alivePlayers.size
        val totalAll = MurderGame.allGamePlayers.size
        sender.sendMessage(Component.text("Spieler: ", NamedTextColor.GRAY)
            .append(Component.text("$total", NamedTextColor.YELLOW))
            .append(Component.text(" / $totalAll am Leben", NamedTextColor.GRAY)))
        sender.sendMessage(sep)
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§a[Murder] §fBefehle:")
        sender.sendMessage("§7/murder §8- §fHauptmenü öffnen")
        sender.sendMessage("§7/murder setSpawnpoint <1-10> [Name]")
        sender.sendMessage("§7/murder deletespawnpoint <1-10>")
        sender.sendMessage("§7/murder set-lobby §8- §fLobby-Spawn setzen")
        sender.sendMessage("§7/murder spawn.show §8- §fBeacon-Anzeige ein-/ausblenden")
        sender.sendMessage("§7/murder start [maxSpieler]")
        sender.sendMessage("§7/murder stop §8- §fRunde abbrechen (Unentschieden)")
        val lobby = MurderGame.lobbySpawn
        if (lobby != null) {
            sender.sendMessage("§fLobby: §e${lobby.world?.name} §f(${lobby.blockX}, ${lobby.blockY}, ${lobby.blockZ})")
        } else {
            sender.sendMessage("§cKein Lobby-Spawn gesetzt.")
        }
        if (MurderGame.spawnPoints.isEmpty()) {
            sender.sendMessage("§cKeine Spawnpunkte gesetzt.")
        } else {
            sender.sendMessage("§fSpawnpunkte:")
            MurderGame.spawnPoints.entries.sortedBy { it.key }.forEach { (num, spawn) ->
                sender.sendMessage("  §e#$num §7- §f${spawn.name}")
            }
        }
    }
}
