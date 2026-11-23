package de.tecca.zyrox.chunkloader.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class ChunkloaderItems {

    private final JavaPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;

    public ChunkloaderItems(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "chunkloader");
        this.recipeKey = new NamespacedKey(plugin, "chunkloader_recipe");
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    public Material blockMaterial() {
        String materialName = plugin.getConfig().getString("chunkloader.block-material", "VAULT");
        Material material = Material.matchMaterial(materialName == null ? "VAULT" : materialName);
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Invalid chunkloader.block-material '" + materialName + "'. Falling back to VAULT.");
            return Material.VAULT;
        }
        return material;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(blockMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Zyrox Chunkloader");
            List<String> lore = new ArrayList<>();
            lore.add("Laedt den Chunk, in dem er steht.");
            lore.add("Betrieb: Diamantbloecke als Fuel.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isChunkloaderItem(ItemStack item) {
        if (item == null || item.getType() != blockMaterial()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem(1));
        recipe.shape("NDN", "DCD", "NDN");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('C', Material.CONDUIT);
        return recipe;
    }
}
