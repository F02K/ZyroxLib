package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopHolograms {

    private ShopHolograms() {
    }

    public static ShopHologramService create(JavaPlugin plugin, ShopText text) {
        String backend = plugin.getConfig().getString("holograms.backend", "auto");
        if ("none".equalsIgnoreCase(backend)) {
            return new NoopShopHologramService();
        }
        boolean decentLoaded = plugin.getServer().getPluginManager().isPluginEnabled("DecentHolograms");
        if (("auto".equalsIgnoreCase(backend) && decentLoaded) || "decent".equalsIgnoreCase(backend)) {
            try {
                return new DecentShopHologramService(plugin, text);
            } catch (NoClassDefFoundError error) {
                plugin.getLogger().warning("DecentHolograms API is unavailable. Falling back to TextDisplay holograms.");
            }
        }
        return new TextDisplayShopHologramService(plugin, text);
    }
}
