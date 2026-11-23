package de.tecca.zyrox.shop.gui;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopTransactionRecord;
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
    public static final int SLOT_DISCOUNT = 23;
    public static final int SLOT_HISTORY = 24;
    public static final int SLOT_DELETE = 25;

    public static final int SLOT_PURCHASE_ITEM = 4;
    public static final int SLOT_PURCHASE_FAVORITE = 20;
    public static final int SLOT_PURCHASE_RATING = 22;
    public static final int SLOT_PURCHASE_FAVORITES = 24;
    public static final int[] PURCHASE_SLOTS = {11, 12, 13, 14, 15};
    public static final long[] PURCHASE_BUNDLES = {1, 2, 4, 8, -1};
    public static final int[] RATING_SLOTS = {10, 11, 12, 13, 14};
    public static final int[] LIST_ENTRY_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    public static final int SLOT_PREVIOUS_PAGE = 18;
    public static final int SLOT_BACK = 22;
    public static final int SLOT_NEXT_PAGE = 26;
    public static final int SLOT_DISCOUNT_PERCENT_DOWN = 10;
    public static final int SLOT_DISCOUNT_PERCENT_INFO = 11;
    public static final int SLOT_DISCOUNT_PERCENT_UP = 12;
    public static final int SLOT_DISCOUNT_DURATION_PERMANENT = 14;
    public static final int SLOT_DISCOUNT_DURATION_30M = 15;
    public static final int SLOT_DISCOUNT_DURATION_2H = 16;
    public static final int SLOT_DISCOUNT_DURATION_7D = 17;
    public static final int SLOT_DISCOUNT_SAVE = 20;
    public static final int SLOT_DISCOUNT_CLEAR = 24;

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
        inventory.setItem(SLOT_DISCOUNT, button(Material.GOLD_INGOT, text.gui("editor.discount-name", placeholders), text.guiList("editor.discount-lore", placeholders)));
        inventory.setItem(SLOT_HISTORY, button(Material.WRITABLE_BOOK, text.gui("editor.history-name", placeholders), text.guiList("editor.history-lore", placeholders)));
        inventory.setItem(SLOT_DELETE, button(Material.BARRIER, text.gui("editor.delete-name", placeholders), text.guiList("editor.delete-lore", placeholders)));
    }

    public static void renderDiscount(Inventory inventory, ShopEditorHolder holder, ShopText text) {
        inventory.clear();
        Map<String, String> placeholders = new HashMap<>(text.placeholders(holder.shop()));
        placeholders.put("selected_discount_percent", Integer.toString(holder.discountPercent()));
        placeholders.put("selected_discount_duration", durationLabel(holder.discountDuration(), text));
        inventory.setItem(SLOT_DISCOUNT_PERCENT_DOWN, button(Material.REDSTONE, text.gui("editor.discount-menu.percent-down-name", placeholders),
                text.guiList("editor.discount-menu.percent-lore", placeholders)));
        inventory.setItem(SLOT_DISCOUNT_PERCENT_INFO, button(Material.GOLD_INGOT, text.gui("editor.discount-menu.percent-info-name", placeholders),
                text.guiList("editor.discount-menu.percent-lore", placeholders)));
        inventory.setItem(SLOT_DISCOUNT_PERCENT_UP, button(Material.EMERALD, text.gui("editor.discount-menu.percent-up-name", placeholders),
                text.guiList("editor.discount-menu.percent-lore", placeholders)));
        inventory.setItem(SLOT_DISCOUNT_DURATION_PERMANENT, durationButton(holder, ShopEditorHolder.DiscountDuration.PERMANENT, text, placeholders));
        inventory.setItem(SLOT_DISCOUNT_DURATION_30M, durationButton(holder, ShopEditorHolder.DiscountDuration.THIRTY_MINUTES, text, placeholders));
        inventory.setItem(SLOT_DISCOUNT_DURATION_2H, durationButton(holder, ShopEditorHolder.DiscountDuration.TWO_HOURS, text, placeholders));
        inventory.setItem(SLOT_DISCOUNT_DURATION_7D, durationButton(holder, ShopEditorHolder.DiscountDuration.SEVEN_DAYS, text, placeholders));
        inventory.setItem(SLOT_DISCOUNT_SAVE, button(Material.LIME_STAINED_GLASS_PANE, text.gui("editor.discount-menu.save-name", placeholders),
                text.guiList("editor.discount-menu.save-lore", placeholders)));
        inventory.setItem(SLOT_BACK, button(Material.ARROW, text.gui("common.back-name", placeholders), text.guiList("common.back-lore", placeholders)));
        inventory.setItem(SLOT_DISCOUNT_CLEAR, button(Material.BARRIER, text.gui("editor.discount-menu.clear-name", placeholders),
                text.guiList("editor.discount-menu.clear-lore", placeholders)));
    }

    public static void renderHistory(Inventory inventory, ShopEditorHolder holder, ShopText text, ZyroxDiamondPaymentService payments) {
        inventory.clear();
        Map<String, String> placeholders = new HashMap<>(text.placeholders(holder.shop()));
        placeholders.put("page", Integer.toString(holder.historyPage()));
        inventory.setItem(4, button(Material.WRITABLE_BOOK, text.gui("editor.history.title-name", placeholders),
                text.guiList("editor.history.title-lore", placeholders)));
        List<ShopTransactionRecord> history = holder.history();
        if (history.isEmpty()) {
            inventory.setItem(13, button(Material.GRAY_STAINED_GLASS_PANE, text.gui("editor.history.empty-name", placeholders),
                    text.guiList("editor.history.empty-lore", placeholders)));
        }
        for (int index = 0; index < history.size() && index < LIST_ENTRY_SLOTS.length; index++) {
            ShopTransactionRecord transaction = history.get(index);
            Map<String, String> entryPlaceholders = new HashMap<>(placeholders);
            entryPlaceholders.put("buyer", transaction.actorName() == null ? "-" : transaction.actorName());
            entryPlaceholders.put("amount", Long.toString(transaction.itemAmount()));
            entryPlaceholders.put("price", payments.formatDiamonds(transaction.diamondAmount()));
            entryPlaceholders.put("created_at", transaction.createdAt());
            inventory.setItem(LIST_ENTRY_SLOTS[index], button(Material.PAPER, text.gui("editor.history.entry-name", entryPlaceholders),
                    text.guiList("editor.history.entry-lore", entryPlaceholders)));
        }
        inventory.setItem(SLOT_PREVIOUS_PAGE, button(Material.ARROW, text.gui("common.previous-page-name", placeholders),
                text.guiList("common.previous-page-lore", placeholders)));
        inventory.setItem(SLOT_BACK, button(Material.ARROW, text.gui("common.back-name", placeholders), text.guiList("common.back-lore", placeholders)));
        inventory.setItem(SLOT_NEXT_PAGE, button(Material.ARROW, text.gui("common.next-page-name", placeholders),
                text.guiList("common.next-page-lore", placeholders)));
    }

    public static Inventory purchase(ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text) {
        return purchase(shop, payments, text, false);
    }

    public static Inventory purchase(ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text, boolean favorite) {
        ShopPurchaseHolder holder = new ShopPurchaseHolder(shop);
        Inventory inventory = Bukkit.createInventory(holder, 27, text.gui("purchase.title", text.placeholders(shop)));
        renderPurchase(inventory, shop, payments, text, favorite);
        return inventory;
    }

    public static void renderPurchase(Inventory inventory, ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text) {
        renderPurchase(inventory, shop, payments, text, false);
    }

    public static void renderPurchase(Inventory inventory, ShopRecord shop, ZyroxDiamondPaymentService payments, ShopText text, boolean favorite) {
        inventory.clear();
        Map<String, String> placeholders = text.placeholders(shop);
        placeholders.put("favorite", favorite ? "Ja" : "Nein");
        inventory.setItem(SLOT_PURCHASE_ITEM, named(shop.itemTemplate(), shop.bundleAmount() + "x " + ItemStacks.displayName(shop.itemTemplate()),
                text.guiList("purchase.item-lore", placeholders)));
        for (int index = 0; index < PURCHASE_SLOTS.length; index++) {
            long bundles = PURCHASE_BUNDLES[index] == -1 ? shop.maxBundles() : PURCHASE_BUNDLES[index];
            long items = safeMultiply(shop.bundleAmount(), bundles);
            long price = safeMultiply(shop.effectivePriceDiamonds(java.time.Clock.systemUTC()), bundles);
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
        inventory.setItem(SLOT_PURCHASE_FAVORITE, button(favorite ? Material.NETHER_STAR : Material.GRAY_DYE,
                text.gui(favorite ? "purchase.favorite-remove-name" : "purchase.favorite-add-name", placeholders),
                text.guiList("purchase.favorite-lore", placeholders)));
        inventory.setItem(SLOT_PURCHASE_RATING, button(Material.AMETHYST_SHARD, text.gui("purchase.rating-name", placeholders),
                text.guiList("purchase.rating-lore", placeholders)));
        inventory.setItem(SLOT_PURCHASE_FAVORITES, button(Material.BOOK, text.gui("purchase.favorites-name", placeholders),
                text.guiList("purchase.favorites-lore", placeholders)));
    }

    public static void renderRating(Inventory inventory, ShopRecord shop, ShopText text, boolean favorite) {
        inventory.clear();
        Map<String, String> placeholders = text.placeholders(shop);
        placeholders.put("favorite", favorite ? "Ja" : "Nein");
        inventory.setItem(SLOT_PURCHASE_ITEM, named(shop.itemTemplate(), shop.bundleAmount() + "x " + ItemStacks.displayName(shop.itemTemplate()),
                text.guiList("rating.item-lore", placeholders)));
        for (int index = 0; index < RATING_SLOTS.length; index++) {
            Map<String, String> ratingPlaceholders = new HashMap<>(placeholders);
            ratingPlaceholders.put("rating_value", Integer.toString(index + 1));
            inventory.setItem(RATING_SLOTS[index], button(Material.AMETHYST_SHARD,
                    text.gui("rating.rate-name", ratingPlaceholders), text.guiList("rating.rate-lore", ratingPlaceholders)));
        }
        inventory.setItem(SLOT_BACK, button(Material.ARROW, text.gui("common.back-name", placeholders), text.guiList("common.back-lore", placeholders)));
    }

    public static void renderFavorites(
            Inventory inventory,
            ShopPurchaseHolder holder,
            List<ShopRecord> favorites,
            ZyroxDiamondPaymentService payments,
            ShopText text
    ) {
        inventory.clear();
        Map<String, String> placeholders = new HashMap<>(text.placeholders(holder.shop()));
        placeholders.put("page", Integer.toString(holder.favoritesPage()));
        holder.favoriteSlots(favorites, LIST_ENTRY_SLOTS);
        inventory.setItem(4, button(Material.BOOK, text.gui("favorites.title-name", placeholders),
                text.guiList("favorites.title-lore", placeholders)));
        if (favorites.isEmpty()) {
            inventory.setItem(13, button(Material.GRAY_STAINED_GLASS_PANE, text.gui("favorites.empty-name", placeholders),
                    text.guiList("favorites.empty-lore", placeholders)));
        }
        for (int index = 0; index < favorites.size() && index < LIST_ENTRY_SLOTS.length; index++) {
            ShopRecord favorite = favorites.get(index);
            Map<String, String> favoritePlaceholders = new HashMap<>(text.placeholders(favorite));
            favoritePlaceholders.put("page", Integer.toString(holder.favoritesPage()));
            favoritePlaceholders.put("price", payments.formatDiamonds(favorite.effectivePriceDiamonds(java.time.Clock.systemUTC())));
            inventory.setItem(LIST_ENTRY_SLOTS[index], named(favorite.itemTemplate(),
                    text.gui("favorites.entry-name", favoritePlaceholders), text.guiList("favorites.entry-lore", favoritePlaceholders)));
        }
        inventory.setItem(SLOT_PREVIOUS_PAGE, button(Material.ARROW, text.gui("common.previous-page-name", placeholders),
                text.guiList("common.previous-page-lore", placeholders)));
        inventory.setItem(SLOT_BACK, button(Material.ARROW, text.gui("common.back-name", placeholders), text.guiList("common.back-lore", placeholders)));
        inventory.setItem(SLOT_NEXT_PAGE, button(Material.ARROW, text.gui("common.next-page-name", placeholders),
                text.guiList("common.next-page-lore", placeholders)));
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

    private static ItemStack durationButton(
            ShopEditorHolder holder,
            ShopEditorHolder.DiscountDuration duration,
            ShopText text,
            Map<String, String> placeholders
    ) {
        Map<String, String> durationPlaceholders = new HashMap<>(placeholders);
        durationPlaceholders.put("duration", durationLabel(duration, text));
        durationPlaceholders.put("selected", holder.discountDuration() == duration ? "Ja" : "Nein");
        Material material = holder.discountDuration() == duration ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        return button(material, text.gui("editor.discount-menu.duration-name", durationPlaceholders),
                text.guiList("editor.discount-menu.duration-lore", durationPlaceholders));
    }

    private static String durationLabel(ShopEditorHolder.DiscountDuration duration, ShopText text) {
        return switch (duration) {
            case PERMANENT -> text.gui("editor.discount-menu.duration-permanent", Map.of());
            case THIRTY_MINUTES -> text.gui("editor.discount-menu.duration-30m", Map.of());
            case TWO_HOURS -> text.gui("editor.discount-menu.duration-2h", Map.of());
            case SEVEN_DAYS -> text.gui("editor.discount-menu.duration-7d", Map.of());
        };
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
