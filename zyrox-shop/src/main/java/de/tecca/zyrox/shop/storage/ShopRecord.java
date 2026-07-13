package de.tecca.zyrox.shop.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

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
        String emptyStockExpiresAt,
        String lastEmptyStockWarningAt,
        String createdAt,
        String updatedAt
) {

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
}
