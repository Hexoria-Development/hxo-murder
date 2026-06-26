package dev.hexoria.hxo.murder

import dev.hexoria.hxo.murder.gui.ArrowSkinGui
import dev.hexoria.hxo.murder.gui.BowSkinGui
import dev.hexoria.hxo.murder.gui.KnifeSkinGui
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

object ConfigManager {

    private lateinit var plugin: JavaPlugin

    fun init(p: JavaPlugin) {
        plugin = p
    }

    // ── Laden ─────────────────────────────────────────────────────────────────
    fun loadAll() {
        val cfg = plugin.config

        // Spawnpunkte
        cfg.getConfigurationSection("spawnpoints")?.let { sec ->
            for (key in sec.getKeys(false)) {
                val num = key.toIntOrNull() ?: continue
                val s   = sec.getConfigurationSection(key) ?: continue
                val world = plugin.server.getWorld(s.getString("world") ?: continue) ?: continue
                val loc = Location(
                    world,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    s.getDouble("yaw").toFloat(), s.getDouble("pitch").toFloat()
                )
                MurderGame.spawnPoints[num] = SpawnPoint(loc, s.getString("name") ?: "Spawn #$num")
            }
        }

        // Goldpunkte
        cfg.getConfigurationSection("goldpoints")?.let { sec ->
            for (key in sec.getKeys(false)) {
                val num = key.toIntOrNull() ?: continue
                val s   = sec.getConfigurationSection(key) ?: continue
                val world = plugin.server.getWorld(s.getString("world") ?: continue) ?: continue
                val loc = Location(
                    world,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    s.getDouble("yaw").toFloat(), s.getDouble("pitch").toFloat()
                )
                MurderGame.goldPoints[num] = GoldPoint(loc, s.getString("name") ?: "Gold #$num")
            }
        }

        // Lobby
        cfg.getConfigurationSection("lobby")?.let { sec ->
            val world = plugin.server.getWorld(sec.getString("world") ?: return@let) ?: return@let
            MurderGame.lobbySpawn = Location(
                world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                sec.getDouble("yaw").toFloat(), sec.getDouble("pitch").toFloat()
            )
        }

        // Einstellungen
        MurderGame.configuredMaxPlayers = cfg.getInt("settings.maxPlayers", 10).coerceIn(2, 10)

        // Messer-Skins
        cfg.getConfigurationSection("skins.knife")?.let { sec ->
            for (uuidStr in sec.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                val mat  = runCatching { Material.valueOf(sec.getString(uuidStr) ?: "") }.getOrNull() ?: continue
                if (mat in KnifeSkinGui.WEAPON_MATERIALS) MurderGame.playerKnifeSkins[uuid] = mat
            }
        }

        // Bogen-Skins
        cfg.getConfigurationSection("skins.bow")?.let { sec ->
            for (uuidStr in sec.getKeys(false)) {
                val uuid  = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                val index = sec.getInt(uuidStr, 0)
                if (index in BowSkinGui.SKINS.indices) MurderGame.playerBowSkinIndex[uuid] = index
            }
        }

        // Pfeil-Skins
        cfg.getConfigurationSection("skins.arrow")?.let { sec ->
            for (uuidStr in sec.getKeys(false)) {
                val uuid  = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                val index = sec.getInt(uuidStr, 0)
                if (index in ArrowSkinGui.SKINS.indices) MurderGame.playerArrowSkinIndex[uuid] = index
            }
        }

        // Rollenverlauf
        cfg.getConfigurationSection("stats.murder")?.let { sec ->
            for (uuidStr in sec.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                MurderGame.playerMurderCount[uuid] = sec.getInt(uuidStr, 0)
            }
        }
        cfg.getConfigurationSection("stats.detective")?.let { sec ->
            for (uuidStr in sec.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                MurderGame.playerDetectiveCount[uuid] = sec.getInt(uuidStr, 0)
            }
        }
        cfg.getConfigurationSection("stats.games")?.let { sec ->
            for (uuidStr in sec.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull() ?: continue
                MurderGame.playerTotalGames[uuid] = sec.getInt(uuidStr, 0)
            }
        }
    }

    // ── Speichern ─────────────────────────────────────────────────────────────
    fun saveAll() {
        val cfg = plugin.config

        // Spawnpunkte
        cfg.set("spawnpoints", null)
        for ((num, spawn) in MurderGame.spawnPoints) {
            val p = "spawnpoints.$num"
            cfg.set("$p.world", spawn.location.world?.name)
            cfg.set("$p.x",    spawn.location.x)
            cfg.set("$p.y",    spawn.location.y)
            cfg.set("$p.z",    spawn.location.z)
            cfg.set("$p.yaw",  spawn.location.yaw.toDouble())
            cfg.set("$p.pitch",spawn.location.pitch.toDouble())
            cfg.set("$p.name", spawn.name)
        }

        // Goldpunkte
        cfg.set("goldpoints", null)
        for ((num, gold) in MurderGame.goldPoints) {
            val p = "goldpoints.$num"
            cfg.set("$p.world", gold.location.world?.name)
            cfg.set("$p.x",    gold.location.x)
            cfg.set("$p.y",    gold.location.y)
            cfg.set("$p.z",    gold.location.z)
            cfg.set("$p.yaw",  gold.location.yaw.toDouble())
            cfg.set("$p.pitch",gold.location.pitch.toDouble())
            cfg.set("$p.name", gold.name)
        }

        // Lobby
        cfg.set("lobby", null)
        MurderGame.lobbySpawn?.let { loc ->
            cfg.set("lobby.world", loc.world?.name)
            cfg.set("lobby.x",    loc.x)
            cfg.set("lobby.y",    loc.y)
            cfg.set("lobby.z",    loc.z)
            cfg.set("lobby.yaw",  loc.yaw.toDouble())
            cfg.set("lobby.pitch",loc.pitch.toDouble())
        }

        // Einstellungen
        cfg.set("settings.maxPlayers", MurderGame.configuredMaxPlayers)

        // Messer-Skins
        cfg.set("skins.knife", null)
        for ((uuid, mat) in MurderGame.playerKnifeSkins) {
            cfg.set("skins.knife.$uuid", mat.name)
        }

        // Bogen-Skins
        cfg.set("skins.bow", null)
        for ((uuid, index) in MurderGame.playerBowSkinIndex) {
            cfg.set("skins.bow.$uuid", index)
        }

        // Pfeil-Skins
        cfg.set("skins.arrow", null)
        for ((uuid, index) in MurderGame.playerArrowSkinIndex) {
            cfg.set("skins.arrow.$uuid", index)
        }

        // Rollenverlauf
        cfg.set("stats.murder", null)
        for ((uuid, count) in MurderGame.playerMurderCount) cfg.set("stats.murder.$uuid", count)
        cfg.set("stats.detective", null)
        for ((uuid, count) in MurderGame.playerDetectiveCount) cfg.set("stats.detective.$uuid", count)
        cfg.set("stats.games", null)
        for ((uuid, count) in MurderGame.playerTotalGames) cfg.set("stats.games.$uuid", count)

        plugin.saveConfig()
    }
}
