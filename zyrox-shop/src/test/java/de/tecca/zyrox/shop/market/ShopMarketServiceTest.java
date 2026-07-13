package de.tecca.zyrox.shop.market;

import de.tecca.zyrox.shop.api.ZyroxMarketShopEventType;
import de.tecca.zyrox.shop.storage.ShopItemCodec;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopStorage;
import de.tecca.zyrox.shop.storage.TestLocation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ShopMarketServiceTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void searchFiltersByItemOwnerAndStock() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Seller");
        Player other = player("Other");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord stocked = storage.createShop(new TestLocation("world", 1, 2, 3).location(), owner, item(Material.STICK), 16, 2);
            storage.addStock(stocked.id(), 64, owner);
            storage.createShop(new TestLocation("world", 4, 5, 6).location(), other, item(Material.COBBLESTONE), 32, 1);

            ShopMarketService market = new ShopMarketService(storage);

            assertEquals(1, market.search("stick", "sell", true).size());
            assertEquals(0, market.search("cobble", "", true).size());
            assertEquals(1, market.search("cobble", "", false).size());
        }
    }

    @Test
    void revenueShopsOnlyReturnsShopsWithDiamondsWaiting() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Seller");
        Player buyer = player("Buyer");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 1, 2, 3).location(), owner, item(Material.STICK), 16, 2);
            storage.addStock(shop.id(), 32, owner);
            storage.recordPurchase(shop.id(), buyer, 1, 16, 2);

            ShopMarketService market = new ShopMarketService(storage);

            assertEquals(shop.id(), market.shopsWithRevenue().getFirst().id());
        }
    }

    @Test
    void publishNotifiesRegisteredListeners() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Seller");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 1, 2, 3).location(), owner, item(Material.STICK), 16, 2);
            ShopMarketService market = new ShopMarketService(storage);
            List<Long> seen = new ArrayList<>();
            market.registerListener(event -> {
                if (event.type() == ZyroxMarketShopEventType.CREATED) {
                    seen.add(event.shop().id());
                }
            });

            market.publish(ZyroxMarketShopEventType.CREATED, shop);

            assertTrue(seen.contains(shop.id()));
        }
    }

    private static ShopStorage storage(File database) {
        return new ShopStorage(null, database, new ShopItemCodec() {
            @Override
            public String serialize(ItemStack item) {
                return item.getType().name();
            }

            @Override
            public ItemStack deserialize(String value) {
                return item(Material.valueOf(value));
            }
        }, Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC));
    }

    private static Player player(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "toString" -> name;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
