package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.storage.ShopRecord;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;

import java.util.HashMap;
import java.util.Map;

final class ShopItemDisplayManager {

    private static final String TAG = "zyrox-shop-display";
    private static final String SHOP_ID_TAG_PREFIX = "zyrox-shop-display-id-";

    private final JavaPlugin plugin;
    private final Map<Long, ItemDisplay> displays = new HashMap<>();

    ShopItemDisplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
        removeTaggedDisplays();
    }

    void upsert(ShopRecord shop) {
        Location location = shop.blockLocation();
        if (location.getWorld() == null) {
            return;
        }
        double offsetY = plugin.getConfig().getDouble("shop.item-display-offset-y", 1.05);
        Location displayLocation = location.add(0.5, offsetY, 0.5);
        ItemDisplay display = displays.get(shop.id());
        if (display == null || display.isDead()) {
            display = displayLocation.getWorld().spawn(displayLocation, ItemDisplay.class);
            display.addScoreboardTag(TAG);
            displays.put(shop.id(), display);
        } else {
            display.teleport(displayLocation);
        }
        display.addScoreboardTag(shopIdTag(shop.id()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(scaled(display.getTransformation(), "shop.item-display-scale", 1.0f));
        display.setItemStack(shop.itemTemplate().clone());
    }

    void remove(long shopId) {
        ItemDisplay display = displays.remove(shopId);
        if (display != null && !display.isDead()) {
            display.remove();
        }
        removeTaggedDisplay(shopId);
    }

    void clear() {
        for (ItemDisplay display : displays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        displays.clear();
        removeTaggedDisplays();
    }

    private void removeTaggedDisplays() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void removeTaggedDisplay(long shopId) {
        String shopIdTag = shopIdTag(shopId);
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG) && entity.getScoreboardTags().contains(shopIdTag)) {
                    entity.remove();
                }
            }
        }
    }

    private String shopIdTag(long shopId) {
        return SHOP_ID_TAG_PREFIX + shopId;
    }

    private Transformation scaled(Transformation transformation, String configPath, float defaultScale) {
        float scale = (float) plugin.getConfig().getDouble(configPath, defaultScale);
        return new Transformation(
                transformation.getTranslation(),
                transformation.getLeftRotation(),
                transformation.getScale().set(scale, scale, scale),
                transformation.getRightRotation()
        );
    }
}
