package de.tecca.zyrox.dc.market;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.dc.discord.DiscordBotService;
import de.tecca.zyrox.dc.storage.ZyroxDcStorage;
import de.tecca.zyrox.shop.api.ZyroxMarketShop;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEvent;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEventType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class ZyroxDcMarketplaceIntegrationTest {

    @Test
    void doesNotPostCreatedShopsToDiscord() {
        DiscordBotService discordBot = mock(DiscordBotService.class);
        ZyroxDcMarketplaceIntegration integration = integration(discordBot, true);

        integration.onMarketShopEvent(new ZyroxMarketShopEvent(ZyroxMarketShopEventType.CREATED, shop()));

        verify(discordBot, never()).sendMarketplaceShopPost(any(), any());
    }

    @Test
    void stillPostsShopUpdatesWhenEnabled() {
        DiscordBotService discordBot = mock(DiscordBotService.class);
        ZyroxDcMarketplaceIntegration integration = integration(discordBot, true);

        integration.onMarketShopEvent(new ZyroxMarketShopEvent(ZyroxMarketShopEventType.UPDATED, shop()));

        verify(discordBot).sendMarketplaceShopPost(eq("UPDATED"), any(DiscordMarketplaceShop.class));
    }

    @Test
    void suppressesShopUpdatesWhenDisabled() {
        DiscordBotService discordBot = mock(DiscordBotService.class);
        ZyroxDcMarketplaceIntegration integration = integration(discordBot, false);

        integration.onMarketShopEvent(new ZyroxMarketShopEvent(ZyroxMarketShopEventType.UPDATED, shop()));

        verify(discordBot, never()).sendMarketplaceShopPost(any(), any());
    }

    private static ZyroxDcMarketplaceIntegration integration(DiscordBotService discordBot, boolean postUpdates) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("marketplace.enabled", true);
        config.set("marketplace.post-updates", postUpdates);
        when(plugin.getConfig()).thenReturn(config);
        return new ZyroxDcMarketplaceIntegration(
                plugin,
                mock(ZyroxApi.class),
                mock(ZyroxDcStorage.class),
                discordBot,
                Clock.systemUTC()
        );
    }

    private static ZyroxMarketShop shop() {
        return new ZyroxMarketShop(
                1L,
                UUID.nameUUIDFromBytes("owner".getBytes()),
                "Alex",
                "Stick",
                "STICK",
                "",
                16,
                2,
                2,
                0,
                "",
                0.0,
                0,
                64,
                0,
                "world",
                10,
                64,
                -5,
                "2026-07-14T00:00:00Z",
                "2026-07-14T00:00:00Z"
        );
    }
}
