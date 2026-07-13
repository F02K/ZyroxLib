package de.tecca.zyrox.shop.api;

import java.sql.SQLException;
import java.util.List;

public interface ZyroxShopMarketService {

    List<ZyroxMarketShop> search(String itemFilter, String ownerFilter, boolean inStockOnly) throws SQLException;

    List<ZyroxMarketShop> shopsWithRevenue() throws SQLException;

    void registerListener(ZyroxShopMarketListener listener);

    void unregisterListener(ZyroxShopMarketListener listener);
}
