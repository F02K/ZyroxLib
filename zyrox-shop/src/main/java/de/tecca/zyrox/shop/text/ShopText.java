package de.tecca.zyrox.shop.text;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.util.ItemStacks;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class ShopText {

    private final JavaPlugin plugin;
    private final ZyroxDiamondPaymentService payments;

    public ShopText(JavaPlugin plugin, ZyroxDiamondPaymentService payments) {
        this.plugin = plugin;
        this.payments = payments;
    }

    public String message(String path, Map<String, String> placeholders) {
        return format(plugin.getConfig().getString("messages." + path, path), placeholders);
    }

    public String gui(String path, Map<String, String> placeholders) {
        return format(plugin.getConfig().getString("gui." + path, path), placeholders);
    }

    public List<String> guiList(String path, Map<String, String> placeholders) {
        List<String> configured = plugin.getConfig().getStringList("gui." + path);
        if (configured.isEmpty() && plugin.getConfig().isString("gui." + path)) {
            configured = List.of(plugin.getConfig().getString("gui." + path, ""));
        }
        List<String> lines = new ArrayList<>();
        for (String line : configured) {
            addFormattedLine(lines, line, placeholders);
        }
        return lines;
    }

    public List<String> hologramLines(ShopRecord shop) {
        Map<String, String> placeholders = placeholders(shop);
        List<String> configured = plugin.getConfig().getStringList("holograms.lines");
        if (configured.isEmpty()) {
            configured = List.of(
                    "Shop #{shop_id}",
                    "{bundle}x {item}",
                    "{enchant_lines}",
                    "Preis: {price}",
                    "Lager: {stock}",
                    "{empty_stock_lines}",
                    "Besitzer: {owner}"
            );
        }
        List<String> lines = new ArrayList<>();
        for (String line : configured) {
            addFormattedLine(lines, line, placeholders);
        }
        return lines;
    }

    public Map<String, String> placeholders(ShopRecord shop) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("shop_id", Long.toString(shop.id()));
        placeholders.put("item", ItemStacks.displayName(shop.itemTemplate()));
        placeholders.put("material", shop.itemTemplate().getType().name());
        placeholders.put("bundle", Integer.toString(shop.bundleAmount()));
        placeholders.put("price", payments.formatDiamonds(shop.priceDiamonds()));
        placeholders.put("price_raw", Long.toString(shop.priceDiamonds()));
        placeholders.put("original_price", payments.formatDiamonds(shop.priceDiamonds()));
        placeholders.put("effective_price", payments.formatDiamonds(shop.effectivePriceDiamonds(Clock.systemUTC())));
        placeholders.put("stock", Long.toString(shop.stockAmount()));
        placeholders.put("owner", shop.ownerName());
        placeholders.put("world", shop.worldName());
        placeholders.put("x", Integer.toString(shop.x()));
        placeholders.put("y", Integer.toString(shop.y()));
        placeholders.put("z", Integer.toString(shop.z()));
        placeholders.put("revenue", payments.formatDiamonds(shop.revenueDiamonds()));
        placeholders.put("enchants", ItemStacks.enchantmentSummary(shop.itemTemplate()));
        placeholders.put("rating", shop.ratingCount() <= 0 ? "-" : String.format(Locale.ROOT, "%.1f", shop.ratingAverage()));
        placeholders.put("rating_count", Integer.toString(shop.ratingCount()));
        placeholders.put("discount", discountText(shop));
        placeholders.put("discount_percent", Integer.toString(shop.activeDiscountPercent(Clock.systemUTC())));
        placeholders.put("discount_expires_at", formatInstant(shop.discountExpiresAt()));
        placeholders.put("empty_stock_expires_at", formatInstant(shop.emptyStockExpiresAt()));
        placeholders.put("empty_stock_time_left", timeLeft(shop.emptyStockExpiresAt()));
        return placeholders;
    }

    public String format(String value, Map<String, String> placeholders) {
        String formatted = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return formatted;
    }

    private void addFormattedLine(List<String> lines, String line, Map<String, String> placeholders) {
        if (line == null) {
            return;
        }
        if (!line.contains("{enchant_lines}")) {
            if (line.contains("{empty_stock_lines}")) {
                addEmptyStockLine(lines, line, placeholders);
                return;
            }
            addIfNotBlank(lines, format(line, placeholders));
            return;
        }
        String enchantSummary = placeholders.getOrDefault("enchants", "");
        if (enchantSummary.isBlank()) {
            return;
        }
        for (String enchantment : enchantSummary.split(", ")) {
            addIfNotBlank(lines, format(line.replace("{enchant_lines}", enchantment), placeholders));
        }
    }

    private void addEmptyStockLine(List<String> lines, String line, Map<String, String> placeholders) {
        String timeLeft = placeholders.getOrDefault("empty_stock_time_left", "-");
        if ("-".equals(timeLeft)) {
            return;
        }
        String configured = plugin.getConfig().getString("holograms.empty-stock-line", "Schliesst in: {empty_stock_time_left}");
        addIfNotBlank(lines, format(line.replace("{empty_stock_lines}", configured), placeholders));
    }

    private void addIfNotBlank(List<String> lines, String line) {
        if (line != null && !line.isBlank()) {
            lines.add(line);
        }
    }

    private String formatInstant(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private String discountText(ShopRecord shop) {
        if (!shop.hasActiveDiscount(Clock.systemUTC())) {
            return "-";
        }
        if (shop.discountExpiresAt() == null || shop.discountExpiresAt().isBlank()) {
            return shop.discountPercent() + "%";
        }
        return shop.discountPercent() + "% bis " + formatInstant(shop.discountExpiresAt());
    }

    private String timeLeft(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        try {
            Duration duration = Duration.between(Instant.now(), Instant.parse(value));
            if (duration.isNegative() || duration.isZero()) {
                return "jetzt";
            }
            long days = duration.toDays();
            long hours = duration.minusDays(days).toHours();
            if (days > 0) {
                return days + "d " + hours + "h";
            }
            long minutes = duration.minusHours(hours).toMinutes();
            if (hours > 0) {
                return hours + "h " + minutes + "m";
            }
            return Math.max(1, minutes) + "m";
        } catch (RuntimeException exception) {
            return "-";
        }
    }
}
