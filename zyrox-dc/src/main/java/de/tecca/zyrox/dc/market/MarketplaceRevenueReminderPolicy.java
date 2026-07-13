package de.tecca.zyrox.dc.market;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class MarketplaceRevenueReminderPolicy {

    private MarketplaceRevenueReminderPolicy() {
    }

    public static boolean shouldSend(Optional<Instant> lastSentAt, Duration interval, Instant now) {
        if (lastSentAt.isEmpty()) {
            return true;
        }
        Duration safeInterval = interval == null || interval.isNegative() || interval.isZero()
                ? Duration.ofHours(24)
                : interval;
        return !now.minus(safeInterval).isBefore(lastSentAt.get());
    }
}
