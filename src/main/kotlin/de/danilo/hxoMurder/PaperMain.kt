package de.danilo.hxoMurder

import de.danilo.hxoMurder.commands.MurderCommand
import de.danilo.hxoMurder.gui.BowSkinGui
import de.danilo.hxoMurder.gui.KnifeSkinGui
import de.danilo.hxoMurder.gui.MurderMainGui
import de.danilo.hxoMurder.gui.SettingsGui
import de.danilo.hxoMurder.listeners.MurderListener
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
        MurderGame.reset()
        ConfigManager.saveAll()
        logger.info("hxo-Murder Plugin deaktiviert!")
    }
}
