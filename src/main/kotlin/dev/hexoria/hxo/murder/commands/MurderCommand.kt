package dev.hexoria.hxo.murder.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import dev.hexoria.hxo.murder.ConfigManager
import dev.hexoria.hxo.murder.MurderGame
import dev.hexoria.hxo.murder.SpawnPoint
import dev.hexoria.hxo.murder.gui.ArrowSkinGui
import dev.hexoria.hxo.murder.gui.BowSkinGui
import dev.hexoria.hxo.murder.gui.KnifeSkinGui
import dev.hexoria.hxo.murder.gui.MurderMainGui
import dev.hexoria.hxo.murder.listeners.MurderListener
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

private const val EDITOR_PERM = "hxo-murder-command-editor"

object MurderCommand {

    fun register(commands: Commands, plugin: JavaPlugin, listener: MurderListener) {
        commands.register(
            Commands.literal("murder")
                // ── Spawn-Punkte ──────────────────────────────────────────────
                .then(
                    Commands.literal("setSpawnpoint")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .executes { ctx ->
                            val player = ctx.source.executor as? org.bukkit.entity.Player
                            if (player == null) ctx.source.sender.sendMessage("§c[Murder] §fNur Spieler können den Setzer verwenden!")
                            else listener.giveSpawnWand(player)
                            Command.SINGLE_SUCCESS
                        }
                        .then(
                            Commands.argument("nummer", IntegerArgumentType.integer(1, 10))
                                .executes { ctx ->
                                    val nummer = IntegerArgumentType.getInteger(ctx, "nummer")
                                    setSpawn(ctx.source.executor as? org.bukkit.entity.Player, nummer, "Spawn #$nummer", ctx.source.sender)
                                    Command.SINGLE_SUCCESS
                                }
                                .then(
                                    Commands.argument("name", StringArgumentType.greedyString())
                                        .executes { ctx ->
                                            val nummer = IntegerArgumentType.getInteger(ctx, "nummer")
                                            setSpawn(ctx.source.executor as? org.bukkit.entity.Player, nummer, StringArgumentType.getString(ctx, "name"), ctx.source.sender)
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                        )
                )
                .then(
                    Commands.literal("deletespawnpoint")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
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
                // ── Goldpunkte ────────────────────────────────────────────────
                .then(
                    Commands.literal("goldpoint")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .then(
                            Commands.literal("add")
                                .executes { ctx ->
                                    val player = ctx.source.executor as? org.bukkit.entity.Player
                                    if (player == null) ctx.source.sender.sendMessage("§c[Murder] §fNur Spieler können Goldpunkte setzen!")
                                    else listener.giveGoldWand(player)
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("nummer", IntegerArgumentType.integer(1, 100))
                                        .executes { ctx ->
                                            val nummer = IntegerArgumentType.getInteger(ctx, "nummer")
                                            val removed = MurderGame.goldPoints.remove(nummer)
                                            if (removed != null) {
                                                ctx.source.sender.sendMessage("§a[Murder] §fGoldpunkt §e#$nummer §f(§e${removed.name}§f) gelöscht.")
                                                ConfigManager.saveAll()
                                            } else {
                                                ctx.source.sender.sendMessage("§c[Murder] §fGoldpunkt §e#$nummer §fexistiert nicht.")
                                            }
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                        )
                        .then(
                            Commands.literal("list")
                                .executes { ctx ->
                                    listGoldPoints(ctx.source.sender)
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .then(
                            Commands.literal("show")
                                .executes { ctx ->
                                    toggleGoldBeacons(ctx.source.sender, plugin)
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
                // ── Lobby ─────────────────────────────────────────────────────
                .then(
                    Commands.literal("set-lobby")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .executes { ctx ->
                            val player = ctx.source.executor as? org.bukkit.entity.Player
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
                // ── Beacons ───────────────────────────────────────────────────
                .then(
                    Commands.literal("spawn.show")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .executes { ctx ->
                            toggleBeacons(ctx.source.sender, plugin)
                            Command.SINGLE_SUCCESS
                        }
                )
                // ── Spiel ─────────────────────────────────────────────────────
                .then(
                    Commands.literal("start")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .then(
                            Commands.argument("maxSpieler", IntegerArgumentType.integer(2, 10))
                                .executes { ctx ->
                                    listener.startGame(ctx.source.sender, IntegerArgumentType.getInteger(ctx, "maxSpieler"))
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .executes { ctx ->
                            listener.startGame(ctx.source.sender, null)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("stop")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .executes { ctx ->
                            listener.stopGame(ctx.source.sender)
                            Command.SINGLE_SUCCESS
                        }
                )
                .then(
                    Commands.literal("info")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .executes { ctx ->
                            showInfo(ctx.source.sender, plugin)
                            Command.SINGLE_SUCCESS
                        }
                )
                // ── Skins ─────────────────────────────────────────────────────
                .then(
                    Commands.literal("skin")
                        .requires { it.sender.hasPermission(EDITOR_PERM) }
                        .then(
                            Commands.literal("knife")
                                .executes { ctx ->
                                    val player = ctx.source.executor as? org.bukkit.entity.Player
                                    if (player == null) ctx.source.sender.sendMessage("§c[Murder] §fNur Spieler können den Skin wählen!")
                                    else KnifeSkinGui.open(player)
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
                // ── Root → GUI ────────────────────────────────────────────────
                .executes { ctx ->
                    val player = ctx.source.executor as? org.bukkit.entity.Player
                    if (player != null) MurderMainGui.open(player)
                    else showHelp(ctx.source.sender)
                    Command.SINGLE_SUCCESS
                }
                .build(),
            "Murder-Minigame Befehle"
        )
    }

    // ── Hilfsfunktionen ───────────────────────────────────────────────────────

    private fun setSpawn(player: org.bukkit.entity.Player?, nummer: Int, name: String, sender: CommandSender) {
        if (player == null) { sender.sendMessage("§c[Murder] §fNur Spieler können Spawnpunkte setzen!"); return }
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
                    for (player in plugin.server.onlinePlayers) player.sendBlockChange(loc, realData)
                }
            }
            MurderGame.showingBeacons = false
            sender.sendMessage("§a[Murder] §fSpawn-Anzeige §cdeaktiviert§f.")
        } else {
            if (MurderGame.spawnPoints.isEmpty()) { sender.sendMessage("§c[Murder] §fKeine Spawnpunkte gesetzt!"); return }
            for (spawn in MurderGame.spawnPoints.values) {
                for ((loc, material) in MurderGame.fakeBeaconBlocks(spawn.location)) {
                    val data = material.createBlockData()
                    for (player in plugin.server.onlinePlayers) player.sendBlockChange(loc, data)
                }
            }
            MurderGame.showingBeacons = true
            sender.sendMessage("§a[Murder] §fSpawn-Anzeige §aaktiviert§f.")
        }
    }

    fun toggleGoldBeacons(sender: CommandSender, plugin: JavaPlugin) {
        if (MurderGame.showingGoldBeacons) {
            for (gold in MurderGame.goldPoints.values) {
                for ((loc, _) in MurderGame.fakeGoldBeaconBlocks(gold.location)) {
                    val world = loc.world ?: continue
                    val realData = world.getBlockAt(loc).blockData
                    for (player in plugin.server.onlinePlayers) player.sendBlockChange(loc, realData)
                }
            }
            MurderGame.showingGoldBeacons = false
            sender.sendMessage("§a[Murder] §fGold-Anzeige §cdeaktiviert§f.")
        } else {
            if (MurderGame.goldPoints.isEmpty()) { sender.sendMessage("§c[Murder] §fKeine Goldpunkte gesetzt!"); return }
            for (gold in MurderGame.goldPoints.values) {
                for ((loc, material) in MurderGame.fakeGoldBeaconBlocks(gold.location)) {
                    val data = material.createBlockData()
                    for (player in plugin.server.onlinePlayers) player.sendBlockChange(loc, data)
                }
            }
            MurderGame.showingGoldBeacons = true
            sender.sendMessage("§a[Murder] §fGold-Anzeige §aaktiviert§f.")
        }
    }

    private fun listGoldPoints(sender: CommandSender) {
        if (MurderGame.goldPoints.isEmpty()) { sender.sendMessage("§c[Murder] §fKeine Goldpunkte gesetzt."); return }
        val sep = Component.text("─────────────────────────────", NamedTextColor.DARK_GRAY)
        sender.sendMessage(sep)
        sender.sendMessage(Component.text("[Murder] Goldpunkte (${MurderGame.goldPoints.size}/100)", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
        MurderGame.goldPoints.entries.sortedBy { it.key }.forEach { (num, gold) ->
            val loc = gold.location
            sender.sendMessage(Component.text("  §e#$num §7- §f${gold.name} §8(${loc.world?.name} ${loc.blockX}, ${loc.blockY}, ${loc.blockZ})"))
        }
        sender.sendMessage(sep)
    }

    private fun showInfo(sender: CommandSender, plugin: JavaPlugin) {
        val sep = Component.text("─────────────────────────────", NamedTextColor.DARK_GRAY)
        sender.sendMessage(sep)
        if (!MurderGame.running) {
            sender.sendMessage(Component.text("[Murder] ", NamedTextColor.GRAY).append(Component.text("Kein Spiel aktiv.", NamedTextColor.WHITE)))
            sender.sendMessage(sep)
            return
        }
        val murdererName  = MurderGame.murderId?.let  { plugin.server.getPlayer(it)?.name ?: "Offline" } ?: "?"
        val detectiveName = MurderGame.detectiveId?.let { plugin.server.getPlayer(it)?.name ?: "Offline" } ?: "?"
        val aliveInnocents = MurderGame.alivePlayers
            .filter { it != MurderGame.murderId && it != MurderGame.detectiveId }
            .mapNotNull { plugin.server.getPlayer(it)?.name }
        sender.sendMessage(Component.text("[Murder] Aktuelle Runde", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
        sender.sendMessage(Component.text("Mörder:   ", NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(murdererName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
        sender.sendMessage(Component.text("Detektiv: ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD).append(Component.text(detectiveName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
        val innocentLine = if (aliveInnocents.isEmpty()) "keine" else aliveInnocents.joinToString(", ")
        sender.sendMessage(Component.text("Lebend (Unschuldige): ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(innocentLine, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)))
        sender.sendMessage(Component.text("Spieler: ", NamedTextColor.GRAY).append(Component.text("${MurderGame.alivePlayers.size}", NamedTextColor.YELLOW)).append(Component.text(" / ${MurderGame.allGamePlayers.size} am Leben", NamedTextColor.GRAY)))
        sender.sendMessage(sep)
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§a[Murder] §fBefehle (Berechtigung: $EDITOR_PERM):")
        sender.sendMessage("§7/murder setSpawnpoint <1-10> [Name]")
        sender.sendMessage("§7/murder deletespawnpoint <1-10>")
        sender.sendMessage("§7/murder set-lobby")
        sender.sendMessage("§7/murder spawn.show")
        sender.sendMessage("§7/murder goldpoint add/remove/list/show")
        sender.sendMessage("§7/murder start [maxSpieler]")
        sender.sendMessage("§7/murder stop")
        sender.sendMessage("§7/murder info")
    }
}
