package de.tecca.zyrox.villagershop.gui;

import de.tecca.zyrox.villagershop.storage.VillagerShopRecord;
import de.tecca.zyrox.villagershop.storage.VillagerTradeRecord;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class VillagerShopEditorHolder implements InventoryHolder {

    private VillagerShopRecord shop;
    private int selectedIndex;
    private ItemStack inputOne;
    private ItemStack inputTwo;
    private ItemStack output;
    private String profession;
    private int maxUses;
    private int currentUses;
    private boolean unlimited;

    public VillagerShopEditorHolder(VillagerShopRecord shop) {
        update(shop, 0);
    }

    public VillagerShopRecord shop() {
        return shop;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public VillagerTradeRecord selectedTrade() {
        return shop.tradeAt(selectedIndex);
    }

    public void selectPrevious() {
        update(shop, selectedIndex - 1);
    }

    public void selectNext() {
        update(shop, selectedIndex + 1);
    }

    public ItemStack inputOne() {
        return inputOne;
    }

    public void inputOne(ItemStack inputOne) {
        this.inputOne = inputOne;
    }

    public ItemStack inputTwo() {
        return inputTwo;
    }

    public void inputTwo(ItemStack inputTwo) {
        this.inputTwo = inputTwo;
    }

    public ItemStack output() {
        return output;
    }

    public void output(ItemStack output) {
        this.output = output;
    }

    public String profession() {
        return profession;
    }

    public void profession(String profession) {
        this.profession = profession;
    }

    public int maxUses() {
        return maxUses;
    }

    public void maxUses(int maxUses) {
        this.maxUses = Math.max(1, maxUses);
    }

    public int currentUses() {
        return currentUses;
    }

    public void currentUses(int currentUses) {
        this.currentUses = Math.max(0, currentUses);
    }

    public boolean unlimited() {
        return unlimited;
    }

    public void unlimited(boolean unlimited) {
        this.unlimited = unlimited;
    }

    public void update(VillagerShopRecord shop) {
        update(shop, selectedIndex);
    }

    public void update(VillagerShopRecord shop, int selectedIndex) {
        this.shop = shop;
        this.selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, shop.trades().size() - 1)));
        VillagerTradeRecord trade = selectedTrade();
        this.inputOne = trade.inputOne();
        this.inputTwo = trade.inputTwo();
        this.output = trade.output();
        this.profession = shop.profession();
        this.maxUses = trade.maxUses();
        this.currentUses = trade.currentUses();
        this.unlimited = trade.unlimited();
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

