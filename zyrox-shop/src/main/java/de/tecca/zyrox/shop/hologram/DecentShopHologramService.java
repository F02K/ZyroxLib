package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class DecentShopHologramService implements ShopHologramService {

    private final JavaPlugin plugin;
    private final ShopText text;
    private final ShopItemDisplayManager itemDisplays;
    private final Method removeHologram;
    private final Method createHologram;
    private final Set<Long> activeShopIds = new HashSet<>();

    public DecentShopHologramService(JavaPlugin plugin, ShopText text) {
        this.plugin = plugin;
        this.text = text;
        itemDisplays = new ShopItemDisplayManager(plugin);
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            removeHologram = dhapiClass.getMethod("removeHologram", String.class);
            createHologram = dhapiClass.getMethod("createHologram", String.class, Location.class, boolean.class, java.util.List.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("DecentHolograms DHAPI is not available.", exception);
        }
    }

    @Override
    public void upsert(ShopRecord shop) {
        Location location = HologramLines.location(plugin, shop);
        if (location == null) {
            return;
        }
        String name = name(shop.id());
        try {
            removeHologram.invoke(null, name);
            createHologram.invoke(null, name, location, false, HologramLines.forShop(text, shop));
            activeShopIds.add(shop.id());
            itemDisplays.upsert(shop);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not update DecentHolograms shop hologram " + shop.id()
                    + ": " + exception.getMessage());
        }
    }

    @Override
    public void remove(long shopId) {
        try {
            removeHologram.invoke(null, name(shopId));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not remove DecentHolograms shop hologram " + shopId
                    + ": " + exception.getMessage());
        } finally {
            activeShopIds.remove(shopId);
            itemDisplays.remove(shopId);
        }
    }

    @Override
    public void clear() {
        for (long shopId : new HashSet<>(activeShopIds)) {
            try {
                removeHologram.invoke(null, name(shopId));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("Could not clear DecentHolograms shop hologram " + shopId
                        + ": " + exception.getMessage());
            }
        }
        activeShopIds.clear();
        itemDisplays.clear();
    }

    private String name(long shopId) {
        return "zyrox_shop_" + shopId;
    }
}
