package de.tecca.zyrox.shop.service;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.hologram.NoopShopHologramService;
import de.tecca.zyrox.shop.hologram.ShopHologramService;
import de.tecca.zyrox.shop.storage.ShopItemCodec;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopStorage;
import de.tecca.zyrox.shop.storage.TestLocation;
import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void ownerCannotPurchaseOwnShop() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        Player owner = player("Owner");

        try (ShopStorage storage = storage(database)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 1, 2, 3).location(), owner, item(Material.STICK), 1, 1);
            storage.addStock(shop.id(), 10, owner);
            ShopService service = service(storage);

            ShopActionResult result = service.purchase(shop, owner, 1);
            ShopRecord loaded = storage.findById(shop.id()).orElseThrow();

            assertFalse(result.success());
            assertEquals(10, loaded.stockAmount());
            assertEquals(0, loaded.revenueDiamonds());
        }
    }

    @Test
    void maintenanceAutoClosesExpiredEmptyShopAndRemovesHologram() throws Exception {
        File database = temporaryDirectory.resolve("shops.db").toFile();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T12:00:00Z"));
        Player owner = player("Owner");
        RecordingHolograms holograms = new RecordingHolograms();

        try (ShopStorage storage = storage(database, clock)) {
            storage.open();
            ShopRecord shop = storage.createShop(new TestLocation("world", 4, 5, 6).location(), owner, item(Material.STICK), 1, 1);
            clock.setInstant(Instant.parse("2026-07-19T12:00:00Z"));
            ShopService service = service(storage, holograms, clock);

            service.performEmptyStockMaintenance();

            assertTrue(storage.findById(shop.id()).isEmpty());
            assertTrue(holograms.removed.contains(shop.id()));
        }
    }

    private static ShopService service(ShopStorage storage) {
        return service(storage, new NoopShopHologramService(), Clock.systemUTC());
    }

    private static ShopService service(ShopStorage storage, ShopHologramService holograms, Clock clock) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.own-shop-buy", "Du kannst nicht bei deinem eigenen Shop einkaufen.");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ShopServiceTest"));
        ZyroxDiamondPaymentService payments = new ZyroxDiamondPaymentService() {
            @Override
            public Material currencyMaterial() {
                return Material.DIAMOND;
            }

            @Override
            public long countDiamonds(Player player) {
                return 0;
            }

            @Override
            public boolean hasDiamonds(Player player, long diamonds) {
                return true;
            }

            @Override
            public PaymentResult withdrawDiamonds(Player player, long diamonds, DiamondCommit commit) {
                return PaymentResult.success(diamonds, 0);
            }

            @Override
            public PaymentResult giveDiamonds(Player player, long diamonds) {
                return PaymentResult.success(diamonds, 0);
            }

            @Override
            public String formatDiamonds(long diamonds) {
                return diamonds + " Diamant(en)";
            }
        };
        return new ShopService(plugin, storage, payments, holograms, new ShopText(plugin, payments), clock);
    }

    private static ShopStorage storage(File database) {
        return storage(database, Clock.systemUTC());
    }

    private static ShopStorage storage(File database, Clock clock) {
        return new ShopStorage(null, database, new ShopItemCodec() {
            @Override
            public String serialize(ItemStack item) {
                return item.getType().name();
            }

            @Override
            public ItemStack deserialize(String value) {
                return item(Material.valueOf(value));
            }
        }, clock);
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

    private static final class RecordingHolograms implements ShopHologramService {

        private final Set<Long> removed = new HashSet<>();

        @Override
        public void upsert(ShopRecord shop) {
        }

        @Override
        public void remove(long shopId) {
            removed.add(shopId);
        }

        @Override
        public void clear() {
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
