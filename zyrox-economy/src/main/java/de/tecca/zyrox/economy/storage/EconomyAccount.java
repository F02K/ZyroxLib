package de.tecca.zyrox.economy.storage;

public record EconomyAccount(
        String playerKey,
        String playerName,
        String playerUuid,
        long changeBalanceCents,
        long payoutBalanceCents
) {
}
