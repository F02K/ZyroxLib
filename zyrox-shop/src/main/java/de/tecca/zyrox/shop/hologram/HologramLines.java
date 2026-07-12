package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

final class HologramLines {

    private HologramLines() {
    }

    static List<String> forShop(ShopText text, ShopRecord shop) {
        return text.hologramLines(shop);
    }

    static Location location(JavaPlugin plugin, ShopRecord shop) {
        Location location = shop.blockLocation();
        if (location.getWorld() == null) {
            return null;
        }
        double legacyOffsetY = plugin.getConfig().getDouble("shop.hologram-offset-y", 1.35);
        double offsetY = plugin.getConfig().getDouble("shop.hologram-text-offset-y", legacyOffsetY);
        return location.add(0.5, offsetY, 0.5);
    }
}
