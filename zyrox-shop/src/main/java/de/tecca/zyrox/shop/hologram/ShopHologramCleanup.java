package de.tecca.zyrox.shop.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.List;

final class ShopHologramCleanup {

    static final String TEXT_DISPLAY_TAG = "zyrox-shop-hologram";
    static final String TEXT_DISPLAY_SHOP_ID_TAG_PREFIX = "zyrox-shop-hologram-id-";

    private static final String DECENT_NAME_PREFIX = "zyrox_shop_";

    private ShopHologramCleanup() {
    }

    static Method decentRemoveHologramMethod() throws ReflectiveOperationException {
        return dhapiClass().getMethod("removeHologram", String.class);
    }

    static Method decentCreateHologramMethod() throws ReflectiveOperationException {
        return dhapiClass().getMethod("createHologram", String.class, Location.class, boolean.class, List.class);
    }

    static void removeTextDisplays(JavaPlugin plugin) {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(TEXT_DISPLAY_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    static void removeTextDisplay(JavaPlugin plugin, long shopId) {
        String shopIdTag = textDisplayShopIdTag(shopId);
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(TEXT_DISPLAY_TAG)
                        && entity.getScoreboardTags().contains(shopIdTag)) {
                    entity.remove();
                }
            }
        }
    }

    static void removeDecentHologram(JavaPlugin plugin, Method removeHologram, long shopId) {
        try {
            removeHologram.invoke(null, decentName(shopId));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not remove DecentHolograms shop hologram " + shopId
                    + ": " + exception.getMessage());
        }
    }

    static void removeDecentHologramIfAvailable(JavaPlugin plugin, long shopId) {
        try {
            removeDecentHologram(plugin, decentRemoveHologramMethod(), shopId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // DecentHolograms is optional; if its API is not available there is nothing to clean up.
        }
    }

    static String textDisplayShopIdTag(long shopId) {
        return TEXT_DISPLAY_SHOP_ID_TAG_PREFIX + shopId;
    }

    static String decentName(long shopId) {
        return DECENT_NAME_PREFIX + shopId;
    }

    private static Class<?> dhapiClass() throws ClassNotFoundException {
        return Class.forName("eu.decentsoftware.holograms.api.DHAPI");
    }
}
