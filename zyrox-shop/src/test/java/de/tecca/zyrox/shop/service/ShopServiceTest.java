package de.tecca.zyrox.shop.service;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.hologram.NoopShopHologramService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static ShopService service(ShopStorage storage) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.own-shop-buy", "Du kannst nicht bei deinem eigenen Shop einkaufen.");
        when(plugin.getConfig()).thenReturn(config);
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
        return new ShopService(plugin, storage, payments, new NoopShopHologramService(), new ShopText(plugin, payments));
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
        });
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
