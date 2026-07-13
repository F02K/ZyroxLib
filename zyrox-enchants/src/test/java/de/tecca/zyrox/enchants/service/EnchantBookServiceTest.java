package de.tecca.zyrox.enchants.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class EnchantBookServiceTest {

    private static final NamespacedKey BOOK_MARKER = NamespacedKey.minecraft("zyrox_book_marker");
    private static final NamespacedKey ITEM_MARKER = NamespacedKey.minecraft("zyrox_item_marker");

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

    @Test
    void marksBookMetaAsZyroxBook() {
        EnchantmentStorageMeta meta = mock(EnchantmentStorageMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        EnchantBookService service = service();

        assertTrue(service.markZyroxBook(meta));

        verify(container).set(BOOK_MARKER, PersistentDataType.BYTE, (byte) 1);
    }

    @Test
    void recognizesOnlyMarkedStoredEnchantBooksAsZyroxBooks() {
        EnchantBookService service = service();
        ItemStack book = mock(ItemStack.class);
        EnchantmentStorageMeta meta = mock(EnchantmentStorageMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(book.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(book.getItemMeta()).thenReturn(meta);
        when(meta.hasStoredEnchants()).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(container.has(BOOK_MARKER, PersistentDataType.BYTE)).thenReturn(true);

        assertTrue(service.isZyroxBook(book));

        when(container.has(BOOK_MARKER, PersistentDataType.BYTE)).thenReturn(false);
        assertFalse(service.isZyroxBook(book));

        when(book.getType()).thenReturn(Material.BOOK);
        assertFalse(service.isZyroxBook(book));
    }

    @Test
    void rejectsUnmarkedOverleveledBooks() {
        EnchantBookService service = service();
        ItemStack target = item(Material.DIAMOND_SWORD);
        ItemStack book = zyroxBook(false);

        assertNull(service.applyZyroxBook(target, book, null, 10));

        verify(target, never()).clone();
    }

    @Test
    void nonZyroxAnvilResultsAreLimitedToVanillaMax() {
        EnchantBookService service = service();

        assertEquals(5, service.allowedAnvilLevel(5, 10, false));
    }

    @Test
    void preservesExistingMarkedOverlevelsButDoesNotIncreaseThem() {
        EnchantBookService service = service();

        assertEquals(10, service.allowedAnvilLevel(5, 10, true));
        assertEquals(5, service.allowedAnvilLevel(5, 4, true));
    }

    @Test
    void appliesCompatibleZyroxBookUpToConfiguredMaxLevel() {
        EnchantBookService service = service();

        assertEquals(10, service.cappedBookLevel(12, 10));
        assertEquals(8, service.cappedBookLevel(8, 10));
    }

    @Test
    void normalSecondItemsCannotTransferOverlevels() {
        EnchantBookService service = service();

        assertEquals(5, service.allowedAnvilLevel(5, 6, false));
    }

    private EnchantBookService service() {
        return new EnchantBookService(BOOK_MARKER, ITEM_MARKER, key -> null, List::of);
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    private ItemStack zyroxBook(boolean marked) {
        ItemStack book = mock(ItemStack.class);
        EnchantmentStorageMeta meta = mock(EnchantmentStorageMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(book.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(book.getItemMeta()).thenReturn(meta);
        when(meta.hasStoredEnchants()).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(container.has(BOOK_MARKER, PersistentDataType.BYTE)).thenReturn(marked);
        return book;
    }
}
