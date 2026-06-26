package dev.hexoria.hxo.murder

import dev.hexoria.hxo.murder.commands.MurderCommand
import dev.hexoria.hxo.murder.gui.ArrowSkinGui
import dev.hexoria.hxo.murder.gui.BowSkinGui
import dev.hexoria.hxo.murder.gui.KnifeSkinGui
import dev.hexoria.hxo.murder.gui.MurderMainGui
import dev.hexoria.hxo.murder.gui.SettingsGui
import dev.hexoria.hxo.murder.listeners.MurderListener
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class PaperMain : JavaPlugin() {

    override fun onEnable() {
        ConfigManager.init(this)
        ConfigManager.loadAll()

        val listener = MurderListener(this)
        server.pluginManager.registerEvents(listener, this)
        server.pluginManager.registerEvents(KnifeSkinGui, this)
        server.pluginManager.registerEvents(BowSkinGui, this)
        server.pluginManager.registerEvents(ArrowSkinGui, this)
        server.pluginManager.registerEvents(SettingsGui, this)
        server.pluginManager.registerEvents(MurderMainGui, this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            MurderCommand.register(event.registrar(), this, listener)
        }
        logger.info("hxo-Murder Plugin aktiviert!")
    }

    override fun onDisable() {
        if (MurderGame.showingBeacons) {
            for (spawn in MurderGame.spawnPoints.values) {
                for ((loc, _) in MurderGame.fakeBeaconBlocks(spawn.location)) {
                    val world = loc.world ?: continue
                    val realData = world.getBlockAt(loc).blockData
                    for (player in server.onlinePlayers) {
                        player.sendBlockChange(loc, realData)
                    }
                }
            }
            MurderGame.showingBeacons = false
        }
        if (MurderGame.showingGoldBeacons) {
            for (gold in MurderGame.goldPoints.values) {
                for ((loc, _) in MurderGame.fakeGoldBeaconBlocks(gold.location)) {
                    val world = loc.world ?: continue
                    val realData = world.getBlockAt(loc).blockData
                    for (player in server.onlinePlayers) {
                        player.sendBlockChange(loc, realData)
                    }
                }
            }
            MurderGame.showingGoldBeacons = false
        }
        MurderGame.reset()
        ConfigManager.saveAll()
        logger.info("hxo-Murder Plugin deaktiviert!")
    }
}
