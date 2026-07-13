package de.tecca.zyrox.shop.api;

import java.util.UUID;

public record ZyroxMarketShop(
        long id,
        UUID ownerUuid,
        String ownerName,
        String itemName,
        String materialName,
        String enchantments,
        int bundleAmount,
        long priceDiamonds,
        long stockAmount,
        long revenueDiamonds,
        String worldName,
        int x,
        int y,
        int z,
        String createdAt,
        String updatedAt
) {

    public boolean inStock() {
        return stockAmount > 0;
    }

    public long maxBundles() {
        return bundleAmount <= 0 ? 0 : stockAmount / bundleAmount;
    }
}
