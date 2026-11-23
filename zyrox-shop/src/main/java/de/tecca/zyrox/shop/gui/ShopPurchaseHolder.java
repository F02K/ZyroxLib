package de.tecca.zyrox.shop.gui;

import de.tecca.zyrox.shop.storage.ShopRecord;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopPurchaseHolder implements InventoryHolder {

    public enum View {
        PURCHASE,
        RATING,
        FAVORITES
    }

    private ShopRecord shop;
    private long expectedEffectivePriceDiamonds;
    private View view = View.PURCHASE;
    private int favoritesPage = 1;
    private final Map<Integer, ShopRecord> favoriteSlots = new HashMap<>();

    public ShopPurchaseHolder(ShopRecord shop) {
        this.shop = shop;
        this.expectedEffectivePriceDiamonds = shop.effectivePriceDiamonds(Clock.systemUTC());
    }

    public ShopRecord shop() {
        return shop;
    }

    public long expectedEffectivePriceDiamonds() {
        return expectedEffectivePriceDiamonds;
    }

    public View view() {
        return view;
    }

    public void view(View view) {
        this.view = view;
    }

    public int favoritesPage() {
        return favoritesPage;
    }

    public void favoritesPage(int favoritesPage) {
        this.favoritesPage = Math.max(1, favoritesPage);
    }

    public void favoriteSlots(List<ShopRecord> favorites, int[] slots) {
        favoriteSlots.clear();
        for (int index = 0; index < favorites.size() && index < slots.length; index++) {
            favoriteSlots.put(slots[index], favorites.get(index));
        }
    }

    public ShopRecord favoriteAt(int slot) {
        return favoriteSlots.get(slot);
    }

    public void update(ShopRecord shop) {
        this.shop = shop;
        this.expectedEffectivePriceDiamonds = shop.effectivePriceDiamonds(Clock.systemUTC());
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
