package de.tecca.zyrox.shop.api;

public record ZyroxMarketShopEvent(
        ZyroxMarketShopEventType type,
        ZyroxMarketShop shop
) {
}
