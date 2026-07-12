package de.tecca.zyrox.shop.service;

import de.tecca.zyrox.shop.storage.ShopRecord;

public record ShopActionResult(boolean success, String message, ShopRecord shop) {

    public static ShopActionResult success(String message, ShopRecord shop) {
        return new ShopActionResult(true, message, shop);
    }

    public static ShopActionResult failure(String message, ShopRecord shop) {
        return new ShopActionResult(false, message, shop);
    }
}
