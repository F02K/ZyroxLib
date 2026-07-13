package de.tecca.zyrox.dc.market;

import java.util.UUID;

public record DiscordMarketplaceShop(
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
        int z
) {
}
