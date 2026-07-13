package de.tecca.zyrox.shop.market;

import de.tecca.zyrox.shop.api.ZyroxMarketShop;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEvent;
import de.tecca.zyrox.shop.api.ZyroxMarketShopEventType;
import de.tecca.zyrox.shop.api.ZyroxShopMarketListener;
import de.tecca.zyrox.shop.api.ZyroxShopMarketService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopStorage;
import de.tecca.zyrox.shop.util.ItemStacks;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ShopMarketService implements ZyroxShopMarketService {

    private final ShopStorage storage;
    private final List<ZyroxShopMarketListener> listeners = new CopyOnWriteArrayList<>();

    public ShopMarketService(ShopStorage storage) {
        this.storage = storage;
    }

    @Override
    public List<ZyroxMarketShop> search(String itemFilter, String ownerFilter, boolean inStockOnly) throws SQLException {
        String itemQuery = normalize(itemFilter);
        String ownerQuery = normalize(ownerFilter);
        return storage.allShops().stream()
                .filter(shop -> !inStockOnly || shop.stockAmount() > 0)
                .map(this::snapshot)
                .filter(shop -> itemQuery.isBlank()
                        || normalize(shop.itemName()).contains(itemQuery)
                        || normalize(shop.materialName()).contains(itemQuery)
                        || normalize(shop.enchantments()).contains(itemQuery))
                .filter(shop -> ownerQuery.isBlank() || normalize(shop.ownerName()).contains(ownerQuery))
                .sorted((left, right) -> Long.compare(right.stockAmount(), left.stockAmount()))
                .toList();
    }

    @Override
    public List<ZyroxMarketShop> shopsWithRevenue() throws SQLException {
        return storage.allShops().stream()
                .filter(shop -> shop.revenueDiamonds() > 0)
                .map(this::snapshot)
                .toList();
    }

    @Override
    public void registerListener(ZyroxShopMarketListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void unregisterListener(ZyroxShopMarketListener listener) {
        listeners.remove(listener);
    }

    public void publish(ZyroxMarketShopEventType type, ShopRecord shop) {
        if (type == null || shop == null) {
            return;
        }
        ZyroxMarketShopEvent event = new ZyroxMarketShopEvent(type, snapshot(shop));
        for (ZyroxShopMarketListener listener : listeners) {
            listener.onMarketShopEvent(event);
        }
    }

    private ZyroxMarketShop snapshot(ShopRecord shop) {
        return new ZyroxMarketShop(
                shop.id(),
                shop.ownerUuid(),
                shop.ownerName(),
                ItemStacks.displayName(shop.itemTemplate()),
                shop.itemTemplate().getType().name(),
                ItemStacks.enchantmentSummary(shop.itemTemplate()),
                shop.bundleAmount(),
                shop.priceDiamonds(),
                shop.stockAmount(),
                shop.revenueDiamonds(),
                shop.worldName(),
                shop.x(),
                shop.y(),
                shop.z(),
                shop.createdAt(),
                shop.updatedAt()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
