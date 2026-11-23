package de.tecca.zyrox.shop.storage;

import java.util.UUID;

public record ShopTransactionRecord(
        long id,
        long shopId,
        UUID actorUuid,
        String actorName,
        ShopTransactionType type,
        long itemAmount,
        long diamondAmount,
        String createdAt
) {
}
