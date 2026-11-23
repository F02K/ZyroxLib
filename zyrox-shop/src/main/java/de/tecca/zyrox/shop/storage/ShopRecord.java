package de.tecca.zyrox.shop.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record ShopRecord(
        long id,
        String worldName,
        int x,
        int y,
        int z,
        UUID ownerUuid,
        String ownerName,
        ItemStack itemTemplate,
        int bundleAmount,
        long priceDiamonds,
        long stockAmount,
        long revenueDiamonds,
        int discountPercent,
        String discountExpiresAt,
        double ratingAverage,
        int ratingCount,
        String emptyStockExpiresAt,
        String lastEmptyStockWarningAt,
        String createdAt,
        String updatedAt
) {

    public ShopRecord(
            long id,
            String worldName,
            int x,
            int y,
            int z,
            UUID ownerUuid,
            String ownerName,
            ItemStack itemTemplate,
            int bundleAmount,
            long priceDiamonds,
            long stockAmount,
            long revenueDiamonds,
            String emptyStockExpiresAt,
            String lastEmptyStockWarningAt,
            String createdAt,
            String updatedAt
    ) {
        this(id, worldName, x, y, z, ownerUuid, ownerName, itemTemplate, bundleAmount, priceDiamonds,
                stockAmount, revenueDiamonds, 0, null, 0.0D, 0, emptyStockExpiresAt,
                lastEmptyStockWarningAt, createdAt, updatedAt);
    }

    public Location blockLocation() {
        return new Location(Bukkit.getWorld(worldName), x, y, z);
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid != null && ownerUuid.equals(uuid);
    }

    public long maxBundles() {
        if (bundleAmount <= 0) {
            return 0;
        }
        return stockAmount / bundleAmount;
    }

    public boolean hasActiveDiscount(Clock clock) {
        if (discountPercent <= 0) {
            return false;
        }
        if (discountExpiresAt == null || discountExpiresAt.isBlank()) {
            return true;
        }
        try {
            return Instant.parse(discountExpiresAt).isAfter(Instant.now(clock));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public int activeDiscountPercent(Clock clock) {
        return hasActiveDiscount(clock) ? discountPercent : 0;
    }

    public long effectivePriceDiamonds(Clock clock) {
        int activeDiscount = activeDiscountPercent(clock);
        if (activeDiscount <= 0) {
            return Math.max(1L, priceDiamonds);
        }
        long discount;
        try {
            discount = Math.floorDiv(Math.multiplyExact(Math.max(1L, priceDiamonds), activeDiscount), 100L);
        } catch (ArithmeticException exception) {
            discount = Math.floorDiv(Long.MAX_VALUE, 100L);
        }
        return Math.max(1L, priceDiamonds - discount);
    }
}
