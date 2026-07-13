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
            player.openInventory(ShopGui.purchase(shop.get(), payments, plugin.text()));
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

    private void handlePurchaseClick(Player player, int rawSlot, Inventory inventory, ShopPurchaseHolder holder) {
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
        ShopActionResult result = shops.purchase(holder.shop(), player, bundles);
        player.sendMessage(result.message());
        if (result.shop() != null) {
            holder.update(result.shop());
            ShopGui.renderPurchase(inventory, result.shop(), payments, plugin.text());
        }
    }

    private boolean isCoolingDown(UUID playerId, long shopId, String action) {
        String key = playerId + ":" + shopId + ":" + action;
        long now = System.currentTimeMillis();
        Long previous = clickCooldowns.put(key, now);
        return previous != null && now - previous < 250L;
    }
}
