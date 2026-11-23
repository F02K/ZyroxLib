package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;

import java.util.HashMap;
import java.util.Map;

public final class TextDisplayShopHologramService implements ShopHologramService {

    private final JavaPlugin plugin;
    private final ShopText text;
    private final ShopItemDisplayManager itemDisplays;
    private final Map<Long, TextDisplay> displays = new HashMap<>();

    public TextDisplayShopHologramService(JavaPlugin plugin, ShopText text) {
        this.plugin = plugin;
        this.text = text;
        itemDisplays = new ShopItemDisplayManager(plugin);
        ShopHologramCleanup.removeTextDisplays(plugin);
    }

    @Override
    public void upsert(ShopRecord shop) {
        ShopHologramCleanup.removeDecentHologramIfAvailable(plugin, shop.id());
        Location location = HologramLines.location(plugin, shop);
        if (location == null) {
            return;
        }
        TextDisplay display = displays.get(shop.id());
        if (display == null || display.isDead()) {
            display = location.getWorld().spawn(location, TextDisplay.class);
            display.addScoreboardTag(ShopHologramCleanup.TEXT_DISPLAY_TAG);
            displays.put(shop.id(), display);
        } else {
            display.teleport(location);
        }
        display.addScoreboardTag(ShopHologramCleanup.textDisplayShopIdTag(shop.id()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setTransformation(scaled(display.getTransformation(), "shop.hologram-text-scale", 1.0f));
        display.setText(String.join("\n", HologramLines.forShop(text, shop)));
        itemDisplays.upsert(shop);
    }

    @Override
    public void remove(long shopId) {
        TextDisplay display = displays.remove(shopId);
        if (display != null && !display.isDead()) {
            display.remove();
        }
        ShopHologramCleanup.removeTextDisplay(plugin, shopId);
        itemDisplays.remove(shopId);
    }

    @Override
    public void clear() {
        for (TextDisplay display : displays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        displays.clear();
        ShopHologramCleanup.removeTextDisplays(plugin);
        itemDisplays.clear();
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
