package de.tecca.zyrox.dc.market;

import java.sql.SQLException;
import java.util.List;

public interface DiscordMarketplaceHandler {

    List<DiscordMarketplaceShop> search(String itemFilter, String ownerFilter, boolean inStockOnly) throws SQLException;
}
