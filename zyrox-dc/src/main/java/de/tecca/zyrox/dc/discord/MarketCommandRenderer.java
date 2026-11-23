package de.tecca.zyrox.dc.discord;

import de.tecca.zyrox.dc.market.DiscordMarketplaceShop;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

final class MarketCommandRenderer {

    static final String COMPONENT_PREFIX = "zyroxdc_market:";
    static final String ACTION_PREVIOUS = "previous";
    static final String ACTION_REFRESH = "refresh";
    static final String ACTION_NEXT = "next";

    private static final Color MARKET_COLOR = new Color(72, 187, 120);
    private static final Color WARNING_COLOR = new Color(246, 173, 85);
    private static final Color ERROR_COLOR = new Color(229, 62, 62);

    private MarketCommandRenderer() {
    }

    static MarketCommandResponse page(String sessionId, MarketCommandSession session, String iconBaseUrl) {
        if (session.results().isEmpty()) {
            return noResults(session.filters());
        }

        int page = Math.max(0, Math.min(session.page(), session.results().size() - 1));
        DiscordMarketplaceShop shop = session.results().get(page);
        String footer = "Result " + (page + 1) + " of " + session.results().size();
        if (session.results().size() >= DiscordBotService.MARKET_RESULT_LIMIT) {
            footer += " - showing first " + DiscordBotService.MARKET_RESULT_LIMIT + " matches";
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Marketplace Shop #" + shop.id())
                .setDescription(filterSummary(session.filters()))
                .setColor(MARKET_COLOR)
                .setTimestamp(Instant.now())
                .addField("Item", limit(shop.bundleAmount() + "x " + safe(shop.itemName())), true)
                .addField("Material", limit(readableMaterial(shop.materialName())), true)
                .addField("Price", shop.priceDiamonds() + " diamond(s)", true)
                .addField("Stock", shop.stockAmount() + " item(s)", true)
                .addField("Owner", limit(safe(shop.ownerName())), true)
                .addField("Location", limit(shop.worldName() + " " + shop.x() + " " + shop.y() + " " + shop.z()), true)
                .setFooter(footer);

        if (shop.enchantments() != null && !shop.enchantments().isBlank()) {
            embed.addField("Enchantments", limit(shop.enchantments()), false);
        }

        String thumbnailUrl = thumbnailUrl(iconBaseUrl, shop.materialName());
        if (thumbnailUrl != null) {
            embed.setThumbnail(thumbnailUrl);
        }

        return new MarketCommandResponse(embed.build(), List.of(navigation(sessionId, page, session.results().size())));
    }

    static MarketCommandResponse noResults(MarketCommandFilters filters) {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("No matching shops found")
                .setDescription(filterSummary(filters))
                .setColor(WARNING_COLOR)
                .setTimestamp(Instant.now())
                .build();
        return new MarketCommandResponse(embed, List.of());
    }

    static MarketCommandResponse unavailable() {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Marketplace unavailable")
                .setDescription("The marketplace search command is currently unavailable.")
                .setColor(WARNING_COLOR)
                .setTimestamp(Instant.now())
                .build();
        return new MarketCommandResponse(embed, List.of());
    }

    static MarketCommandResponse expired() {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Marketplace session expired")
                .setDescription("Run /market again to start a fresh search.")
                .setColor(WARNING_COLOR)
                .setTimestamp(Instant.now())
                .build();
        return new MarketCommandResponse(embed, List.of());
    }

    static MarketCommandResponse forbidden() {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("This is not your market search")
                .setDescription("Run /market to create your own marketplace result view.")
                .setColor(WARNING_COLOR)
                .setTimestamp(Instant.now())
                .build();
        return new MarketCommandResponse(embed, List.of());
    }

    static MarketCommandResponse error(String message) {
        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Marketplace search failed")
                .setDescription(limit(message == null || message.isBlank() ? "An unknown error occurred." : message))
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now())
                .build();
        return new MarketCommandResponse(embed, List.of());
    }

    static String thumbnailUrl(String iconBaseUrl, String materialName) {
        if (iconBaseUrl == null || iconBaseUrl.isBlank() || materialName == null || materialName.isBlank()) {
            return null;
        }

        String material = materialName.trim().toLowerCase(Locale.ROOT);
        if (!material.matches("[a-z0-9_]+")) {
            return null;
        }

        String base = iconBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.startsWith("https://")) {
            return null;
        }
        return base + "/" + material + ".png";
    }

    private static ActionRow navigation(String sessionId, int page, int total) {
        Button previous = Button.secondary(componentId(sessionId, ACTION_PREVIOUS), "Previous")
                .withDisabled(page <= 0);
        Button refresh = Button.primary(componentId(sessionId, ACTION_REFRESH), "Refresh");
        Button next = Button.secondary(componentId(sessionId, ACTION_NEXT), "Next")
                .withDisabled(page >= total - 1);
        return ActionRow.of(previous, refresh, next);
    }

    private static String componentId(String sessionId, String action) {
        return COMPONENT_PREFIX + sessionId + ":" + action;
    }

    private static String filterSummary(MarketCommandFilters filters) {
        if (filters == null || !filters.hasAnyFilter()) {
            return "Showing available player shops.";
        }

        StringBuilder builder = new StringBuilder("Filters:");
        if (!filters.item().isBlank()) {
            builder.append(" item=").append(filters.item());
        }
        if (!filters.owner().isBlank()) {
            builder.append(" owner=").append(filters.owner());
        }
        if (!filters.inStockOnly()) {
            builder.append(" including out of stock");
        }
        return limit(builder.toString());
    }

    private static String readableMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return "Unknown";
        }
        String[] parts = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? materialName : builder.toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private static String limit(String value) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        int max = MessageEmbed.VALUE_MAX_LENGTH;
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
