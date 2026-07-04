package de.tecca.zyrox.dc.storage;

import java.time.Instant;

public record DiscordBan(
        String discordId,
        String reason,
        Instant bannedAt,
        Instant updatedAt
) {
}
