package de.tecca.zyrox.dc.market;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.dc.discord.DiscordBotService;
import de.tecca.zyrox.dc.storage.LinkedAccount;
import de.tecca.zyrox.dc.storage.ZyroxDcStorage;
import de.tecca.zyrox.shop.api.ZyroxMarketShop;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEvent;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEventType;
import de.tecca.zyrox.shop.api.ZyroxShopMarketListener;
import de.tecca.zyrox.shop.api.ZyroxShopMarketService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class ZyroxDcMarketplaceIntegration implements ZyroxShopMarketListener, DiscordMarketplaceHandler {

    private final JavaPlugin plugin;
    private final ZyroxApi zyroxApi;
    private final ZyroxDcStorage storage;
    private final DiscordBotService discordBot;
    private final Clock clock;
    private ZyroxShopMarketService marketService;
    private BukkitTask reminderTask;

    public ZyroxDcMarketplaceIntegration(JavaPlugin plugin, ZyroxApi zyroxApi, ZyroxDcStorage storage, DiscordBotService discordBot) {
        this(plugin, zyroxApi, storage, discordBot, Clock.systemUTC());
    }

    ZyroxDcMarketplaceIntegration(JavaPlugin plugin, ZyroxApi zyroxApi, ZyroxDcStorage storage, DiscordBotService discordBot, Clock clock) {
        this.plugin = plugin;
        this.zyroxApi = zyroxApi;
        this.storage = storage;
        this.discordBot = discordBot;
        this.clock = clock;
    }

    public void start() {
        stop();
        if (!enabled()) {
            return;
        }

        Optional<ZyroxShopMarketService> service = zyroxApi.services().get(ZyroxShopMarketService.class);
        if (service.isEmpty()) {
            plugin.getLogger().warning("ZyroxDC marketplace is enabled, but ZyroxShop market service is not available.");
            return;
        }

        marketService = service.get();
        marketService.registerListener(this);
        discordBot.setMarketplaceHandler(this);
        long periodTicks = 20L * 60L * 10L;
        reminderTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sendRevenueReminders, 20L * 60L, periodTicks);
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
        if (marketService != null) {
            marketService.unregisterListener(this);
            marketService = null;
        }
        discordBot.setMarketplaceHandler(null);
    }

    @Override
    public void onMarketShopEvent(ZyroxMarketShopEvent event) {
        if (!enabled() || event == null || event.shop() == null) {
            return;
        }
        if (event.type() == ZyroxMarketShopEventType.CREATED) {
            return;
        }
        if (!plugin.getConfig().getBoolean("marketplace.post-updates", true)) {
            return;
        }
        discordBot.sendMarketplaceShopPost(event.type().name(), toDiscordShop(event.shop()));
    }

    @Override
    public List<DiscordMarketplaceShop> search(String itemFilter, String ownerFilter, boolean inStockOnly) throws SQLException {
        if (!enabled() || marketService == null) {
            return List.of();
        }
        return marketService.search(itemFilter, ownerFilter, inStockOnly).stream()
                .map(this::toDiscordShop)
                .toList();
    }

    private void sendRevenueReminders() {
        if (!enabled() || marketService == null || !discordBot.isRunning()) {
            return;
        }
        try {
            Instant now = Instant.now(clock);
            Duration interval = Duration.ofHours(Math.max(1L, plugin.getConfig().getLong("marketplace.revenue-reminder-hours", 24L)));
            for (ZyroxMarketShop shop : marketService.shopsWithRevenue()) {
                Optional<Instant> lastSent = storage.findMarketplaceRevenueReminder(shop.id(), shop.ownerUuid());
                if (!MarketplaceRevenueReminderPolicy.shouldSend(lastSent, interval, now)) {
                    continue;
                }
                Optional<LinkedAccount> account = storage.findByMinecraftIdentity(shop.ownerUuid(), shop.ownerName());
                if (account.isEmpty()) {
                    continue;
                }
                discordBot.sendMarketplaceRevenueReminder(account.get().discordId(), toDiscordShop(shop));
                storage.markMarketplaceRevenueReminderSent(shop.id(), shop.ownerUuid(), now);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not send marketplace revenue reminders: " + exception.getMessage());
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("marketplace.enabled", false);
    }

    private DiscordMarketplaceShop toDiscordShop(ZyroxMarketShop shop) {
        return new DiscordMarketplaceShop(
                shop.id(),
                shop.ownerUuid(),
                shop.ownerName(),
                shop.itemName(),
                shop.materialName(),
                shop.enchantments(),
                shop.bundleAmount(),
                shop.priceDiamonds(),
                shop.stockAmount(),
                shop.revenueDiamonds(),
                shop.worldName(),
                shop.x(),
                shop.y(),
                shop.z()
        );
    }
}
