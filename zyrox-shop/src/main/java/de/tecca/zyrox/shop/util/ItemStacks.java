package de.tecca.zyrox.shop.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

public final class ItemStacks {

    private ItemStacks() {
    }

    public static ItemStack template(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("Shop item cannot be air.");
        }
        ItemStack template = item.clone();
        template.setAmount(1);
        return template;
    }

    public static String serialize(ItemStack item) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(template(item));
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not serialize item stack.", exception);
        }
    }

    public static ItemStack deserialize(String value) {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(value));
             BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
            Object object = input.readObject();
            if (!(object instanceof ItemStack item)) {
                throw new IllegalArgumentException("Serialized value is not an item stack.");
            }
            return template(item);
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Could not deserialize item stack.", exception);
        }
    }

    public static boolean matchesTemplate(ItemStack item, ItemStack template) {
        if (item == null || template == null || item.getType().isAir()) {
            return false;
        }
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized.isSimilar(template);
    }

    public static long countMatching(Inventory inventory, ItemStack template) {
        long count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (matchesTemplate(item, template)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static long removeMatching(Inventory inventory, ItemStack template, long requested) {
        long remaining = requested;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!matchesTemplate(item, template)) {
                continue;
            }
            int taken = (int) Math.min(item.getAmount(), remaining);
            if (item.getAmount() == taken) {
                inventory.setItem(slot, null);
            } else {
                ItemStack replacement = item.clone();
                replacement.setAmount(item.getAmount() - taken);
                inventory.setItem(slot, replacement);
            }
            remaining -= taken;
        }
        return requested - remaining;
    }

    public static int freeCapacity(Inventory inventory, ItemStack template) {
        int capacity = 0;
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                capacity += maxStackSize;
            } else if (matchesTemplate(item, template)) {
                capacity += Math.max(0, maxStackSize - item.getAmount());
            }
        }
        return capacity;
    }

    public static boolean give(Inventory inventory, ItemStack template, long amount) {
        if (amount < 0 || amount > Integer.MAX_VALUE) {
            return false;
        }
        if (freeCapacity(inventory, template) < amount) {
            return false;
        }
        ItemStack item = template.clone();
        item.setAmount(Math.toIntExact(amount));
        Map<Integer, ItemStack> leftovers = inventory.addItem(item);
        return leftovers.isEmpty();
    }

    public static String displayName(ItemStack item) {
        if (item == null) {
            return "Unset item";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }
}
