package de.tecca.zyrox.shop.gui;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.text.ShopText;
import de.tecca.zyrox.shop.util.ItemStacks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class ShopGui {

    public static final int SLOT_TEMPLATE = 10;
    public static final int SLOT_PRICE_DOWN = 11;
    public static final int SLOT_PRICE_INFO = 12;
    public static final int SLOT_PRICE_UP = 13;
    public static final int SLOT_BUNDLE_DOWN = 14;
    public static final int SLOT_BUNDLE_INFO = 15;
    public static final int SLOT_BUNDLE_UP = 16;
    public static final int SLOT_DEPOSIT = 19;
    public static final int SLOT_WITHDRAW_BUNDLE = 20;
    public static final int SLOT_WITHDRAW_ALL = 21;
    public static final int SLOT_REVENUE = 22;
    public static final int SLOT_DELETE = 25;

    public static final int[] PURCHASE_SLOTS = {11, 12, 13, 14, 15};
    public static final long[] PURCHASE_BUNDLES = {1, 2, 4, 8, -1};

    private ShopGui() {
    }

    public static Inventory editor(ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text) {
        ShopEditorHolder holder = new ShopEditorHolder(shop);
        Inventory inventory = Bukkit.createInventory(holder, 27, text.gui("editor.title", text.placeholders(shop)));
        renderEditor(inventory, shop, payments, text);
        return inventory;
    }

    public static void renderEditor(Inventory inventory, ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text) {
        inventory.clear();
        Map<String, String> placeholders = text.placeholders(shop);
        inventory.setItem(SLOT_TEMPLATE, named(shop.itemTemplate(), text.gui("editor.selling-name", placeholders),
                text.guiList("editor.selling-lore", placeholders)));
        inventory.setItem(SLOT_PRICE_DOWN, button(Material.REDSTONE, text.gui("editor.price-down-name", placeholders), text.guiList("editor.current-price", placeholders)));
        inventory.setItem(SLOT_PRICE_INFO, button(payments.currencyMaterial(), text.gui("editor.price-info-name", placeholders), text.guiList("editor.current-price", placeholders)));
        inventory.setItem(SLOT_PRICE_UP, button(Material.EMERALD, text.gui("editor.price-up-name", placeholders), text.guiList("editor.current-price", placeholders)));
        inventory.setItem(SLOT_BUNDLE_DOWN, button(Material.HOPPER, text.gui("editor.bundle-down-name", placeholders), text.guiList("editor.current-bundle", placeholders)));
        inventory.setItem(SLOT_BUNDLE_INFO, button(Material.CHEST, text.gui("editor.bundle-info-name", placeholders), text.guiList("editor.current-bundle", placeholders)));
        inventory.setItem(SLOT_BUNDLE_UP, button(Material.DROPPER, text.gui("editor.bundle-up-name", placeholders), text.guiList("editor.current-bundle", placeholders)));
        inventory.setItem(SLOT_DEPOSIT, button(Material.LIME_STAINED_GLASS_PANE, text.gui("editor.deposit-name", placeholders), text.guiList("editor.deposit-lore", placeholders)));
        inventory.setItem(SLOT_WITHDRAW_BUNDLE, button(Material.YELLOW_STAINED_GLASS_PANE, text.gui("editor.withdraw-bundle-name", placeholders), text.guiList("editor.withdraw-bundle-lore", placeholders)));
        inventory.setItem(SLOT_WITHDRAW_ALL, button(Material.ORANGE_STAINED_GLASS_PANE, text.gui("editor.withdraw-all-name", placeholders), text.guiList("editor.withdraw-all-lore", placeholders)));
        inventory.setItem(SLOT_REVENUE, button(Material.DIAMOND, text.gui("editor.revenue-name", placeholders), text.guiList("editor.revenue-lore", placeholders)));
        inventory.setItem(SLOT_DELETE, button(Material.BARRIER, text.gui("editor.delete-name", placeholders), text.guiList("editor.delete-lore", placeholders)));
    }

    public static Inventory purchase(ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text) {
        ShopPurchaseHolder holder = new ShopPurchaseHolder(shop);
        Inventory inventory = Bukkit.createInventory(holder, 27, text.gui("purchase.title", text.placeholders(shop)));
        renderPurchase(inventory, shop, payments, text);
        return inventory;
    }

    public static void renderPurchase(Inventory inventory, ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text) {
        inventory.clear();
        Map<String, String> placeholders = text.placeholders(shop);
        inventory.setItem(4, named(shop.itemTemplate(), shop.bundleAmount() + "x " + ItemStacks.displayName(shop.itemTemplate()),
                text.guiList("purchase.item-lore", placeholders)));
        for (int index = 0; index < PURCHASE_SLOTS.length; index++) {
            long bundles = PURCHASE_BUNDLES[index] == -1 ? shop.maxBundles() : PURCHASE_BUNDLES[index];
            long items = safeMultiply(shop.bundleAmount(), bundles);
            long price = safeMultiply(shop.priceDiamonds(), bundles);
            Map<String, String> buyPlaceholders = new HashMap<>(placeholders);
            buyPlaceholders.put("bundles", Long.toString(bundles));
            buyPlaceholders.put("amount", Long.toString(items));
            buyPlaceholders.put("price", payments.formatDiamonds(price));
            String label = PURCHASE_BUNDLES[index] == -1
                    ? text.gui("purchase.buy-max-name", buyPlaceholders)
                    : text.gui("purchase.buy-name", buyPlaceholders);
            inventory.setItem(PURCHASE_SLOTS[index], button(Material.LIME_STAINED_GLASS_PANE, label,
                    text.guiList("purchase.buy-lore", buyPlaceholders)));
        }
    }

    private static long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return named(new ItemStack(material), name, lore);
    }

    private static ItemStack named(ItemStack source, String name, List<String> lore) {
        ItemStack item = source == null ? new ItemStack(Material.BARRIER) : source.clone();
        item.setAmount(1);
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
