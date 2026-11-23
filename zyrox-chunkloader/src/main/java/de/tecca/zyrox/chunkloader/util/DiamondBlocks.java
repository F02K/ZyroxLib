package de.tecca.zyrox.chunkloader.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class DiamondBlocks {

    private DiamondBlocks() {
    }

    public static long count(Inventory inventory) {
        long count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == Material.DIAMOND_BLOCK) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static boolean withdraw(Inventory inventory, long amount) {
        if (amount <= 0 || amount > Integer.MAX_VALUE || count(inventory) < amount) {
            return false;
        }
        ItemStack[] snapshot = snapshot(inventory);
        long remaining = amount;
        try {
            for (int slot = 0; slot < snapshot.length && remaining > 0; slot++) {
                ItemStack item = snapshot[slot];
                if (item == null || item.getType() != Material.DIAMOND_BLOCK) {
                    continue;
                }
                int taken = (int) Math.min(item.getAmount(), remaining);
                int replacementAmount = item.getAmount() - taken;
                ItemStack replacement = null;
                if (replacementAmount > 0) {
                    replacement = item.clone();
                    replacement.setAmount(replacementAmount);
                }
                inventory.setItem(slot, replacement);
                remaining -= taken;
            }
            if (remaining == 0) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Restore below.
        }
        restore(inventory, snapshot);
        return false;
    }

    public static void give(Inventory inventory, long amount) {
        long remaining = Math.max(0, amount);
        while (remaining > 0) {
            int stack = (int) Math.min(64, remaining);
            inventory.addItem(new ItemStack(Material.DIAMOND_BLOCK, stack));
            remaining -= stack;
        }
    }

    private static ItemStack[] snapshot(Inventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            snapshot[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return snapshot;
    }

    private static void restore(Inventory inventory, ItemStack[] snapshot) {
        for (int slot = 0; slot < snapshot.length; slot++) {
            inventory.setItem(slot, snapshot[slot] == null ? null : snapshot[slot].clone());
        }
    }
}
