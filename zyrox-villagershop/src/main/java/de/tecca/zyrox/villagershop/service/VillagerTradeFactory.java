package de.tecca.zyrox.villagershop.service;

import de.tecca.zyrox.villagershop.storage.VillagerTradeRecord;
import de.tecca.zyrox.villagershop.util.ItemStacks;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

public final class VillagerTradeFactory {

    private VillagerTradeFactory() {
    }

    public static MerchantRecipe createRecipe(VillagerTradeRecord trade) {
        ItemStack inputOne = ItemStacks.tradeItem(trade.inputOne());
        ItemStack output = ItemStacks.tradeItem(trade.output());
        int maxUses = trade.effectiveMaxUses();
        int uses = trade.unlimited() ? 0 : trade.clampedUses();

        MerchantRecipe recipe = new MerchantRecipe(output, maxUses);
        recipe.setMaxUses(maxUses);
        recipe.setUses(uses);
        recipe.setExperienceReward(false);
        recipe.setVillagerExperience(0);
        recipe.setDemand(0);
        recipe.setSpecialPrice(0);
        recipe.setPriceMultiplier(0.0F);
        recipe.setIgnoreDiscounts(true);

        List<ItemStack> ingredients = new ArrayList<>();
        ingredients.add(inputOne);
        if (trade.hasSecondInput()) {
            ingredients.add(ItemStacks.tradeItem(trade.inputTwo()));
        }
        recipe.setIngredients(ingredients);
        return recipe;
    }
}
