package de.tecca.zyrox.dc.discord;

import de.tecca.zyrox.dc.market.DiscordMarketplaceShop;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

record MarketCommandSession(
        String requesterId,
        MarketCommandFilters filters,
        List<DiscordMarketplaceShop> results,
        int page,
        Instant createdAt
) {

    MarketCommandSession {
        results = results == null ? List.of() : List.copyOf(results);
        page = clampPage(page, results.size());
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    boolean isExpired(Instant now, Duration ttl) {
        return createdAt.plus(ttl).isBefore(now);
    }

    MarketCommandSession withPage(int nextPage) {
        return new MarketCommandSession(requesterId, filters, results, nextPage, createdAt);
    }

    MarketCommandSession withResults(List<DiscordMarketplaceShop> nextResults, Instant now) {
        return new MarketCommandSession(requesterId, filters, nextResults, page, now);
    }

    private static int clampPage(int page, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(page, size - 1));
    }
}
