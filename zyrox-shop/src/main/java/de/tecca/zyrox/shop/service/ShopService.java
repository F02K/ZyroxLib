package de.tecca.zyrox.shop.service;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.hologram.ShopHologramService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopStorage;
import de.tecca.zyrox.shop.text.ShopText;
import de.tecca.zyrox.shop.util.ItemStacks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopService {

    private final JavaPlugin plugin;
    private final ShopStorage storage;
    private final ZyroxDiamondPaymentService payments;
    private final ShopHologramService holograms;
    private final ShopText text;
    private final Map<Long, Object> shopLocks = new ConcurrentHashMap<>();

    public ShopService(
            JavaPlugin plugin,
            ShopStorage storage,
            ZyroxDiamondPaymentService payments,
            ShopHologramService holograms,
            ShopText text
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.payments = payments;
        this.holograms = holograms;
        this.text = text;
    }

    public Optional<ShopRecord> findShop(Location location) {
        try {
            return storage.findByLocation(location);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not find shop: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public List<ShopRecord> allShops() throws SQLException {
        return storage.allShops();
    }

    public ShopActionResult createShop(Block block, Player owner, ItemStack handItem) {
        if (!(block.getState() instanceof Container container)) {
            return ShopActionResult.failure(message("create-not-chest"), null);
        }
        if (!container.getInventory().isEmpty()) {
            return ShopActionResult.failure(message("create-not-empty"), null);
        }
        if (handItem == null || handItem.getType().isAir()) {
            return ShopActionResult.failure(message("create-hold-item"), null);
        }
        int bundleAmount = Math.max(1, handItem.getAmount());
        long price = Math.max(1, plugin.getConfig().getLong("shop.default-price-diamonds", 1));
        try {
            ShopRecord shop = storage.createShop(block.getLocation(), owner, ItemStacks.template(handItem), bundleAmount, price);
            holograms.upsert(shop);
            return ShopActionResult.success(message("create-success", shop), shop);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not create shop: " + exception.getMessage());
            return ShopActionResult.failure(message("create-failed"), null);
        }
    }

    public ShopActionResult updateTemplate(ShopRecord shop, ItemStack item) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            if (item == null || item.getType().isAir()) {
                return ShopActionResult.failure(message("update-template-empty-hand", current), current);
            }
            if (current.stockAmount() > 0) {
                return ShopActionResult.failure(message("update-template-stock", current), current);
            }
            try {
                ShopRecord updated = storage.updateTemplate(current.id(), ItemStacks.template(item));
                holograms.upsert(updated);
                return ShopActionResult.success(message("update-template-success", updated), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not update shop item: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
    }

    public ShopActionResult updateBundle(ShopRecord shop, int bundleAmount, long priceDiamonds) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            try {
                ShopRecord updated = storage.updateBundle(current.id(), Math.max(1, bundleAmount), Math.max(1, priceDiamonds));
                holograms.upsert(updated);
                return ShopActionResult.success(message("update-offer-success", updated), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not update shop offer: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
    }

    public ShopActionResult depositAllMatchingStock(ShopRecord shop, Player owner) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            long available = ItemStacks.countMatching(owner.getInventory(), current.itemTemplate());
            if (available <= 0) {
                return ShopActionResult.failure(message("no-matching-stock", current), current);
            }
            long removed = ItemStacks.removeMatching(owner.getInventory(), current.itemTemplate(), available);
            if (removed <= 0) {
                return ShopActionResult.failure(message("no-matching-stock", current), current);
            }
            try {
                ShopRecord updated = storage.addStock(current.id(), removed, owner);
                holograms.upsert(updated);
                return ShopActionResult.success(message("stock-deposit-success", updated, Map.of("amount", Long.toString(removed))), updated);
            } catch (SQLException exception) {
                ItemStacks.give(owner.getInventory(), current.itemTemplate(), removed);
                plugin.getLogger().warning("Could not deposit shop stock: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
    }

    public ShopActionResult withdrawStock(ShopRecord shop, Player owner, long amount) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            long target = amount;
            if (target <= 0 || current.stockAmount() <= 0) {
                return ShopActionResult.failure(message("stock-withdraw-empty", current), current);
            }
            if (current.stockAmount() < target) {
                return ShopActionResult.failure(message("stock-withdraw-failed", current), current);
            }
            if (ItemStacks.freeCapacity(owner.getInventory(), current.itemTemplate()) < target) {
                return ShopActionResult.failure(message("no-space-stock", current), current);
            }
            try {
                ShopRecord updated = storage.withdrawStock(current.id(), target, owner);
                if (!ItemStacks.give(owner.getInventory(), current.itemTemplate(), target)) {
                    storage.addStock(current.id(), target, owner);
                    return ShopActionResult.failure(message("no-space-stock", current), current);
                }
                holograms.upsert(updated);
                return ShopActionResult.success(message("stock-withdraw-success", updated, Map.of("amount", Long.toString(target))), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not withdraw shop stock: " + exception.getMessage());
                return ShopActionResult.failure(message("stock-withdraw-failed", current), current);
            }
        }
    }

    public ShopActionResult withdrawRevenue(ShopRecord shop, Player owner) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            long amount = current.revenueDiamonds();
            if (amount <= 0) {
                return ShopActionResult.failure(message("revenue-empty", current), current);
            }
            ItemStack currency = new ItemStack(payments.currencyMaterial());
            if (ItemStacks.freeCapacity(owner.getInventory(), currency) < amount) {
                return ShopActionResult.failure(message("no-space-buy", current), current);
            }
            try {
                ShopRecord updated = storage.withdrawRevenue(current.id(), amount, owner);
                ZyroxDiamondPaymentService.PaymentResult payout = payments.giveDiamonds(owner, amount);
                if (!payout.success()) {
                    storage.refundRevenue(current.id(), amount);
                    return ShopActionResult.failure(payout.message(), current);
                }
                holograms.upsert(updated);
                return ShopActionResult.success(message("revenue-withdraw-success", updated, Map.of("amount", payments.formatDiamonds(amount))), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not withdraw shop revenue: " + exception.getMessage());
                return ShopActionResult.failure(message("revenue-withdraw-failed", current), current);
            }
        }
    }

    public ShopActionResult purchase(ShopRecord shop, Player buyer, long bundles) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            if (current.isOwner(buyer.getUniqueId())) {
                return ShopActionResult.failure(message("own-shop-buy", current), current);
            }
            if (bundles <= 0) {
                return ShopActionResult.failure(message("no-bundles", current), current);
            }
            if (current.bundleAmount() != shop.bundleAmount() || current.priceDiamonds() != shop.priceDiamonds()) {
                return ShopActionResult.failure(message("shop-updated", current), current);
            }
            long items;
            long price;
            try {
                items = Math.multiplyExact(current.bundleAmount(), bundles);
                price = Math.multiplyExact(current.priceDiamonds(), bundles);
            } catch (ArithmeticException exception) {
                return ShopActionResult.failure(message("shop-updated", current), current);
            }
            if (current.stockAmount() < items) {
                return ShopActionResult.failure(message("no-stock", current), current);
            }
            if (ItemStacks.freeCapacity(buyer.getInventory(), current.itemTemplate()) < items) {
                return ShopActionResult.failure(message("no-space-buy", current), current);
            }
            if (!payments.hasDiamonds(buyer, price)) {
                return ShopActionResult.failure(message("no-diamonds", current, Map.of("price", payments.formatDiamonds(price))), current);
            }

            ZyroxDiamondPaymentService.PaymentResult payment = payments.withdrawDiamonds(
                    buyer,
                    price,
                    () -> storage.recordPurchase(current.id(), buyer, bundles, current.bundleAmount(), current.priceDiamonds())
            );
            if (!payment.success()) {
                ShopRecord refreshed = currentShop(current).orElse(current);
                return ShopActionResult.failure(message("shop-updated", refreshed), refreshed);
            }
            if (!ItemStacks.give(buyer.getInventory(), current.itemTemplate(), items)) {
                plugin.getLogger().severe("Buyer inventory rejected items after paid shop purchase: buyer="
                        + buyer.getName() + " shop=" + current.id() + " items=" + items);
                return ShopActionResult.failure(message("purchase-failed-paid", current), current);
            }
            try {
                ShopRecord updated = storage.findById(current.id()).orElse(current);
                holograms.upsert(updated);
                return ShopActionResult.success(message("purchase-success", updated, Map.of(
                        "amount", Long.toString(items),
                        "price", payments.formatDiamonds(price)
                )), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not reload shop after purchase: " + exception.getMessage());
                return ShopActionResult.success(message("purchase-success", current, Map.of(
                        "amount", Long.toString(items),
                        "price", payments.formatDiamonds(price)
                )), current);
            }
        }
    }

    public ShopActionResult deleteShop(ShopRecord shop, Player actor, boolean force) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            try {
                boolean deleted = storage.deleteShop(current.id(), actor, force);
                if (!deleted) {
                    return ShopActionResult.failure(message("delete-blocked", current), current);
                }
                holograms.remove(current.id());
                return ShopActionResult.success(message("delete-success", current), null);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not delete shop: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
    }

    public boolean isChest(Block block) {
        return block != null && (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST);
    }

    private Optional<ShopRecord> currentShop(ShopRecord shop) {
        try {
            return storage.findById(shop.id());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not reload shop: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private Object lockFor(long shopId) {
        return shopLocks.computeIfAbsent(shopId, ignored -> new Object());
    }

    private String message(String path) {
        return text.message(path, Map.of());
    }

    private String message(String path, ShopRecord shop) {
        return message(path, shop, Map.of());
    }

    private String message(String path, ShopRecord shop, Map<String, String> extra) {
        Map<String, String> placeholders = new HashMap<>(text.placeholders(shop));
        placeholders.putAll(extra);
        return text.message(path, placeholders);
    }
}
