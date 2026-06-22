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

object KnifeSkinGui : Listener {

    private val openInventories = mutableSetOf<Inventory>()

    data class WeaponOption(val material: Material, val displayName: String, val color: NamedTextColor)

    val WEAPONS = listOf(
        // Schwerter (i 0-5 → Slots 0-5)
        WeaponOption(Material.WOODEN_SWORD,    "Holzmesser",      NamedTextColor.DARK_GRAY),
        WeaponOption(Material.STONE_SWORD,     "Steinmesser",     NamedTextColor.GRAY),
        WeaponOption(Material.IRON_SWORD,      "Eisenmesser",     NamedTextColor.WHITE),
        WeaponOption(Material.GOLDEN_SWORD,    "Goldmesser",      NamedTextColor.GOLD),
        WeaponOption(Material.DIAMOND_SWORD,   "Diamantmesser",   NamedTextColor.AQUA),
        WeaponOption(Material.NETHERITE_SWORD, "Netheritklinke",  NamedTextColor.DARK_PURPLE),
        // Äxte (i 6-11 → Slots 9-14)
        WeaponOption(Material.WOODEN_AXE,      "Holzaxt",         NamedTextColor.DARK_GRAY),
        WeaponOption(Material.STONE_AXE,       "Steinaxt",        NamedTextColor.GRAY),
        WeaponOption(Material.IRON_AXE,        "Eisenaxt",        NamedTextColor.WHITE),
        WeaponOption(Material.GOLDEN_AXE,      "Goldaxt",         NamedTextColor.GOLD),
        WeaponOption(Material.DIAMOND_AXE,     "Diamantaxt",      NamedTextColor.AQUA),
        WeaponOption(Material.NETHERITE_AXE,   "Netheritspalter", NamedTextColor.DARK_PURPLE),
        // Schaufeln (i 12-17 → Slots 18-23)
        WeaponOption(Material.WOODEN_SHOVEL,    "Holzschaufel",    NamedTextColor.DARK_GRAY),
        WeaponOption(Material.STONE_SHOVEL,     "Steinschaufel",   NamedTextColor.GRAY),
        WeaponOption(Material.IRON_SHOVEL,      "Eisenschaufel",   NamedTextColor.WHITE),
        WeaponOption(Material.GOLDEN_SHOVEL,    "Goldschaufel",    NamedTextColor.GOLD),
        WeaponOption(Material.DIAMOND_SHOVEL,   "Diamantschaufel", NamedTextColor.AQUA),
        WeaponOption(Material.NETHERITE_SHOVEL, "Netheritstich",   NamedTextColor.DARK_PURPLE),
        // Hacken + Sonstiges (i 18-26 → Slots 27-35)
        WeaponOption(Material.IRON_HOE,         "Eisensense",      NamedTextColor.WHITE),
        WeaponOption(Material.GOLDEN_HOE,       "Goldsichel",      NamedTextColor.GOLD),
        WeaponOption(Material.DIAMOND_HOE,      "Diamantsense",    NamedTextColor.AQUA),
        WeaponOption(Material.NETHERITE_HOE,    "Netheritsense",   NamedTextColor.DARK_PURPLE),
        WeaponOption(Material.BLAZE_ROD,        "Feuerstab",       NamedTextColor.YELLOW),
        WeaponOption(Material.BONE,             "Knochen",         NamedTextColor.WHITE),
        WeaponOption(Material.STICK,            "Spitze",          NamedTextColor.DARK_GRAY),
        WeaponOption(Material.WOODEN_HOE,       "Holzhacke",       NamedTextColor.DARK_GRAY),
    )

    val WEAPON_MATERIALS: Set<Material> = WEAPONS.map { it.material }.toSet()

    // 45 Slots (5 Reihen): Waffen in Reihen 0-3, Buttons in Reihe 4
    private const val SLOT_BACK = 36
    private const val SLOT_SAVE = 44

    private fun slot(index: Int): Int = when {
        index < 6  -> index
        index < 12 -> index + 3
        index < 18 -> index + 6
        else       -> index + 9
    }

    // Umkehrung: Slot → Waffen-Index (null = kein Waffenslot)
    private fun weaponIndex(rawSlot: Int): Int? = when (rawSlot) {
        in 0..5   -> rawSlot
        in 9..14  -> rawSlot - 3
        in 18..23 -> rawSlot - 6
        in 27..35 -> rawSlot - 9
        else      -> null
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

    fun open(player: Player) {
        val currentMat = MurderGame.playerKnifeSkins[player.uniqueId] ?: Material.IRON_SWORD
        val inv = Bukkit.createInventory(
            null, 45,
            Component.text("Waffen-Skin waehlen", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
        )

        WEAPONS.forEachIndexed { i, weapon ->
            val s = slot(i)
            val selected = weapon.material == currentMat
            val item = ItemStack(weapon.material)
            val meta = item.itemMeta
            meta.displayName(
                Component.text((if (selected) "✔ " else "◆ ") + weapon.displayName, weapon.color)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, selected)
            )
            meta.lore(listOf(
                Component.text("One-Shot Waffe", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                if (selected)
                    Component.text("Aktuell ausgewaehlt", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                else
                    Component.text("Klicke zum Auswaehlen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            item.itemMeta = meta
            inv.setItem(s, item)
        }

        val filler = makeFiller()
        for (i in 0 until 45) {
            if (inv.getItem(i) == null) inv.setItem(i, filler.clone())
        }

        inv.setItem(SLOT_BACK, makeBack())
        inv.setItem(SLOT_SAVE, makeSave())

        openInventories.add(inv)
        player.openInventory(inv)
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
                    .append(Component.text("Skin gespeichert!", NamedTextColor.GREEN)))
                player.closeInventory()
                MurderMainGui.open(player)
                return
            }
        }

        val idx = weaponIndex(event.rawSlot) ?: return
        if (idx !in WEAPONS.indices) return
        val weapon = WEAPONS[idx]

        MurderGame.playerKnifeSkins[player.uniqueId] = weapon.material
        ConfigManager.saveAll()

        if (MurderGame.running && player.uniqueId == MurderGame.murderId) {
            player.inventory.setItem(1, ItemStack(weapon.material))
        }

        // Alle Waffenslots aktualisieren (Häkchen verschieben)
        WEAPONS.forEachIndexed { i, w ->
            val s = slot(i)
            val selected = w.material == weapon.material
            val item = ItemStack(w.material)
            val meta = item.itemMeta
            meta.displayName(
                Component.text((if (selected) "✔ " else "◆ ") + w.displayName, w.color)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, selected)
            )
            meta.lore(listOf(
                Component.text("One-Shot Waffe", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                if (selected)
                    Component.text("Aktuell ausgewaehlt", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                else
                    Component.text("Klicke zum Auswaehlen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ))
            item.itemMeta = meta
            event.inventory.setItem(s, item)
        }

        player.sendActionBar(
            Component.text("Skin ausgewaehlt: ", NamedTextColor.WHITE)
                .append(Component.text(weapon.displayName, weapon.color))
        )
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove(event.inventory)
    }
}
