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
import java.util.HashMap;
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
        if (isEmpty(target) || !isZyroxBook(book)) {
            return null;
        }

        return combineAnvilResult(target, book, vanillaResult, maxLevel);
    }

    public ItemStack combineAnvilResult(ItemStack first, ItemStack second, ItemStack vanillaResult, int maxLevel) {
        if (isEmpty(first)) {
            return null;
        }

        if (!hasZyroxAnvilInput(first, second)) {
            return sanitizeAnvilResult(first, vanillaResult);
        }

        ItemStack result = vanillaResult == null ? first.clone() : vanillaResult.clone();
        sanitizeAnvilResult(first, result);

        Map<Enchantment, Integer> leftEnchants = sourceEnchants(first);
        Map<Enchantment, Integer> rightEnchants = sourceEnchants(second);
        boolean leftIsTrusted = isZyroxCarrier(first);
        boolean rightIsTrusted = isZyroxCarrier(second);
        boolean combinedAny = false;

        for (Map.Entry<Enchantment, Integer> entry : rightEnchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!canApplyFromAnvilSource(first, result, enchantment)) {
                continue;
            }

            int vanillaMaxLevel = enchantment.getMaxLevel();
            int leftLevel = trustedAnvilLevel(vanillaMaxLevel, leftEnchants.getOrDefault(enchantment, 0), leftIsTrusted, maxLevel);
            int rightLevel = trustedAnvilLevel(vanillaMaxLevel, entry.getValue(), rightIsTrusted, maxLevel);
            if (rightLevel < 1) {
                continue;
            }

            combinedAny = true;
            int currentLevel = resultEnchantLevel(result, enchantment);
            int appliedLevel = combinedAnvilLevel(leftLevel, rightLevel, maxLevel);
            if (appliedLevel <= currentLevel) {
                continue;
            }

            setResultEnchant(result, enchantment, appliedLevel);
        }

        if (!combinedAny && vanillaResult == null) {
            return null;
        }

        markResultIfOverleveled(result);
        return result;
    }

    public ItemStack sanitizeAnvilResult(ItemStack leftInput, ItemStack result) {
        if (isEmpty(result)) {
            return result;
        }

        boolean leftIsZyroxEnchanted = isZyroxCarrier(leftInput);
        Map<Enchantment, Integer> leftEnchants = sourceEnchants(leftInput);
        boolean keptOverlevel = false;

        for (Map.Entry<Enchantment, Integer> entry : sourceEnchants(result).entrySet()) {
            Enchantment enchantment = entry.getKey();
            int resultLevel = entry.getValue();
            int vanillaMax = enchantment.getMaxLevel();
            int allowedLevel = allowedAnvilLevel(vanillaMax, leftEnchants.getOrDefault(enchantment, 0), leftIsZyroxEnchanted);

            if (resultLevel > allowedLevel) {
                setResultEnchant(result, enchantment, allowedLevel);
                resultLevel = allowedLevel;
            }

            if (resultLevel > vanillaMax) {
                keptOverlevel = true;
            }
        }

        if (keptOverlevel) {
            markResultIfOverleveled(result);
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

    private boolean hasZyroxAnvilInput(ItemStack first, ItemStack second) {
        return isZyroxCarrier(first) || isZyroxCarrier(second);
    }

    private boolean isZyroxCarrier(ItemStack item) {
        return isZyroxBook(item) || isZyroxEnchanted(item);
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

    int combinedAnvilLevel(int leftLevel, int rightLevel, int configuredMaxLevel) {
        int combined = leftLevel == rightLevel ? leftLevel + 1 : Math.max(leftLevel, rightLevel);
        return Math.min(combined, configuredMaxLevel);
    }

    int trustedAnvilLevel(int vanillaMaxLevel, int level, boolean trustedSource, int configuredMaxLevel) {
        if (level < 1) {
            return 0;
        }

        int cappedLevel = Math.min(level, configuredMaxLevel);
        if (trustedSource) {
            return cappedLevel;
        }
        return Math.min(cappedLevel, vanillaMaxLevel);
    }

    private boolean canApplyFromAnvilSource(ItemStack target, ItemStack result, Enchantment enchantment) {
        if (!isVanilla(enchantment) || (!isEnchantedBook(target) && !enchantment.canEnchantItem(target))) {
            return false;
        }

        for (Enchantment existing : sourceEnchants(result).keySet()) {
            if (!existing.equals(enchantment) && (existing.conflictsWith(enchantment) || enchantment.conflictsWith(existing))) {
                return false;
            }
        }
        return true;
    }

    private void setResultEnchant(ItemStack result, Enchantment enchantment, int level) {
        if (isEnchantedBook(result) && result.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            if (meta.hasStoredEnchant(enchantment)) {
                meta.removeStoredEnchant(enchantment);
            }
            meta.addStoredEnchant(enchantment, level, true);
            result.setItemMeta(meta);
            return;
        }

        result.removeEnchantment(enchantment);
        result.addUnsafeEnchantment(enchantment, level);
    }

    private int resultEnchantLevel(ItemStack item, Enchantment enchantment) {
        if (isEnchantedBook(item) && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            return meta.getStoredEnchantLevel(enchantment);
        }
        return item.getEnchantmentLevel(enchantment);
    }

    private Map<Enchantment, Integer> sourceEnchants(ItemStack item) {
        if (isEmpty(item)) {
            return Map.of();
        }

        if (isEnchantedBook(item) && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            return new HashMap<>(meta.getStoredEnchants());
        }
        return new HashMap<>(item.getEnchantments());
    }

    private void markResultIfOverleveled(ItemStack item) {
        if (!hasOverleveledEnchant(item)) {
            return;
        }

        if (isEnchantedBook(item) && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            markZyroxBook(meta);
            item.setItemMeta(meta);
            return;
        }

        markZyroxEnchanted(item);
    }

    private boolean hasOverleveledEnchant(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }

        return sourceEnchants(item).entrySet().stream()
                .anyMatch(entry -> entry.getValue() > entry.getKey().getMaxLevel());
    }

    private boolean isEnchantedBook(ItemStack item) {
        return item != null && item.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEmpty(ItemStack item) {
        if (item == null) {
            return true;
        }

        Material type = item.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }
}
