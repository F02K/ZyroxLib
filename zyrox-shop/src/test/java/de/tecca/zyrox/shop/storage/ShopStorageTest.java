package de.tecca.zyrox.shop.storage;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    void newEmptyShopStartsWithAutoCloseDeadline() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Owner");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 20, 21, 22).location(), owner, item(Material.STICK), 1, 1);

            assertEquals("2026-07-18T12:00:00Z", shop.emptyStockExpiresAt());
        }
    }

    @Test
    void depositClearsEmptyStockDeadline() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Owner");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 23, 24, 25).location(), owner, item(Material.STICK), 1, 1);
            ShopRecord stocked = storage.addStock(shop.id(), 4, owner);

            assertEquals(null, stocked.emptyStockExpiresAt());
            assertEquals(null, stocked.lastEmptyStockWarningAt());
        }
    }

    @Test
    void purchaseThatEmptiesStockSetsDeadline() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Owner");
        Player buyer = player("Buyer");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 26, 27, 28).location(), owner, item(Material.STICK), 2, 1);
            storage.addStock(shop.id(), 2, owner);
            ShopRecord emptied = storage.recordPurchase(shop.id(), buyer, 1, 2, 1);

            assertEquals(0, emptied.stockAmount());
            assertEquals("2026-07-18T12:00:00Z", emptied.emptyStockExpiresAt());
        }
    }

    @Test
    void onlyExpiredEmptyShopsAreReturnedForAutoClose() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T12:00:00Z"));
        Player owner = player("Owner");

        try (ShopStorage storage = storage(database, clock)) {
            storage.open();
            ShopRecord empty = storage.createShop(new TestLocation("world", 29, 30, 31).location(), owner, item(Material.STICK), 1, 1);
            ShopRecord stocked = storage.createShop(new TestLocation("world", 32, 33, 34).location(), owner, item(Material.COBBLESTONE), 1, 1);
            storage.addStock(stocked.id(), 1, owner);
            clock.setInstant(Instant.parse("2026-07-19T12:00:00Z"));

            assertEquals(1, storage.expiredEmptyStockShops().size());
            assertEquals(empty.id(), storage.expiredEmptyStockShops().getFirst().id());
        }
    }

    @Test
    void migratedZeroStockShopGetsFutureDeadline() throws Exception {
        File database = temporaryDirectory.resolve("legacy-shops.db").toFile();
        createLegacyShopDatabase(database);

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord migrated = storage.findById(1).orElseThrow();
            assertEquals(null, migrated.emptyStockExpiresAt());

            assertEquals(1, storage.initializeMissingEmptyStockDeadlines());
            ShopRecord initialized = storage.findById(1).orElseThrow();
            assertEquals("2026-07-18T12:00:00Z", initialized.emptyStockExpiresAt());
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
        return storage(database, Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC));
    }

    private static ShopStorage storage(File database, Clock clock) {
        return new ShopStorage(null, database, new ShopItemCodec() {
            @Override
            public String serialize(org.bukkit.inventory.ItemStack item) {
                return item.getType().name();
            }

            @Override
            public org.bukkit.inventory.ItemStack deserialize(String value) {
                return item(Material.valueOf(value));
            }
        }, clock);
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

    private static void createLegacyShopDatabase(File database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE shops(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        item_data TEXT NOT NULL,
                        bundle_amount INTEGER NOT NULL,
                        price_diamonds INTEGER NOT NULL,
                        stock_amount INTEGER NOT NULL,
                        revenue_diamonds INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        UNIQUE(world_name, x, y, z)
                    )
                    """);
            UUID uuid = UUID.nameUUIDFromBytes("Owner".getBytes());
            statement.executeUpdate("""
                    INSERT INTO shops(world_name, x, y, z, owner_uuid, owner_name, item_data,
                                      bundle_amount, price_diamonds, stock_amount, revenue_diamonds,
                                      created_at, updated_at)
                    VALUES('world', 40, 41, 42, '%s', 'Owner', 'STICK', 1, 1, 0, 0,
                           '2026-07-13T12:00:00Z', '2026-07-13T12:00:00Z')
                    """.formatted(uuid));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
