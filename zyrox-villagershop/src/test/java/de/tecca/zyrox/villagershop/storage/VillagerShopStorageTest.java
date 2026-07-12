package de.tecca.zyrox.villagershop.storage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerShopStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndLoadsVillagerShopWithInitialTrade() throws Exception {
        File database = temporaryDirectory.resolve("villager-shops.db").toFile();
        UUID entityUuid = UUID.randomUUID();

        try (VillagerShopStorage storage = storage(database)) {
            storage.open();
            VillagerShopRecord created = storage.createShop(
                    entityUuid,
                    location("world", 10.5, 64.0, -3.25),
                    "Builder",
                    "MASON",
                    item(Material.EMERALD, 3),
                    item(Material.STONE, 8),
                    item(Material.BRICKS, 16),
                    12,
                    false
            );

            VillagerShopRecord loaded = storage.findById(created.id()).orElseThrow();

            assertEquals(entityUuid, loaded.entityUuid());
            assertEquals("world", loaded.worldName());
            assertEquals("Builder", loaded.displayName());
            assertEquals("Builder", loaded.displayText());
            assertEquals("MASON", loaded.profession());
            assertEquals(1, loaded.trades().size());
            VillagerTradeRecord trade = loaded.trades().get(0);
            assertEquals(Material.EMERALD, trade.inputOne().getType());
            assertEquals(3, trade.inputOne().getAmount());
            assertEquals(Material.STONE, trade.inputTwo().getType());
            assertEquals(8, trade.inputTwo().getAmount());
            assertEquals(Material.BRICKS, trade.output().getType());
            assertEquals(16, trade.output().getAmount());
            assertEquals(12, trade.maxUses());
            assertFalse(trade.unlimited());
        }
    }

    @Test
    void addsUpdatesAndDeletesTrades() throws Exception {
        File database = temporaryDirectory.resolve("villager-shops.db").toFile();

        try (VillagerShopStorage storage = storage(database)) {
            storage.open();
            VillagerShopRecord shop = storage.createShop(
                    UUID.randomUUID(),
                    location("world", 1, 2, 3),
                    "Trader",
                    "NONE",
                    item(Material.EMERALD, 1),
                    null,
                    item(Material.BREAD, 1),
                    16,
                    true
            );

            VillagerShopRecord withSecond = storage.addTrade(shop.id(), item(Material.DIAMOND, 2), null,
                    item(Material.GOLDEN_CARROT, 4), 5, false);
            VillagerTradeRecord second = withSecond.trades().get(1);
            VillagerShopRecord updated = storage.updateTrade(second.id(), item(Material.DIAMOND, 3), item(Material.APPLE, 1),
                    item(Material.GOLDEN_CARROT, 8), 9, 2, false);
            VillagerShopRecord used = storage.updateTradeUses(second.id(), 4);
            VillagerShopRecord removed = storage.deleteTrade(second.id());

            assertEquals(2, withSecond.trades().size());
            assertEquals(Material.DIAMOND, updated.trades().get(1).inputOne().getType());
            assertEquals(3, updated.trades().get(1).inputOne().getAmount());
            assertEquals(Material.APPLE, updated.trades().get(1).inputTwo().getType());
            assertEquals(9, updated.trades().get(1).maxUses());
            assertEquals(4, used.trades().get(1).currentUses());
            assertEquals(1, removed.trades().size());
        }
    }

    @Test
    void updatesIdentityFields() throws Exception {
        File database = temporaryDirectory.resolve("villager-shops.db").toFile();

        try (VillagerShopStorage storage = storage(database)) {
            storage.open();
            VillagerShopRecord shop = storage.createShop(
                    UUID.randomUUID(),
                    location("world", 1, 2, 3),
                    "Trader",
                    "NONE",
                    item(Material.EMERALD, 1),
                    null,
                    item(Material.BREAD, 1),
                    16,
                    true
            );

            VillagerShopRecord profession = storage.updateProfession(shop.id(), "FARMER");
            VillagerShopRecord moved = storage.updateLocation(shop.id(), location("world_nether", 8, 9, 10));
            VillagerShopRecord renamed = storage.updateName(shop.id(), "Food");
            VillagerShopRecord display = storage.updateDisplayText(shop.id(), "Food Dealer");

            assertEquals("FARMER", profession.profession());
            assertEquals("world_nether", moved.worldName());
            assertEquals("Food", renamed.displayName());
            assertEquals("Food Dealer", display.displayText());
        }
    }

    @Test
    void deletesById() throws Exception {
        File database = temporaryDirectory.resolve("villager-shops.db").toFile();

        try (VillagerShopStorage storage = storage(database)) {
            storage.open();
            VillagerShopRecord shop = storage.createShop(
                    UUID.randomUUID(),
                    location("world", 1, 2, 3),
                    "Trader",
                    "NONE",
                    item(Material.EMERALD, 1),
                    null,
                    item(Material.BREAD, 1),
                    16,
                    true
            );

            assertTrue(storage.deleteShop(shop.id()));
            assertTrue(storage.findById(shop.id()).isEmpty());
        }
    }

    private static VillagerShopStorage storage(File database) {
        return new VillagerShopStorage(database, new VillagerShopItemCodec() {
            @Override
            public String serialize(ItemStack item) {
                return item.getType().name() + ":" + item.getAmount();
            }

            @Override
            public ItemStack deserialize(String value) {
                String[] parts = value.split(":");
                return item(Material.valueOf(parts[0]), Integer.parseInt(parts[1]));
            }
        });
    }

    private static Location location(String worldName, double x, double y, double z) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        return new Location(world, x, y, z, 90.0F, 0.0F);
    }

    private static ItemStack item(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.clone()).thenReturn(item);
        return item;
    }
}
