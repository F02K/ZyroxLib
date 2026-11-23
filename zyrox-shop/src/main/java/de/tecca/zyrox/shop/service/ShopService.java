package de.tecca.zyrox.shop.service;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.hologram.ShopHologramService;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEventType;
import de.tecca.zyrox.shop.market.ShopMarketService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopStorage;
import de.tecca.zyrox.shop.storage.ShopTransactionRecord;
import de.tecca.zyrox.shop.text.ShopText;
import de.tecca.zyrox.shop.util.ItemStacks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopService {

    private final JavaPlugin plugin;
    private final ShopStorage storage;
    private final ZyroxDiamondPaymentService payments;
    private final ShopHologramService holograms;
    private final ShopText text;
    private final ShopMarketService market;
    private final Clock clock;
    private final Map<Long, Object> shopLocks = new ConcurrentHashMap<>();

    public ShopService(
            JavaPlugin plugin,
            ShopStorage storage,
            ZyroxDiamondPaymentService payments,
            ShopHologramService holograms,
            ShopText text
    ) {
        this(plugin, storage, payments, holograms, text, null, Clock.systemUTC());
    }

    public ShopService(
            JavaPlugin plugin,
            ShopStorage storage,
            ZyroxDiamondPaymentService payments,
            ShopHologramService holograms,
            ShopText text,
            ShopMarketService market
    ) {
        this(plugin, storage, payments, holograms, text, market, Clock.systemUTC());
    }

    public ShopService(
            JavaPlugin plugin,
            ShopStorage storage,
            ZyroxDiamondPaymentService payments,
            ShopHologramService holograms,
            ShopText text,
            Clock clock
    ) {
        this(plugin, storage, payments, holograms, text, null, clock);
    }

    public ShopService(
            JavaPlugin plugin,
            ShopStorage storage,
            ZyroxDiamondPaymentService payments,
            ShopHologramService holograms,
            ShopText text,
            ShopMarketService market,
            Clock clock
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.payments = payments;
        this.holograms = holograms;
        this.text = text;
        this.market = market;
        this.clock = clock;
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

    public List<ShopTransactionRecord> purchaseHistory(ShopRecord shop, int page, int pageSize) {
        try {
            return storage.purchaseHistory(shop.id(), Math.max(1, pageSize), Math.max(0, page - 1) * Math.max(1, pageSize));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load shop purchase history: " + exception.getMessage());
            return List.of();
        }
    }

    public List<ShopRecord> favorites(Player player, int page, int pageSize) {
        try {
            return storage.favorites(player.getUniqueId(), Math.max(1, pageSize), Math.max(0, page - 1) * Math.max(1, pageSize));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load favorite shops: " + exception.getMessage());
            return List.of();
        }
    }

    public boolean isFavorite(ShopRecord shop, UUID playerId) {
        try {
            return storage.isFavorite(shop.id(), playerId);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not check favorite shop: " + exception.getMessage());
            return false;
        }
    }

    public ShopActionResult toggleFavorite(ShopRecord shop, Player player) {
        try {
            boolean favorite = storage.toggleFavorite(shop.id(), player);
            return ShopActionResult.success(message(favorite ? "favorite-added" : "favorite-removed", shop), shop);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not toggle favorite shop: " + exception.getMessage());
            return ShopActionResult.failure(message("storage-failed", shop), shop);
        }
    }

    public ShopActionResult rateShop(ShopRecord shop, Player player, int rating) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            if (current.isOwner(player.getUniqueId())) {
                return ShopActionResult.failure(message("rating-own-shop", current), current);
            }
            try {
                if (!storage.hasPurchased(current.id(), player.getUniqueId())) {
                    return ShopActionResult.failure(message("rating-no-purchase", current), current);
                }
                ShopRecord updated = storage.rateShop(current.id(), player, rating);
                holograms.upsert(updated);
                publishMarketEvent(ZyroxMarketShopEventType.UPDATED, updated);
                return ShopActionResult.success(message("rating-success", updated), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not rate shop: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
    }

    public ShopActionResult setDiscount(ShopRecord shop, int percent, Duration duration) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            try {
                String expiresAt = duration == null ? null : Instant.now(clock).plus(duration).toString();
                ShopRecord updated = storage.setDiscount(current.id(), percent, expiresAt);
                holograms.upsert(updated);
                publishMarketEvent(ZyroxMarketShopEventType.UPDATED, updated);
                return ShopActionResult.success(message("discount-set-success", updated), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not set shop discount: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
    }

    public ShopActionResult clearDiscount(ShopRecord shop) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            try {
                ShopRecord updated = storage.clearDiscount(current.id());
                holograms.upsert(updated);
                publishMarketEvent(ZyroxMarketShopEventType.UPDATED, updated);
                return ShopActionResult.success(message("discount-clear-success", updated), updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not clear shop discount: " + exception.getMessage());
                return ShopActionResult.failure(message("storage-failed", current), current);
            }
        }
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
            publishMarketEvent(ZyroxMarketShopEventType.CREATED, shop);
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
                publishMarketEvent(ZyroxMarketShopEventType.UPDATED, updated);
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
                publishMarketEvent(ZyroxMarketShopEventType.UPDATED, updated);
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
                if (current.stockAmount() <= 0 && updated.stockAmount() > 0) {
                    publishMarketEvent(ZyroxMarketShopEventType.RESTOCKED, updated);
                }
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
        return purchase(shop, buyer, bundles, shop.effectivePriceDiamonds(clock));
    }

    public ShopActionResult purchase(ShopRecord shop, Player buyer, long bundles, long expectedEffectivePriceDiamonds) {
        synchronized (lockFor(shop.id())) {
            ShopRecord current = currentShop(shop).orElse(shop);
            if (current.isOwner(buyer.getUniqueId())) {
                return ShopActionResult.failure(message("own-shop-buy", current), current);
            }
            if (bundles <= 0) {
                return ShopActionResult.failure(message("no-bundles", current), current);
            }
            if (current.bundleAmount() != shop.bundleAmount()
                    || current.priceDiamonds() != shop.priceDiamonds()
                    || current.discountPercent() != shop.discountPercent()
                    || !equalNullable(current.discountExpiresAt(), shop.discountExpiresAt())
                    || current.effectivePriceDiamonds(clock) != expectedEffectivePriceDiamonds) {
                return ShopActionResult.failure(message("shop-updated", current), current);
            }
            long items;
            long pricePerBundle = expectedEffectivePriceDiamonds;
            long price;
            try {
                items = Math.multiplyExact(current.bundleAmount(), bundles);
                price = Math.multiplyExact(pricePerBundle, bundles);
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
                    () -> storage.recordPurchase(
                            current.id(),
                            buyer,
                            bundles,
                            current.bundleAmount(),
                            current.priceDiamonds(),
                            current.discountPercent(),
                            current.discountExpiresAt(),
                            pricePerBundle
                    )
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
                notifyOwnerAboutPendingSales(updated.ownerUuid());
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

    public void notifyOwnerAboutPendingSales(Player owner) {
        if (owner == null) {
            return;
        }
        notifyOwnerAboutPendingSales(owner.getUniqueId());
    }

    private void notifyOwnerAboutPendingSales(UUID ownerId) {
        if (ownerId == null || plugin.getServer() == null) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        try {
            List<ShopTransactionRecord> transactions = storage.pendingPurchaseNotifications(ownerId);
            if (transactions.isEmpty()) {
                return;
            }
            long itemAmount = transactions.stream().mapToLong(ShopTransactionRecord::itemAmount).sum();
            long diamondAmount = transactions.stream().mapToLong(ShopTransactionRecord::diamondAmount).sum();
            owner.sendMessage(message("sale-notification", Map.of(
                    "sales", Integer.toString(transactions.size()),
                    "items", Long.toString(itemAmount),
                    "revenue", payments.formatDiamonds(diamondAmount)
            )));
            storage.markOwnerNotified(transactions.stream().map(ShopTransactionRecord::id).toList());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not notify shop owner about sales: " + exception.getMessage());
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

    public void performEmptyStockMaintenance() {
        try {
            int initialized = storage.initializeMissingEmptyStockDeadlines();
            if (initialized > 0) {
                plugin.getLogger().info("Initialized auto-close deadlines for " + initialized + " empty shop(s).");
            }
            for (ShopRecord shop : storage.expiredEmptyStockShops()) {
                synchronized (lockFor(shop.id())) {
                    ShopRecord current = currentShop(shop).orElse(null);
                    if (current == null || current.stockAmount() > 0 || current.emptyStockExpiresAt() == null) {
                        continue;
                    }
                    boolean deleted = storage.deleteShop(current.id(), null, true);
                    if (deleted) {
                        holograms.remove(current.id());
                        plugin.getLogger().info("Auto-closed empty shop #" + current.id()
                                + " owned by " + current.ownerName() + ".");
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not run empty shop maintenance: " + exception.getMessage());
        }
    }

    public void warnOnlineOwnersAboutEmptyShops() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            warnOwnerAboutEmptyShops(player);
        }
    }

    public void warnOwnerAboutEmptyShops(Player player) {
        try {
            for (ShopRecord shop : storage.allShops()) {
                if (!shop.isOwner(player.getUniqueId()) || shop.stockAmount() > 0 || shop.emptyStockExpiresAt() == null) {
                    continue;
                }
                if (!shouldWarn(shop)) {
                    continue;
                }
                player.sendMessage(message("empty-stock-warning", shop));
                playWarningSound(player);
                storage.markEmptyStockWarningSent(shop.id());
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not send empty shop warnings: " + exception.getMessage());
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

    private String message(String path, Map<String, String> placeholders) {
        return text.message(path, placeholders);
    }

    private String message(String path, ShopRecord shop) {
        return message(path, shop, Map.of());
    }

    private String message(String path, ShopRecord shop, Map<String, String> extra) {
        Map<String, String> placeholders = new HashMap<>(text.placeholders(shop));
        placeholders.putAll(extra);
        return text.message(path, placeholders);
    }

    private boolean shouldWarn(ShopRecord shop) {
        if (shop.lastEmptyStockWarningAt() == null || shop.lastEmptyStockWarningAt().isBlank()) {
            return true;
        }
        try {
            Instant lastWarning = Instant.parse(shop.lastEmptyStockWarningAt());
            Duration interval = Duration.ofHours(Math.max(1, plugin.getConfig().getLong("shop.empty-stock-warning-interval-hours", 24)));
            return !Instant.now(clock).minus(interval).isBefore(lastWarning);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private void playWarningSound(Player player) {
        String configured = plugin.getConfig().getString("shop.empty-stock-warning-sound", "BLOCK_NOTE_BLOCK_PLING");
        try {
            Sound sound = Sound.valueOf(configured == null ? "BLOCK_NOTE_BLOCK_PLING" : configured);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid empty-stock warning sound: " + configured);
        }
    }

    private void publishMarketEvent(ZyroxMarketShopEventType type, ShopRecord shop) {
        if (market != null) {
            market.publish(type, shop);
        }
    }

    private boolean equalNullable(String left, String right) {
        String normalizedLeft = left == null ? "" : left;
        String normalizedRight = right == null ? "" : right;
        return normalizedLeft.equals(normalizedRight);
    }
}
