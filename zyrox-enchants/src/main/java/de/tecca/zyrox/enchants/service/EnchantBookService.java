package de.tecca.zyrox.enchants.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EnchantBookService {

    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String KEY_PATTERN = "[a-z0-9._/-]+";

    private final NamespacedKey bookMarkerKey;
    private final NamespacedKey enchantedItemMarkerKey;
    private final Function<NamespacedKey, Enchantment> enchantmentResolver;
    private final Supplier<Collection<Enchantment>> enchantmentSupplier;

    public EnchantBookService(JavaPlugin plugin) {
        this(
                new NamespacedKey(plugin, "zyrox_book"),
                new NamespacedKey(plugin, "zyrox_enchanted"),
                Registry.ENCHANTMENT::get,
                () -> Registry.ENCHANTMENT.stream().toList()
        );
    }

    EnchantBookService(
            NamespacedKey bookMarkerKey,
            NamespacedKey enchantedItemMarkerKey,
            Function<NamespacedKey, Enchantment> enchantmentResolver,
            Supplier<Collection<Enchantment>> enchantmentSupplier
    ) {
        this.bookMarkerKey = bookMarkerKey;
        this.enchantedItemMarkerKey = enchantedItemMarkerKey;
        this.enchantmentResolver = enchantmentResolver;
        this.enchantmentSupplier = enchantmentSupplier;
    }

    public Optional<Enchantment> resolveVanilla(String input) {
        Optional<NamespacedKey> key = normalizeMinecraftKey(input);
        if (key.isEmpty()) {
            return Optional.empty();
        }

        Enchantment enchantment = enchantmentResolver.apply(key.get());
        if (!isVanilla(enchantment)) {
            return Optional.empty();
        }
        return Optional.of(enchantment);
    }

    public boolean isValidLevel(int level, int maxLevel) {
        return level >= 1 && level <= maxLevel;
    }

    public ItemStack createBook(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            applyStoredEnchant(meta, enchantment, level);
            markZyroxBook(meta);
            book.setItemMeta(meta);
        }
        return book;
    }

    boolean applyStoredEnchant(EnchantmentStorageMeta meta, Enchantment enchantment, int level) {
        return meta.addStoredEnchant(enchantment, level, true);
    }

    boolean markZyroxBook(EnchantmentStorageMeta meta) {
        meta.getPersistentDataContainer().set(bookMarkerKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    public boolean isZyroxBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta) || !meta.hasStoredEnchants()) {
            return false;
        }
        return meta.getPersistentDataContainer().has(bookMarkerKey, PersistentDataType.BYTE);
    }

    public ItemStack applyZyroxBook(ItemStack target, ItemStack book, ItemStack vanillaResult, int maxLevel) {
        if (isEmpty(target) || !isZyroxBook(book) || !(book.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return null;
        }

        ItemStack result = vanillaResult == null ? target.clone() : vanillaResult.clone();
        sanitizeAnvilResult(target, result);

        boolean appliedAny = false;
        boolean appliedOverlevel = false;
        for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!canApplyFromZyroxBook(target, result, enchantment)) {
                continue;
            }

            int requestedLevel = cappedBookLevel(entry.getValue(), maxLevel);
            if (requestedLevel < 1) {
                continue;
            }

            int currentLevel = result.getEnchantmentLevel(enchantment);
            int appliedLevel = Math.max(currentLevel, requestedLevel);
            if (appliedLevel <= currentLevel) {
                continue;
            }

            appliedAny = true;
            result.addUnsafeEnchantment(enchantment, appliedLevel);
            if (appliedLevel > enchantment.getMaxLevel()) {
                appliedOverlevel = true;
            }
        }

        if (!appliedAny && vanillaResult == null) {
            return null;
        }

        if (appliedOverlevel || hasOverleveledEnchant(result)) {
            markZyroxEnchanted(result);
        }
        return result;
    }

    public ItemStack sanitizeAnvilResult(ItemStack leftInput, ItemStack result) {
        if (isEmpty(result)) {
            return result;
        }

        boolean leftIsZyroxEnchanted = isZyroxEnchanted(leftInput);
        Map<Enchantment, Integer> leftEnchants = isEmpty(leftInput) ? Map.of() : leftInput.getEnchantments();
        boolean keptOverlevel = false;

        for (Map.Entry<Enchantment, Integer> entry : result.getEnchantments().entrySet()) {
            Enchantment enchantment = entry.getKey();
            int resultLevel = entry.getValue();
            int vanillaMax = enchantment.getMaxLevel();
            int allowedLevel = allowedAnvilLevel(vanillaMax, leftEnchants.getOrDefault(enchantment, 0), leftIsZyroxEnchanted);

            if (resultLevel > allowedLevel) {
                result.removeEnchantment(enchantment);
                result.addUnsafeEnchantment(enchantment, allowedLevel);
                resultLevel = allowedLevel;
            }

            if (resultLevel > vanillaMax) {
                keptOverlevel = true;
            }
        }

        if (keptOverlevel) {
            markZyroxEnchanted(result);
        }
        return result;
    }

    public void markZyroxEnchanted(ItemStack item) {
        if (isEmpty(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(enchantedItemMarkerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    public List<String> vanillaSuggestions() {
        return enchantmentSupplier.get().stream()
                .filter(this::isVanilla)
                .map(enchantment -> enchantment.getKey().getKey())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public String displayKey(Enchantment enchantment) {
        return enchantment.getKey().asString();
    }

    Optional<NamespacedKey> normalizeMinecraftKey(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String raw = input.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            return Optional.empty();
        }

        String namespace = MINECRAFT_NAMESPACE;
        String key = raw;
        int separator = raw.indexOf(':');
        if (separator >= 0) {
            namespace = raw.substring(0, separator);
            key = raw.substring(separator + 1);
        }

        if (!MINECRAFT_NAMESPACE.equals(namespace) || key.isBlank() || !key.matches(KEY_PATTERN)) {
            return Optional.empty();
        }

        return Optional.of(NamespacedKey.minecraft(key));
    }

    boolean isVanillaKey(NamespacedKey key) {
        return key != null && MINECRAFT_NAMESPACE.equals(key.getNamespace());
    }

    private boolean isVanilla(Enchantment enchantment) {
        return enchantment != null && isVanillaKey(enchantment.getKey());
    }

    private boolean isZyroxEnchanted(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(enchantedItemMarkerKey, PersistentDataType.BYTE);
    }

    int allowedAnvilLevel(int vanillaMax, int leftLevel, boolean leftIsZyroxEnchanted) {
        if (leftIsZyroxEnchanted && leftLevel > vanillaMax) {
            return leftLevel;
        }
        return vanillaMax;
    }

    int cappedBookLevel(int bookLevel, int configuredMaxLevel) {
        return Math.min(bookLevel, configuredMaxLevel);
    }

    private boolean canApplyFromZyroxBook(ItemStack target, ItemStack result, Enchantment enchantment) {
        if (!isVanilla(enchantment) || !enchantment.canEnchantItem(target)) {
            return false;
        }

        for (Enchantment existing : result.getEnchantments().keySet()) {
            if (!existing.equals(enchantment) && (existing.conflictsWith(enchantment) || enchantment.conflictsWith(existing))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOverleveledEnchant(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }

        return item.getEnchantments().entrySet().stream()
                .anyMatch(entry -> entry.getValue() > entry.getKey().getMaxLevel());
    }

    private boolean isEmpty(ItemStack item) {
        if (item == null) {
            return true;
        }

        Material type = item.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }
}
