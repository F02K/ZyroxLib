package de.tecca.zyrox.shop.listener;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.gui.ShopEditorHolder;
import de.tecca.zyrox.shop.gui.ShopGui;
import de.tecca.zyrox.shop.gui.ShopPurchaseHolder;
import de.tecca.zyrox.shop.plugin.ZyroxShopPlugin;
import de.tecca.zyrox.shop.service.ShopActionResult;
import de.tecca.zyrox.shop.service.ShopService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopListener implements Listener {

    private final ZyroxShopPlugin plugin;
    private final ShopService shops;
    private final ZyroxDiamondPaymentService payments;
    private final Map<String, Long> clickCooldowns = new ConcurrentHashMap<>();

    public ShopListener(ZyroxShopPlugin plugin, ShopService shops, ZyroxDiamondPaymentService payments) {
        this.plugin = plugin;
        this.shops = shops;
        this.payments = payments;
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        long delay = Math.max(1L, plugin.getConfig().getLong("shop.empty-stock-login-warning-delay-ticks", 60L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                shops.warnOwnerAboutEmptyShops(event.getPlayer());
                shops.notifyOwnerAboutPendingSales(event.getPlayer());
            }
        }, delay);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (!shops.isChest(block)) {
            return;
        }

        Player player = event.getPlayer();
        Optional<ShopRecord> shop = shops.findShop(block.getLocation());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
            event.setCancelled(true);
            if (shop.isPresent()) {
                openEditor(player, shop.get());
                return;
            }
            if (!player.hasPermission(plugin.createPermission())) {
                player.sendMessage(plugin.text().message("no-create-permission", Map.of()));
                return;
            }
            ShopActionResult created = shops.createShop(block, player, player.getInventory().getItemInMainHand());
            player.sendMessage(created.message());
            if (created.success()) {
                openEditor(player, created.shop());
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && shop.isPresent()) {
            event.setCancelled(true);
            if (!player.hasPermission(plugin.usePermission())) {
                player.sendMessage(plugin.text().message("no-use-permission", Map.of()));
                return;
            }
            if (shop.get().isOwner(player.getUniqueId())) {
                player.sendMessage(plugin.text().message("own-shop-buy", plugin.text().placeholders(shop.get())));
                return;
            }
            player.openInventory(ShopGui.purchase(shop.get(), payments, plugin.text(), shops.isFavorite(shop.get(), player.getUniqueId())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!shops.isChest(block)) {
            return;
        }
        Optional<ShopRecord> shop = shops.findShop(block.getLocation());
        if (shop.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        boolean admin = player.hasPermission(plugin.adminPermission());
        boolean owner = shop.get().isOwner(player.getUniqueId());
        event.setCancelled(true);
        if (!admin && !owner) {
            player.sendMessage(plugin.text().message("only-owner-break", plugin.text().placeholders(shop.get())));
            return;
        }

        player.sendMessage(plugin.text().message("shop-break-protected", plugin.text().placeholders(shop.get())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ShopEditorHolder holder) {
            event.setCancelled(true);
            handleEditorClick(player, event.getRawSlot(), event.getInventory(), holder);
            return;
        }
        if (event.getInventory().getHolder() instanceof ShopPurchaseHolder holder) {
            event.setCancelled(true);
            handlePurchaseClick(player, event.getRawSlot(), event.getInventory(), holder);
        }
    }

    private void openEditor(Player player, ShopRecord shop) {
        if (!shop.isOwner(player.getUniqueId()) && !player.hasPermission(plugin.adminPermission())) {
            player.sendMessage(plugin.text().message("only-owner-edit", plugin.text().placeholders(shop)));
            return;
        }
        player.openInventory(ShopGui.editor(shop, payments, plugin.text()));
    }

    private void handleEditorClick(Player player, int rawSlot, Inventory inventory, ShopEditorHolder holder) {
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            return;
        }
        ShopRecord shop = holder.shop();
        if (!shop.isOwner(player.getUniqueId()) && !player.hasPermission(plugin.adminPermission())) {
            player.closeInventory();
            player.sendMessage(plugin.text().message("only-owner-edit", plugin.text().placeholders(shop)));
            return;
        }
        if (holder.view() == ShopEditorHolder.View.DISCOUNT) {
            handleDiscountClick(player, rawSlot, inventory, holder);
            return;
        }
        if (holder.view() == ShopEditorHolder.View.HISTORY) {
            handleHistoryClick(rawSlot, inventory, holder);
            return;
        }
        if (isCoolingDown(player.getUniqueId(), shop.id(), "editor")) {
            player.sendMessage(plugin.text().message("action-busy", plugin.text().placeholders(shop)));
            return;
        }

        ShopActionResult result = null;
        if (rawSlot == ShopGui.SLOT_TEMPLATE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            result = shops.updateTemplate(shop, hand);
        } else if (rawSlot == ShopGui.SLOT_PRICE_DOWN) {
            result = shops.updateBundle(shop, shop.bundleAmount(), Math.max(1, shop.priceDiamonds() - 1));
        } else if (rawSlot == ShopGui.SLOT_PRICE_UP) {
            result = shops.updateBundle(shop, shop.bundleAmount(), shop.priceDiamonds() + 1);
        } else if (rawSlot == ShopGui.SLOT_BUNDLE_DOWN) {
            result = shops.updateBundle(shop, Math.max(1, shop.bundleAmount() - 1), shop.priceDiamonds());
        } else if (rawSlot == ShopGui.SLOT_BUNDLE_UP) {
            result = shops.updateBundle(shop, shop.bundleAmount() + 1, shop.priceDiamonds());
        } else if (rawSlot == ShopGui.SLOT_DEPOSIT) {
            result = shops.depositAllMatchingStock(shop, player);
        } else if (rawSlot == ShopGui.SLOT_WITHDRAW_BUNDLE) {
            result = shops.withdrawStock(shop, player, shop.bundleAmount());
        } else if (rawSlot == ShopGui.SLOT_WITHDRAW_ALL) {
            result = shops.withdrawStock(shop, player, shop.stockAmount());
        } else if (rawSlot == ShopGui.SLOT_REVENUE) {
            result = shops.withdrawRevenue(shop, player);
        } else if (rawSlot == ShopGui.SLOT_DISCOUNT) {
            holder.view(ShopEditorHolder.View.DISCOUNT);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        } else if (rawSlot == ShopGui.SLOT_HISTORY) {
            holder.view(ShopEditorHolder.View.HISTORY);
            holder.historyPage(1);
            renderHistory(inventory, holder);
            return;
        } else if (rawSlot == ShopGui.SLOT_DELETE) {
            result = shops.deleteShop(shop, player, player.hasPermission(plugin.adminPermission()));
            player.sendMessage(result.message());
            if (result.success()) {
                player.closeInventory();
            }
            return;
        }

        if (result != null) {
            player.sendMessage(result.message());
            if (result.shop() != null) {
                holder.update(result.shop());
                ShopGui.renderEditor(inventory, result.shop(), payments, plugin.text());
            }
        }
    }

    private void handleDiscountClick(Player player, int rawSlot, Inventory inventory, ShopEditorHolder holder) {
        if (rawSlot == ShopGui.SLOT_BACK) {
            holder.view(ShopEditorHolder.View.EDITOR);
            ShopGui.renderEditor(inventory, holder.shop(), payments, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_DISCOUNT_PERCENT_DOWN) {
            holder.discountPercent(holder.discountPercent() - 5);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_DISCOUNT_PERCENT_UP) {
            holder.discountPercent(holder.discountPercent() + 5);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_DISCOUNT_DURATION_PERMANENT) {
            holder.discountDuration(ShopEditorHolder.DiscountDuration.PERMANENT);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_DISCOUNT_DURATION_30M) {
            holder.discountDuration(ShopEditorHolder.DiscountDuration.THIRTY_MINUTES);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_DISCOUNT_DURATION_2H) {
            holder.discountDuration(ShopEditorHolder.DiscountDuration.TWO_HOURS);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_DISCOUNT_DURATION_7D) {
            holder.discountDuration(ShopEditorHolder.DiscountDuration.SEVEN_DAYS);
            ShopGui.renderDiscount(inventory, holder, plugin.text());
            return;
        }
        if (rawSlot != ShopGui.SLOT_DISCOUNT_SAVE && rawSlot != ShopGui.SLOT_DISCOUNT_CLEAR) {
            return;
        }
        if (isCoolingDown(player.getUniqueId(), holder.shop().id(), "discount")) {
            player.sendMessage(plugin.text().message("action-busy", plugin.text().placeholders(holder.shop())));
            return;
        }
        ShopActionResult result = rawSlot == ShopGui.SLOT_DISCOUNT_CLEAR
                ? shops.clearDiscount(holder.shop())
                : shops.setDiscount(holder.shop(), holder.discountPercent(), holder.discountDuration().duration());
        player.sendMessage(result.message());
        if (result.shop() != null) {
            holder.update(result.shop());
        }
        ShopGui.renderDiscount(inventory, holder, plugin.text());
    }

    private void handleHistoryClick(int rawSlot, Inventory inventory, ShopEditorHolder holder) {
        if (rawSlot == ShopGui.SLOT_BACK) {
            holder.view(ShopEditorHolder.View.EDITOR);
            ShopGui.renderEditor(inventory, holder.shop(), payments, plugin.text());
            return;
        }
        if (rawSlot == ShopGui.SLOT_PREVIOUS_PAGE) {
            holder.historyPage(holder.historyPage() - 1);
            renderHistory(inventory, holder);
            return;
        }
        if (rawSlot == ShopGui.SLOT_NEXT_PAGE) {
            holder.historyPage(holder.historyPage() + 1);
            renderHistory(inventory, holder);
        }
    }

    private void renderHistory(Inventory inventory, ShopEditorHolder holder) {
        holder.history(shops.purchaseHistory(holder.shop(), holder.historyPage(), ShopGui.LIST_ENTRY_SLOTS.length));
        ShopGui.renderHistory(inventory, holder, plugin.text(), payments);
    }

    private void handlePurchaseClick(Player player, int rawSlot, Inventory inventory, ShopPurchaseHolder holder) {
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            return;
        }
        if (holder.view() == ShopPurchaseHolder.View.RATING) {
            handleRatingClick(player, rawSlot, inventory, holder);
            return;
        }
        if (holder.view() == ShopPurchaseHolder.View.FAVORITES) {
            handleFavoritesClick(player, rawSlot, inventory, holder);
            return;
        }
        if (rawSlot == ShopGui.SLOT_PURCHASE_FAVORITE) {
            if (isCoolingDown(player.getUniqueId(), holder.shop().id(), "favorite")) {
                player.sendMessage(plugin.text().message("action-busy", plugin.text().placeholders(holder.shop())));
                return;
            }
            ShopActionResult result = shops.toggleFavorite(holder.shop(), player);
            player.sendMessage(result.message());
            if (result.shop() != null) {
                holder.update(result.shop());
            }
            renderPurchase(inventory, holder, player);
            return;
        }
        if (rawSlot == ShopGui.SLOT_PURCHASE_RATING) {
            holder.view(ShopPurchaseHolder.View.RATING);
            ShopGui.renderRating(inventory, holder.shop(), plugin.text(), shops.isFavorite(holder.shop(), player.getUniqueId()));
            return;
        }
        if (rawSlot == ShopGui.SLOT_PURCHASE_FAVORITES) {
            holder.view(ShopPurchaseHolder.View.FAVORITES);
            holder.favoritesPage(1);
            renderFavorites(inventory, holder, player);
            return;
        }
        long bundles = 0;
        for (int index = 0; index < ShopGui.PURCHASE_SLOTS.length; index++) {
            if (ShopGui.PURCHASE_SLOTS[index] == rawSlot) {
                bundles = ShopGui.PURCHASE_BUNDLES[index] == -1 ? holder.shop().maxBundles() : ShopGui.PURCHASE_BUNDLES[index];
                break;
            }
        }
        if (bundles <= 0) {
            return;
        }
        if (isCoolingDown(player.getUniqueId(), holder.shop().id(), "purchase")) {
            player.sendMessage(plugin.text().message("action-busy", plugin.text().placeholders(holder.shop())));
            return;
        }
        ShopActionResult result = shops.purchase(holder.shop(), player, bundles, holder.expectedEffectivePriceDiamonds());
        player.sendMessage(result.message());
        if (result.shop() != null) {
            holder.update(result.shop());
            renderPurchase(inventory, holder, player);
        }
    }

    private void handleRatingClick(Player player, int rawSlot, Inventory inventory, ShopPurchaseHolder holder) {
        if (rawSlot == ShopGui.SLOT_BACK) {
            holder.view(ShopPurchaseHolder.View.PURCHASE);
            renderPurchase(inventory, holder, player);
            return;
        }
        int rating = 0;
        for (int index = 0; index < ShopGui.RATING_SLOTS.length; index++) {
            if (ShopGui.RATING_SLOTS[index] == rawSlot) {
                rating = index + 1;
                break;
            }
        }
        if (rating <= 0) {
            return;
        }
        if (isCoolingDown(player.getUniqueId(), holder.shop().id(), "rating")) {
            player.sendMessage(plugin.text().message("action-busy", plugin.text().placeholders(holder.shop())));
            return;
        }
        ShopActionResult result = shops.rateShop(holder.shop(), player, rating);
        player.sendMessage(result.message());
        if (result.shop() != null) {
            holder.update(result.shop());
        }
        ShopGui.renderRating(inventory, holder.shop(), plugin.text(), shops.isFavorite(holder.shop(), player.getUniqueId()));
    }

    private void handleFavoritesClick(Player player, int rawSlot, Inventory inventory, ShopPurchaseHolder holder) {
        if (rawSlot == ShopGui.SLOT_BACK) {
            holder.view(ShopPurchaseHolder.View.PURCHASE);
            renderPurchase(inventory, holder, player);
            return;
        }
        if (rawSlot == ShopGui.SLOT_PREVIOUS_PAGE) {
            holder.favoritesPage(holder.favoritesPage() - 1);
            renderFavorites(inventory, holder, player);
            return;
        }
        if (rawSlot == ShopGui.SLOT_NEXT_PAGE) {
            holder.favoritesPage(holder.favoritesPage() + 1);
            renderFavorites(inventory, holder, player);
            return;
        }
        ShopRecord favorite = holder.favoriteAt(rawSlot);
        if (favorite == null) {
            return;
        }
        holder.update(favorite);
        holder.view(ShopPurchaseHolder.View.PURCHASE);
        renderPurchase(inventory, holder, player);
    }

    private void renderPurchase(Inventory inventory, ShopPurchaseHolder holder, Player player) {
        ShopGui.renderPurchase(inventory, holder.shop(), payments, plugin.text(), shops.isFavorite(holder.shop(), player.getUniqueId()));
    }

    private void renderFavorites(Inventory inventory, ShopPurchaseHolder holder, Player player) {
        List<ShopRecord> favorites = shops.favorites(player, holder.favoritesPage(), ShopGui.LIST_ENTRY_SLOTS.length);
        ShopGui.renderFavorites(inventory, holder, favorites, payments, plugin.text());
    }

    private boolean isCoolingDown(UUID playerId, long shopId, String action) {
        String key = playerId + ":" + shopId + ":" + action;
        long now = System.currentTimeMillis();
        Long previous = clickCooldowns.put(key, now);
        return previous != null && now - previous < 250L;
    }
}
