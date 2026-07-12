package de.tecca.zyrox.villagershop.service;

import de.tecca.zyrox.villagershop.storage.VillagerTradeRecord;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VillagerTradeFactoryTest {

    @Test
    void recordExposesLimitedRecipeUseState() {
        VillagerTradeRecord trade = record(item(Material.EMERALD, 3), item(Material.STONE, 8), item(Material.BRICKS, 16), 12, 4, false);

        assertEquals(12, trade.effectiveMaxUses());
        assertEquals(4, trade.clampedUses());
    }

    @Test
    void recordExposesUnlimitedRecipeUseState() {
        VillagerTradeRecord trade = record(item(Material.DIAMOND, 1), null, item(Material.NETHERITE_INGOT, 1), 2, 2, true);

        assertEquals(Integer.MAX_VALUE, trade.effectiveMaxUses());
        assertEquals(0, trade.clampedUses());
    }

    private static VillagerTradeRecord record(ItemStack inputOne, ItemStack inputTwo, ItemStack output, int maxUses, int currentUses, boolean unlimited) {
        return new VillagerTradeRecord(
                1,
                1,
                0,
                inputOne,
                inputTwo,
                output,
                maxUses,
                currentUses,
                unlimited,
                "now",
                "now"
        );
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
