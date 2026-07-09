package de.tecca.zyrox.end.service;

import java.util.UUID;

public record DragonEggClaim(
        UUID eggId,
        UUID ownerUniqueId,
        String ownerName,
        long claimedAtEpochMillis
) {
}
