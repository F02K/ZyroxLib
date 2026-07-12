package de.tecca.zyrox.enchants.text;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class EnchantText {

    private final JavaPlugin plugin;

    public EnchantText(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String message(String path, Map<String, String> placeholders) {
        return format(plugin.getConfig().getString("messages." + path, path), placeholders);
    }

    public String format(String text, Map<String, String> placeholders) {
        String formatted = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return formatted;
    }
}
