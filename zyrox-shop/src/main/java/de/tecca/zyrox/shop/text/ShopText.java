package de.tecca.zyrox.shop.text;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.util.ItemStacks;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
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
            lines.add(format(line, placeholders));
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
                    "Preis: {price}",
                    "Lager: {stock}",
                    "Besitzer: {owner}"
            );
        }
        List<String> lines = new ArrayList<>();
        for (String line : configured) {
            lines.add(format(line, placeholders));
        }
        return lines;
    }

    public Map<String, String> placeholders(ShopRecord shop) {
        return Map.of(
                "shop_id", Long.toString(shop.id()),
                "item", ItemStacks.displayName(shop.itemTemplate()),
                "bundle", Integer.toString(shop.bundleAmount()),
                "price", payments.formatDiamonds(shop.priceDiamonds()),
                "price_raw", Long.toString(shop.priceDiamonds()),
                "stock", Long.toString(shop.stockAmount()),
                "owner", shop.ownerName(),
                "revenue", payments.formatDiamonds(shop.revenueDiamonds())
        );
    }

    public String format(String value, Map<String, String> placeholders) {
        String formatted = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return formatted;
    }
}
