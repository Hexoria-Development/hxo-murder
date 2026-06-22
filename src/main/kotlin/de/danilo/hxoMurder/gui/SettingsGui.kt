package de.danilo.hxoMurder.gui

import de.danilo.hxoMurder.ConfigManager
import de.danilo.hxoMurder.MurderGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object SettingsGui : Listener {

    private val openInventories = mutableSetOf<Inventory>()

    // 36 Slots (4 Reihen): Einstellung in Reihe 1, Buttons in Reihe 3
    private const val SLOT_COUNT = 13
    private const val SLOT_BACK  = 27
    private const val SLOT_SAVE  = 35

    fun open(player: Player) {
        val inv = buildInventory()
        openInventories.add(inv)
        player.openInventory(inv)
    }

    private fun buildInventory(): Inventory {
        val inv = Bukkit.createInventory(
            null, 36,
            Component.text("Einstellungen", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, true)
        )
        inv.setItem(SLOT_COUNT, buildCountItem())
        inv.setItem(SLOT_BACK, makeBack())
        inv.setItem(SLOT_SAVE, makeSave())
        val filler = makeFiller()
        for (i in 0 until 36) {
            if (inv.getItem(i) == null) inv.setItem(i, filler.clone())
        }
        return inv
    }

    private fun buildCountItem(): ItemStack {
        val count = MurderGame.configuredMaxPlayers
        val item = ItemStack(Material.LIME_CONCRETE)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Max. Spieler: $count", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
        )
        meta.lore(listOf(
            Component.text("Linksklick: +1 Spieler", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
            Component.text("Rechtsklick: -1 Spieler", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Bereich: 2 - 10", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        return item
    }

    private fun makeFiller(): ItemStack {
        val item = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
        item.itemMeta = meta
        return item
    }

    private fun makeBack(): ItemStack {
        val item = ItemStack(Material.RED_CONCRETE)
        val meta = item.itemMeta
        meta.displayName(Component.text("← Zurueck", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
        item.itemMeta = meta
        return item
    }

    private fun makeSave(): ItemStack {
        val item = ItemStack(Material.GREEN_CONCRETE)
        val meta = item.itemMeta
        meta.displayName(Component.text("✔ Speichern", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true))
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory !in openInventories) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return

        when (event.rawSlot) {
            SLOT_BACK -> { player.closeInventory(); MurderMainGui.open(player); return }
            SLOT_SAVE -> {
                player.sendMessage(Component.text("[Murder] ", NamedTextColor.RED)
                    .append(Component.text("Einstellungen gespeichert!", NamedTextColor.GREEN)))
                player.closeInventory()
                MurderMainGui.open(player)
                return
            }
            SLOT_COUNT -> {
                val delta = when (event.click) {
                    ClickType.LEFT  -> 1
                    ClickType.RIGHT -> -1
                    else            -> return
                }
                MurderGame.configuredMaxPlayers = (MurderGame.configuredMaxPlayers + delta).coerceIn(2, 10)
                ConfigManager.saveAll()
                val updated = buildCountItem()
                openInventories.forEach { it.setItem(SLOT_COUNT, updated) }
                player.sendActionBar(
                    Component.text("Max. Spieler: ${MurderGame.configuredMaxPlayers}", NamedTextColor.GREEN)
                )
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove(event.inventory)
    }
}
