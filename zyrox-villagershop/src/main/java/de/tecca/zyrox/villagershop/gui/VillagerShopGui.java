package de.tecca.zyrox.villagershop.gui;

import de.tecca.zyrox.villagershop.storage.VillagerShopRecord;
import de.tecca.zyrox.villagershop.text.VillagerShopText;
import de.tecca.zyrox.villagershop.util.ItemStacks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VillagerShopGui {

    public static final int SLOT_TRADE_REMOVE = 2;
    public static final int SLOT_TRADE_PREVIOUS = 3;
    public static final int SLOT_TRADE_INFO = 4;
    public static final int SLOT_TRADE_NEXT = 5;
    public static final int SLOT_TRADE_ADD = 6;
    public static final int SLOT_INPUT_ONE = 19;
    public static final int SLOT_INPUT_TWO = 21;
    public static final int SLOT_OUTPUT = 25;
    public static final int SLOT_PROFESSION = 28;
    public static final int SLOT_USES_DOWN = 30;
    public static final int SLOT_USES_INFO = 31;
    public static final int SLOT_USES_UP = 32;
    public static final int SLOT_UNLIMITED = 34;
    public static final int SLOT_RESET_USES = 37;
    public static final int SLOT_SAVE = 40;
    public static final int SLOT_DELETE = 53;

    private VillagerShopGui() {
    }

    public static Inventory editor(VillagerShopRecord shop, VillagerShopText text) {
        VillagerShopEditorHolder holder = new VillagerShopEditorHolder(shop);
        Inventory inventory = Bukkit.createInventory(holder, 54, text.gui("editor.title", text.placeholders(shop)));
        renderEditor(inventory, holder, text);
        return inventory;
    }

    public static void renderEditor(Inventory inventory, VillagerShopEditorHolder holder, VillagerShopText text) {
        inventory.clear();
        Map<String, String> placeholders = placeholders(holder, text);
        inventory.setItem(SLOT_TRADE_REMOVE, button(Material.BARRIER, text.gui("editor.trade-remove-name", placeholders), text.guiList("editor.trade-remove-lore", placeholders)));
        inventory.setItem(SLOT_TRADE_PREVIOUS, button(Material.ARROW, text.gui("editor.trade-previous-name", placeholders), text.guiList("editor.trade-select-lore", placeholders)));
        inventory.setItem(SLOT_TRADE_INFO, button(Material.WRITABLE_BOOK, text.gui("editor.trade-info-name", placeholders), text.guiList("editor.trade-info-lore", placeholders)));
        inventory.setItem(SLOT_TRADE_NEXT, button(Material.ARROW, text.gui("editor.trade-next-name", placeholders), text.guiList("editor.trade-select-lore", placeholders)));
        inventory.setItem(SLOT_TRADE_ADD, button(Material.EMERALD_BLOCK, text.gui("editor.trade-add-name", placeholders), text.guiList("editor.trade-add-lore", placeholders)));
        inventory.setItem(SLOT_INPUT_ONE, tradeItem(holder.inputOne(), text.gui("editor.input-one-name", placeholders), text, placeholders));
        inventory.setItem(SLOT_INPUT_TWO, tradeItem(holder.inputTwo(), text.gui("editor.input-two-name", placeholders), text, placeholders));
        inventory.setItem(SLOT_OUTPUT, tradeItem(holder.output(), text.gui("editor.output-name", placeholders), text, placeholders));
        inventory.setItem(SLOT_PROFESSION, button(Material.VILLAGER_SPAWN_EGG, text.gui("editor.profession-name", placeholders), text.guiList("editor.profession-lore", placeholders)));
        inventory.setItem(SLOT_USES_DOWN, button(Material.REDSTONE, text.gui("editor.uses-down-name", placeholders), text.guiList("editor.uses-lore", placeholders)));
        inventory.setItem(SLOT_USES_INFO, button(Material.CLOCK, text.gui("editor.uses-info-name", placeholders), text.guiList("editor.uses-lore", placeholders)));
        inventory.setItem(SLOT_USES_UP, button(Material.EMERALD, text.gui("editor.uses-up-name", placeholders), text.guiList("editor.uses-lore", placeholders)));
        inventory.setItem(SLOT_UNLIMITED, button(holder.unlimited() ? Material.LIME_DYE : Material.GRAY_DYE,
                text.gui("editor.unlimited-name", placeholders), text.guiList("editor.unlimited-lore", placeholders)));
        inventory.setItem(SLOT_RESET_USES, button(Material.HONEY_BOTTLE, text.gui("editor.reset-uses-name", placeholders), text.guiList("editor.uses-lore", placeholders)));
        inventory.setItem(SLOT_SAVE, button(Material.LIME_STAINED_GLASS_PANE, text.gui("editor.save-name", placeholders), text.guiList("editor.save-lore", placeholders)));
        inventory.setItem(SLOT_DELETE, button(Material.TNT, text.gui("editor.delete-name", placeholders), text.guiList("editor.delete-lore", placeholders)));
    }

    private static ItemStack tradeItem(ItemStack source, String name, VillagerShopText text, Map<String, String> placeholders) {
        if (source == null || source.getType().isAir()) {
            return button(Material.GRAY_STAINED_GLASS_PANE, text.gui("editor.empty-name", placeholders), text.guiList("editor.empty-lore", placeholders));
        }
        return named(ItemStacks.displayItem(source), name, text.guiList("editor.item-lore", placeholders));
    }

    private static Map<String, String> placeholders(VillagerShopEditorHolder holder, VillagerShopText text) {
        Map<String, String> placeholders = new HashMap<>(text.placeholders(holder.shop()));
        placeholders.put("trade_index", Integer.toString(holder.selectedIndex() + 1));
        placeholders.put("trade_count", Integer.toString(holder.shop().trades().size()));
        placeholders.put("profession", holder.profession());
        placeholders.put("max_uses", holder.unlimited() ? "unbegrenzt" : Integer.toString(holder.maxUses()));
        placeholders.put("uses", Integer.toString(holder.currentUses()));
        placeholders.put("unlimited", holder.unlimited() ? "Ja" : "Nein");
        return placeholders;
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return named(new ItemStack(material), name, lore);
    }

    private static ItemStack named(ItemStack source, String name, List<String> lore) {
        ItemStack item = source == null ? new ItemStack(Material.BARRIER) : source.clone();
        item.setAmount(Math.max(1, item.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>();
            if (lore != null) {
                lines.addAll(lore);
            }
            meta.setLore(lines);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}

