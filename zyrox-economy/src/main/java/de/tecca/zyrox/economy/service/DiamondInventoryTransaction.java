package de.tecca.zyrox.economy.service;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class DiamondInventoryTransaction {

    private DiamondInventoryTransaction() {
    }

    static Result withdraw(Inventory inventory, Material material, int requestedItems, Commit commit) {
        ItemStack[] snapshot = snapshot(inventory);
        int beforeItems = count(snapshot, material);
        if (requestedItems <= 0) {
            return new Result(Status.INVALID_AMOUNT, beforeItems, beforeItems, beforeItems, true, null);
        }
        if (beforeItems < requestedItems) {
            return new Result(Status.INSUFFICIENT_ITEMS, beforeItems, beforeItems, beforeItems, true, null);
        }

        int remaining = requestedItems;
        try {
            for (int slot = 0; slot < snapshot.length && remaining > 0; slot++) {
                ItemStack item = snapshot[slot];
                if (item == null || item.getType() != material) {
                    continue;
                }

                int taken = Math.min(item.getAmount(), remaining);
                int replacementAmount = item.getAmount() - taken;
                ItemStack replacement = null;
                if (replacementAmount > 0) {
                    replacement = item.clone();
                    replacement.setAmount(replacementAmount);
                }
                inventory.setItem(slot, replacement);
                remaining -= taken;
            }
        } catch (RuntimeException exception) {
            int observedAfter = count(inventory.getStorageContents(), material);
            boolean restored = restore(inventory, snapshot, material, beforeItems);
            return new Result(Status.MUTATION_FAILED, beforeItems, observedAfter,
                    count(inventory.getStorageContents(), material), restored, exception);
        }

        int observedAfter = count(inventory.getStorageContents(), material);
        int expectedAfter = beforeItems - requestedItems;
        if (remaining != 0 || observedAfter != expectedAfter) {
            boolean restored = restore(inventory, snapshot, material, beforeItems);
            return new Result(Status.DELTA_MISMATCH, beforeItems, observedAfter,
                    count(inventory.getStorageContents(), material), restored, null);
        }

        try {
            commit.commit();
        } catch (Exception exception) {
            boolean restored = restore(inventory, snapshot, material, beforeItems);
            return new Result(Status.COMMIT_FAILED, beforeItems, observedAfter,
                    count(inventory.getStorageContents(), material), restored, exception);
        }

        return new Result(Status.SUCCESS, beforeItems, observedAfter, observedAfter, true, null);
    }

    static int count(Inventory inventory, Material material) {
        return count(inventory.getStorageContents(), material);
    }

    static int count(ItemStack[] contents, Material material) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static ItemStack[] snapshot(Inventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            snapshot[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return snapshot;
    }

    private static boolean restore(Inventory inventory, ItemStack[] snapshot, Material material, int expectedItems) {
        try {
            for (int slot = 0; slot < snapshot.length; slot++) {
                ItemStack item = snapshot[slot];
                inventory.setItem(slot, item == null ? null : item.clone());
            }
            return count(inventory.getStorageContents(), material) == expectedItems;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @FunctionalInterface
    interface Commit {
        void commit() throws Exception;
    }

    enum Status {
        SUCCESS,
        INVALID_AMOUNT,
        INSUFFICIENT_ITEMS,
        MUTATION_FAILED,
        DELTA_MISMATCH,
        COMMIT_FAILED
    }

    record Result(
            Status status,
            int beforeItems,
            int observedAfterItems,
            int finalItems,
            boolean rollbackVerified,
            Exception cause
    ) {
        boolean success() {
            return status == Status.SUCCESS;
        }
    }
}
