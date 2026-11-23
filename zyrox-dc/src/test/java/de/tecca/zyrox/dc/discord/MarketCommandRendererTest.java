package de.tecca.zyrox.dc.discord;

import de.tecca.zyrox.dc.market.DiscordMarketplaceShop;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketCommandRendererTest {

    @Test
    void emptyResultsRenderNoResultsWithoutButtons() {
        MarketCommandResponse response = MarketCommandRenderer.noResults(new MarketCommandFilters("stick", "", true));

        assertEquals("No matching shops found", response.embed().getTitle());
        assertTrue(response.components().isEmpty());
    }

    @Test
    void singleResultRendersShopDetailsAndDisabledPageButtons() {
        MarketCommandSession session = session(List.of(shop(1L, "DIAMOND_SWORD")), 0);

        MarketCommandResponse response = MarketCommandRenderer.page("abc", session, "https://cdn.example.com/items/");

        assertEquals("Marketplace Shop #1", response.embed().getTitle());
        assertEquals("https://cdn.example.com/items/diamond_sword.png", response.embed().getThumbnail().getUrl());
        assertEquals(1, response.components().size());
        List<Button> buttons = response.components().getFirst().getButtons();
        assertTrue(buttons.get(0).isDisabled());
        assertFalse(buttons.get(1).isDisabled());
        assertTrue(buttons.get(2).isDisabled());
    }

    @Test
    void multipleResultsRenderPageCountAndNavigationState() {
        MarketCommandSession session = session(List.of(
                shop(1L, "STONE"),
                shop(2L, "DIRT"),
                shop(3L, "OAK_LOG")
        ), 1);

        MarketCommandResponse response = MarketCommandRenderer.page("abc", session, "");

        assertEquals("Marketplace Shop #2", response.embed().getTitle());
        assertEquals("Result 2 of 3", response.embed().getFooter().getText());
        List<Button> buttons = response.components().getFirst().getButtons();
        assertFalse(buttons.get(0).isDisabled());
        assertFalse(buttons.get(1).isDisabled());
        assertFalse(buttons.get(2).isDisabled());
    }

    @Test
    void blankIconBaseUrlOmitsThumbnail() {
        MarketCommandResponse response = MarketCommandRenderer.page("abc", session(List.of(shop(1L, "DIAMOND")), 0), "");

        assertNull(response.embed().getThumbnail());
    }

    @Test
    void thumbnailUrlRequiresHttpsAndSafeMaterialName() {
        assertEquals(
                "https://cdn.example.com/items/golden_apple.png",
                MarketCommandRenderer.thumbnailUrl("https://cdn.example.com/items", "GOLDEN_APPLE")
        );
        assertNull(MarketCommandRenderer.thumbnailUrl("http://cdn.example.com/items", "GOLDEN_APPLE"));
        assertNull(MarketCommandRenderer.thumbnailUrl("https://cdn.example.com/items", "../bad"));
    }

    private static MarketCommandSession session(List<DiscordMarketplaceShop> shops, int page) {
        return new MarketCommandSession(
                "user-1",
                new MarketCommandFilters("", "", true),
                shops,
                page,
                Instant.parse("2026-07-14T00:00:00Z")
        );
    }

    private static DiscordMarketplaceShop shop(long id, String materialName) {
        return new DiscordMarketplaceShop(
                id,
                UUID.nameUUIDFromBytes(("owner-" + id).getBytes()),
                "Alex",
                "Sharp Sword",
                materialName,
                "Sharpness V",
                1,
                12,
                64,
                0,
                "world",
                10,
                64,
                -5
        );
    }
}
