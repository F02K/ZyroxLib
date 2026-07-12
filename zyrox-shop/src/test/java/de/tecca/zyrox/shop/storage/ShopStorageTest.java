package de.tecca.zyrox.shop.storage;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndLoadsShopByLocation() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        TestLocation location = new TestLocation("world", 10, 64, -5);
        Player owner = player("Owner");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(location.location(), owner, item(Material.STICK), 32, 2);
            ShopRecord loaded = storage.findByLocation(location.location()).orElseThrow();

            assertEquals(shop.id(), loaded.id());
            assertEquals(Material.STICK, loaded.itemTemplate().getType());
            assertEquals(32, loaded.bundleAmount());
            assertEquals(2, loaded.priceDiamonds());
        }
    }

    @Test
    void purchaseMovesVirtualStockIntoRevenue() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        TestLocation location = new TestLocation("world", 1, 2, 3);
        Player owner = player("Owner");
        Player buyer = player("Buyer");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(location.location(), owner, item(Material.COBBLESTONE), 16, 3);
            storage.addStock(shop.id(), 64, owner);

            ShopRecord updated = storage.recordPurchase(shop.id(), buyer, 2, 16, 3);

            assertEquals(32, updated.stockAmount());
            assertEquals(6, updated.revenueDiamonds());
        }
    }

    @Test
    void stalePurchaseDoesNotMoveStockOrRevenue() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        TestLocation location = new TestLocation("world", 7, 8, 9);
        Player owner = player("Owner");
        Player buyer = player("Buyer");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(location.location(), owner, item(Material.STICK), 8, 1);
            storage.addStock(shop.id(), 16, owner);
            storage.updateBundle(shop.id(), 8, 2);

            assertThrows(Exception.class, () -> storage.recordPurchase(shop.id(), buyer, 1, 8, 1));
            ShopRecord loaded = storage.findById(shop.id()).orElseThrow();

            assertEquals(16, loaded.stockAmount());
            assertEquals(0, loaded.revenueDiamonds());
        }
    }

    @Test
    void repeatedPurchasesCannotOverdrawStock() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        TestLocation location = new TestLocation("world", 11, 12, 13);
        Player owner = player("Owner");
        Player buyer = player("Buyer");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(location.location(), owner, item(Material.COBBLESTONE), 16, 3);
            storage.addStock(shop.id(), 16, owner);

            storage.recordPurchase(shop.id(), buyer, 1, 16, 3);
            assertThrows(Exception.class, () -> storage.recordPurchase(shop.id(), buyer, 1, 16, 3));
            ShopRecord loaded = storage.findById(shop.id()).orElseThrow();

            assertEquals(0, loaded.stockAmount());
            assertEquals(3, loaded.revenueDiamonds());
        }
    }

    @Test
    void exactWithdrawalsDoNotPayPartialAmounts() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        TestLocation location = new TestLocation("world", 14, 15, 16);
        Player owner = player("Owner");
        Player buyer = player("Buyer");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(location.location(), owner, item(Material.STICK), 1, 1);
            storage.addStock(shop.id(), 1, owner);
            storage.recordPurchase(shop.id(), buyer, 1, 1, 1);

            assertThrows(Exception.class, () -> storage.withdrawStock(shop.id(), 2, owner));
            assertThrows(Exception.class, () -> storage.withdrawRevenue(shop.id(), 2, owner));

            ShopRecord loaded = storage.findById(shop.id()).orElseThrow();
            assertEquals(0, loaded.stockAmount());
            assertEquals(1, loaded.revenueDiamonds());
        }
    }

    @Test
    void nonForcedDeleteRequiresEmptyStockAndRevenue() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        TestLocation location = new TestLocation("world", 4, 5, 6);
        Player owner = player("Owner");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(location.location(), owner, item(Material.STICK), 1, 1);
            storage.addStock(shop.id(), 1, owner);

            assertFalse(storage.deleteShop(shop.id(), owner, false));
            assertTrue(storage.deleteShop(shop.id(), owner, true));
        }
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

    private static ShopStorage storage(File database) {
        return new ShopStorage(null, database, new ShopItemCodec() {
            @Override
            public String serialize(org.bukkit.inventory.ItemStack item) {
                return item.getType().name();
            }

            @Override
            public org.bukkit.inventory.ItemStack deserialize(String value) {
                return item(Material.valueOf(value));
            }
        });
    }

    private static org.bukkit.inventory.ItemStack item(Material material) {
        org.bukkit.inventory.ItemStack item = mock(org.bukkit.inventory.ItemStack.class);
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
