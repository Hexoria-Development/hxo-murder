package dev.hexoria.hxo.murder.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object MurderMainGui : Listener {

    private val openInventories = mutableSetOf<Inventory>()

    private const val SLOT_KNIFE    = 10
    private const val SLOT_ARROW    = 12
    private const val SLOT_SETTINGS = 14
    private const val SLOT_BOW      = 16

    fun open(player: Player) {
        val inv = Bukkit.createInventory(
            null, 27,
            Component.text("Murder - Hauptmenue", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
        )

        inv.setItem(SLOT_KNIFE, makeButton(
            Material.IRON_SWORD,
            "Messer-Skin waehlen",
            NamedTextColor.RED,
            "Aendere den Look deiner Waffe"
        ))
        inv.setItem(SLOT_ARROW, makeButton(
            Material.ARROW,
            "Pfeil-Skin waehlen",
            NamedTextColor.YELLOW,
            "Aendere den Look deines Pfeils"
        ))
        inv.setItem(SLOT_SETTINGS, makeButton(
            Material.COMPARATOR,
            "Einstellungen",
            NamedTextColor.YELLOW,
            "Max. Spielerzahl und mehr"
        ))
        inv.setItem(SLOT_BOW, makeButton(
            Material.BOW,
            "Bogen-Skin waehlen",
            NamedTextColor.AQUA,
            "Aendere den Look des Detektiv-Bogens"
        ))

        val filler = makeFiller()
        for (i in 0 until 27) {
            if (inv.getItem(i) == null) inv.setItem(i, filler.clone())
        }

        openInventories.add(inv)
        player.openInventory(inv)
    }

    private fun makeButton(mat: Material, name: String, color: NamedTextColor, desc: String): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta.displayName(
            Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
        )
        meta.lore(listOf(
            Component.text(desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
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

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory !in openInventories) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        when (event.rawSlot) {
            SLOT_KNIFE    -> { player.closeInventory(); KnifeSkinGui.open(player) }
            SLOT_ARROW    -> { player.closeInventory(); ArrowSkinGui.open(player) }
            SLOT_SETTINGS -> { player.closeInventory(); SettingsGui.open(player) }
            SLOT_BOW      -> { player.closeInventory(); BowSkinGui.open(player) }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove(event.inventory)
    }
}
