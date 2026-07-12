package de.tecca.zyrox.enchants.service;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EnchantBookService {

    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String KEY_PATTERN = "[a-z0-9._/-]+";

    private final Function<NamespacedKey, Enchantment> enchantmentResolver;
    private final Supplier<Collection<Enchantment>> enchantmentSupplier;

    public EnchantBookService() {
        this(Registry.ENCHANTMENT::get, () -> Registry.ENCHANTMENT.stream().toList());
    }

    EnchantBookService(
            Function<NamespacedKey, Enchantment> enchantmentResolver,
            Supplier<Collection<Enchantment>> enchantmentSupplier
    ) {
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
            book.setItemMeta(meta);
        }
        return book;
    }

    boolean applyStoredEnchant(EnchantmentStorageMeta meta, Enchantment enchantment, int level) {
        return meta.addStoredEnchant(enchantment, level, true);
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
}
