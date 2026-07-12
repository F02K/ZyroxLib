package de.tecca.zyrox.villagershop.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemStacks {

    private ItemStacks() {
    }

    public static ItemStack tradeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("Trade item cannot be air.");
        }
        ItemStack copy = item.clone();
        copy.setAmount(Math.max(1, Math.min(copy.getAmount(), copy.getMaxStackSize())));
        return copy;
    }

    public static ItemStack displayItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new ItemStack(Material.BARRIER);
        }
        ItemStack copy = item.clone();
        copy.setAmount(Math.max(1, copy.getAmount()));
        return copy;
    }

    public static String serialize(ItemStack item) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(tradeItem(item));
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
            return tradeItem(item);
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Could not deserialize item stack.", exception);
        }
    }

    public static String displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Leer";
        }
        String base;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            base = item.getItemMeta().getDisplayName();
        } else {
            base = item.getType().name().toLowerCase().replace('_', ' ');
        }
        return item.getAmount() + "x " + base;
    }
}

