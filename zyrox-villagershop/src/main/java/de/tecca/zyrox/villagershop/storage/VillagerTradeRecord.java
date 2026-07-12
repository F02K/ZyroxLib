package de.tecca.zyrox.villagershop.storage;

import org.bukkit.inventory.ItemStack;

public record VillagerTradeRecord(
        long id,
        long shopId,
        int slot,
        ItemStack inputOne,
        ItemStack inputTwo,
        ItemStack output,
        int maxUses,
        int currentUses,
        boolean unlimited,
        String createdAt,
        String updatedAt
) {

    public boolean hasSecondInput() {
        return inputTwo != null && !inputTwo.getType().isAir();
    }

    public int effectiveMaxUses() {
        return unlimited ? Integer.MAX_VALUE : Math.max(1, maxUses);
    }

    public int clampedUses() {
        if (unlimited) {
            return 0;
        }
        return Math.max(0, Math.min(currentUses, effectiveMaxUses()));
    }
}

