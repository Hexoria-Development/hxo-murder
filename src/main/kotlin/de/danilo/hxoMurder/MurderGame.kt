package de.danilo.hxoMurder

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

data class SpawnPoint(val location: Location, val name: String)

object MurderGame {
    val spawnPoints = mutableMapOf<Int, SpawnPoint>()
    var showingBeacons = false
    var lobbySpawn: Location? = null

    var running = false
    var murderId: UUID? = null
    var detectiveId: UUID? = null
    val alivePlayers = mutableSetOf<UUID>()
    val allGamePlayers = mutableSetOf<UUID>()

    // Persistent skins (survive game resets)
    val playerKnifeSkins = mutableMapOf<UUID, Material>()
    val playerBowSkinIndex = mutableMapOf<UUID, Int>()

    // Settings (persist between games)
    var configuredMaxPlayers: Int = 10

    // Round state
    var arrowCooldownTask: BukkitTask? = null
    var detectiveArrowReady = true
    val frozenPlayers = mutableSetOf<UUID>()

    // Dropped detective bow tracking (item entity UUIDs)
    val droppedDetectiveBows = mutableSetOf<UUID>()

    // Bow drop location + players who must move away before pickup
    val bowDropLocations = mutableMapOf<UUID, org.bukkit.Location>()
    val playersNearBowOnDrop = mutableMapOf<UUID, MutableSet<UUID>>()

    fun fakeBeaconBlocks(spawnLoc: Location): List<Pair<Location, Material>> {
        val bx = spawnLoc.blockX
        val by = spawnLoc.blockY
        val bz = spawnLoc.blockZ
        val world = spawnLoc.world
        return buildList {
            add(Location(world, bx.toDouble(), (by - 1).toDouble(), bz.toDouble()) to Material.BEACON)
            for (dx in -1..1) for (dz in -1..1) {
                add(Location(world, (bx + dx).toDouble(), (by - 2).toDouble(), (bz + dz).toDouble()) to Material.IRON_BLOCK)
            }
            add(Location(world, bx.toDouble(), (by + 1).toDouble(), bz.toDouble()) to Material.YELLOW_STAINED_GLASS)
        }
    }

    fun reset() {
        running = false
        murderId = null
        detectiveId = null
        alivePlayers.clear()
        allGamePlayers.clear()
        arrowCooldownTask?.cancel()
        arrowCooldownTask = null
        detectiveArrowReady = true
        frozenPlayers.clear()
        droppedDetectiveBows.clear()
        bowDropLocations.clear()
        playersNearBowOnDrop.clear()
    }
}
