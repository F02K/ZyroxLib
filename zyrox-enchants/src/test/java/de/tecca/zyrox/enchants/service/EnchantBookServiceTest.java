package de.tecca.zyrox.enchants.service;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class EnchantBookServiceTest {

    @Test
    void normalizesVanillaNamesWithOrWithoutNamespace() {
        EnchantBookService service = service();

        assertEquals(Optional.of(NamespacedKey.minecraft("sharpness")), service.normalizeMinecraftKey("sharpness"));
        assertEquals(Optional.of(NamespacedKey.minecraft("sharpness")), service.normalizeMinecraftKey("minecraft:sharpness"));
        assertEquals(Optional.of(NamespacedKey.minecraft("sharpness")), service.normalizeMinecraftKey("MINECRAFT:SHARPNESS"));
    }

    @Test
    void rejectsCustomNamespacesAndMalformedKeys() {
        EnchantBookService service = service();

        assertTrue(service.normalizeMinecraftKey("example:sharpness").isEmpty());
        assertTrue(service.normalizeMinecraftKey("minecraft:").isEmpty());
        assertTrue(service.normalizeMinecraftKey("minecraft:bad key").isEmpty());
    }

    @Test
    void identifiesOnlyMinecraftKeysAsVanilla() {
        EnchantBookService service = service();

        assertTrue(service.isVanillaKey(NamespacedKey.minecraft("mending")));
        assertFalse(service.isVanillaKey(new NamespacedKey("example", "custom_glow")));
    }

    @Test
    void validatesConfiguredLevelBounds() {
        EnchantBookService service = service();

        assertFalse(service.isValidLevel(0, 10));
        assertTrue(service.isValidLevel(1, 10));
        assertTrue(service.isValidLevel(10, 10));
        assertFalse(service.isValidLevel(11, 10));
    }

    @Test
    void appliesStoredEnchantWithLevelRestrictionIgnored() {
        EnchantmentStorageMeta meta = mock(EnchantmentStorageMeta.class);
        when(meta.addStoredEnchant(null, 10, true)).thenReturn(true);
        EnchantBookService service = service();

        assertTrue(service.applyStoredEnchant(meta, null, 10));
        verify(meta).addStoredEnchant(null, 10, true);
    }

    private EnchantBookService service() {
        return new EnchantBookService(key -> null, List::of);
    }
}
