package de.tecca.zyrox.villagershop.text;

import de.tecca.zyrox.villagershop.storage.VillagerShopRecord;
import de.tecca.zyrox.villagershop.storage.VillagerTradeRecord;
import de.tecca.zyrox.villagershop.util.ItemStacks;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VillagerShopText {

    private final JavaPlugin plugin;

    public VillagerShopText(JavaPlugin plugin) {
        this.plugin = plugin;
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

    public Map<String, String> placeholders(VillagerShopRecord shop) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("shop_id", Long.toString(shop.id()));
        placeholders.put("name", shop.displayName());
        placeholders.put("display_text", shop.displayText());
        placeholders.put("profession", shop.profession());
        placeholders.put("trade_count", Integer.toString(shop.trades().size()));
        placeholders.put("world", shop.worldName());
        placeholders.put("x", Integer.toString((int) Math.floor(shop.x())));
        placeholders.put("y", Integer.toString((int) Math.floor(shop.y())));
        placeholders.put("z", Integer.toString((int) Math.floor(shop.z())));
        VillagerTradeRecord trade = shop.trades().isEmpty() ? null : shop.trades().get(0);
        placeholders.put("input_one", trade == null ? "Leer" : ItemStacks.displayName(trade.inputOne()));
        placeholders.put("input_two", trade != null && trade.hasSecondInput() ? " + " + ItemStacks.displayName(trade.inputTwo()) : "");
        placeholders.put("output", trade == null ? "Leer" : ItemStacks.displayName(trade.output()));
        placeholders.put("max_uses", trade == null || trade.unlimited() ? "unbegrenzt" : Integer.toString(trade.maxUses()));
        placeholders.put("uses", trade == null ? "0" : Integer.toString(trade.currentUses()));
        placeholders.put("unlimited", trade == null || trade.unlimited() ? "Ja" : "Nein");
        return placeholders;
    }

    public String format(String value, Map<String, String> placeholders) {
        String formatted = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return formatted;
    }
}
