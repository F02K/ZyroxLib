package de.tecca.zyrox.shop.api;

@FunctionalInterface
public interface ZyroxShopMarketListener {

    void onMarketShopEvent(ZyroxMarketShopEvent event);
}
