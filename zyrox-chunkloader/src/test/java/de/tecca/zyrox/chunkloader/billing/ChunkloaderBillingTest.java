package de.tecca.zyrox.chunkloader.billing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkloaderBillingTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void chargesLinearRanks() {
        List<ChunkloaderBilling.Decision> decisions = ChunkloaderBilling.evaluate(List.of(
                input(1, 1, 10, null),
                input(2, 2, 10, null),
                input(3, 3, 10, null)
        ), NOW, 1, Duration.ofHours(1));

        assertEquals(1, decisions.get(0).chargedBlocks());
        assertEquals(2, decisions.get(1).chargedBlocks());
        assertEquals(3, decisions.get(2).chargedBlocks());
        assertEquals(9, decisions.get(0).reserveAfter());
        assertEquals(8, decisions.get(1).reserveAfter());
        assertEquals(7, decisions.get(2).reserveAfter());
    }

    @Test
    void unpaidLoaderIsSkippedAndLaterLoadersMoveUp() {
        List<ChunkloaderBilling.Decision> decisions = ChunkloaderBilling.evaluate(List.of(
                input(1, 1, 0, null),
                input(2, 2, 5, null),
                input(3, 3, 5, null)
        ), NOW, 1, Duration.ofHours(1));

        assertFalse(decisions.get(0).active());
        assertEquals(1, decisions.get(1).rank());
        assertEquals(1, decisions.get(1).chargedBlocks());
        assertEquals(2, decisions.get(2).rank());
        assertEquals(2, decisions.get(2).chargedBlocks());
    }

    @Test
    void prepaidCycleDoesNotChargeAgain() {
        Instant paidUntil = NOW.plus(Duration.ofMinutes(20));
        List<ChunkloaderBilling.Decision> decisions = ChunkloaderBilling.evaluate(List.of(
                input(1, 1, 5, paidUntil),
                input(2, 2, 5, null)
        ), NOW, 1, Duration.ofHours(1));

        assertTrue(decisions.get(0).active());
        assertEquals(0, decisions.get(0).chargedBlocks());
        assertEquals(5, decisions.get(0).reserveAfter());
        assertEquals(2, decisions.get(1).rank());
        assertEquals(2, decisions.get(1).chargedBlocks());
    }

    @Test
    void disabledWorldDoesNotConsumeRank() {
        List<ChunkloaderBilling.Decision> decisions = ChunkloaderBilling.evaluate(List.of(
                new ChunkloaderBilling.Input(1, OWNER, 1, true, true, 10, null),
                input(2, 2, 10, null)
        ), NOW, 1, Duration.ofHours(1));

        assertFalse(decisions.get(0).active());
        assertEquals(1, decisions.get(1).rank());
        assertEquals(1, decisions.get(1).chargedBlocks());
    }

    private static ChunkloaderBilling.Input input(long id, int priority, long reserve, Instant paidUntil) {
        return new ChunkloaderBilling.Input(id, OWNER, priority, true, false, reserve, paidUntil);
    }
}
