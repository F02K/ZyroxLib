package de.tecca.zyrox.shop.util;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ItemStacksTest {

    @Test
    void formatsReadableEnchantmentNamesAndLevels() {
        assertEquals("Sharpness V", ItemStacks.formatEnchantment("sharpness", 5));
        assertEquals("Fire Aspect II", ItemStacks.formatEnchantment("fire_aspect", 2));
        assertEquals("Custom Power 12", ItemStacks.formatEnchantment("custom_power", 12));
    }

    @Test
    void returnsNoLinesForPlainItems() {
        ItemStack item = mock(ItemStack.class);

        assertTrue(ItemStacks.enchantmentLines(item).isEmpty());
        assertEquals("", ItemStacks.enchantmentSummary(item));
    }
}
