package de.tecca.zyrox.shop.hologram;

import de.tecca.zyrox.shop.storage.ShopRecord;

public final class NoopShopHologramService implements ShopHologramService {

    @Override
    public void upsert(ShopRecord shop) {
    }

    @Override
    public void remove(long shopId) {
    }

    @Override
    public void clear() {
    }
}
