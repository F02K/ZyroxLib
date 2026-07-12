package de.tecca.zyrox.shop.gui;

import de.tecca.zyrox.shop.storage.ShopRecord;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopPurchaseHolder implements InventoryHolder {

    private ShopRecord shop;

    public ShopPurchaseHolder(ShopRecord shop) {
        this.shop = shop;
    }

    public ShopRecord shop() {
        return shop;
    }

    public void update(ShopRecord shop) {
        this.shop = shop;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
