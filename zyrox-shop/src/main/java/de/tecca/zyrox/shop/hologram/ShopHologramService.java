package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.storage.ShopRecord;

public interface ShopHologramService {

    void upsert(ShopRecord shop);

    void remove(long shopId);

    void clear();
}
