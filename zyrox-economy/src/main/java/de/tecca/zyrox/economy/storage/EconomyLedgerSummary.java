package de.tecca.zyrox.economy.storage;

public record EconomyLedgerSummary(
        long shopReserveCents,
        long totalChangeBalanceCents,
        long totalPayoutBalanceCents,
        long accountCount
) {
}
