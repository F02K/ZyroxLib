package de.tecca.zyrox.dc.storage;

import java.time.Instant;

public record LinkedAccount(
        String discordId,
        String discordName,
        String minecraftUsername,
        String minecraftUuid,
        Instant linkedAt,
        Instant updatedAt
) {
}

