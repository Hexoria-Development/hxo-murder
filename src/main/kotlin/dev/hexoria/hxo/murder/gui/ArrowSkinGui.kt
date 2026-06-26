package dev.hexoria.hxo.murder.gui

import dev.hexoria.hxo.murder.ConfigManager
import dev.hexoria.hxo.murder.MurderGame
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

object ArrowSkinGui : Listener {

    private val openInventories = mutableSetOf<Inventory>()

    data class ArrowSkin(val displayName: String, val color: NamedTextColor)

    val SKINS = listOf(
        ArrowSkin("Normaler Pfeil",  NamedTextColor.WHITE),
        ArrowSkin("Eisenpfeil",      NamedTextColor.GRAY),
        ArrowSkin("Goldpfeil",       NamedTextColor.GOLD),
        ArrowSkin("Feuerpfeil",      NamedTextColor.RED),
        ArrowSkin("Frostpfeil",      NamedTextColor.AQUA),
        ArrowSkin("Schattenpfeil",   NamedTextColor.DARK_PURPLE),
        ArrowSkin("Blitzpfeil",      NamedTextColor.YELLOW),
        ArrowSkin("Giftstachel",     NamedTextColor.GREEN),
        ArrowSkin("Kaiserspfeil",    NamedTextColor.DARK_AQUA),
    )

    private const val SLOT_BACK = 27
    private const val SLOT_SAVE = 35

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

    fun open(player: Player) {
        val currentIndex = MurderGame.playerArrowSkinIndex[player.uniqueId] ?: 0
        val inv = Bukkit.createInventory(
            null, 36,
            Component.text("Pfeil-Skin waehlen", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
        )

        SKINS.forEachIndexed { i, skin ->
            inv.setItem(i + 9, makeGuiItem(skin, i == currentIndex))
        }

        val filler = makeFiller()
        for (i in 0 until 36) {
            if (inv.getItem(i) == null) inv.setItem(i, filler.clone())
        }

        inv.setItem(SLOT_BACK, makeBack())
        inv.setItem(SLOT_SAVE, makeSave())

        openInventories.add(inv)
        player.openInventory(inv)
    }

    fun makeArrowItem(skin: ArrowSkin): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("◆ ${skin.displayName}", skin.color)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return item
    }

    private fun makeGuiItem(skin: ArrowSkin, selected: Boolean): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        meta.displayName(
            Component.text((if (selected) "✔ " else "◆ ") + skin.displayName, skin.color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, selected)
        )
        meta.lore(listOf(
            Component.text("Nur kosmetisch", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
            if (selected)
                Component.text("Aktuell ausgewaehlt", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
            else
                Component.text("Klicke zum Auswaehlen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ))
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
                    .append(Component.text("Pfeil-Skin gespeichert!", NamedTextColor.GREEN)))
                player.closeInventory()
                MurderMainGui.open(player)
                return
            }
        }

        val skinIndex = event.rawSlot - 9
        if (skinIndex !in SKINS.indices) return
        val skin = SKINS[skinIndex]

        MurderGame.playerArrowSkinIndex[player.uniqueId] = skinIndex
        ConfigManager.saveAll()

        if (MurderGame.running && player.uniqueId == MurderGame.detectiveId) {
            val current = player.inventory.getItem(2)
            if (current != null && current.type == Material.ARROW) {
                player.inventory.setItem(2, makeArrowItem(skin))
            }
        }

        SKINS.forEachIndexed { i, s ->
            event.inventory.setItem(i + 9, makeGuiItem(s, i == skinIndex))
        }

        player.sendActionBar(
            Component.text("Pfeil-Skin ausgewaehlt: ", NamedTextColor.WHITE)
                .append(Component.text(skin.displayName, skin.color))
        )
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove(event.inventory)
    }
}
