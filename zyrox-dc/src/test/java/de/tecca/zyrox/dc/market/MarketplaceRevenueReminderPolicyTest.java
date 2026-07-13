package de.tecca.zyrox.dc.market;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketplaceRevenueReminderPolicyTest {

    @Test
    void sendsWhenNoPreviousReminderExists() {
        assertTrue(MarketplaceRevenueReminderPolicy.shouldSend(
                Optional.empty(),
                Duration.ofHours(24),
                Instant.parse("2026-07-13T12:00:00Z")
        ));
    }

    @Test
    void suppressesReminderBeforeIntervalElapsed() {
        assertFalse(MarketplaceRevenueReminderPolicy.shouldSend(
                Optional.of(Instant.parse("2026-07-13T00:00:00Z")),
                Duration.ofHours(24),
                Instant.parse("2026-07-13T12:00:00Z")
        ));
    }

    @Test
    void sendsReminderAfterIntervalElapsed() {
        assertTrue(MarketplaceRevenueReminderPolicy.shouldSend(
                Optional.of(Instant.parse("2026-07-12T12:00:00Z")),
                Duration.ofHours(24),
                Instant.parse("2026-07-13T12:00:00Z")
        ));
    }
}
