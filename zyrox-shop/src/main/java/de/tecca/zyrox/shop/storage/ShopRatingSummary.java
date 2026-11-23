package de.tecca.zyrox.shop.storage;

public record ShopRatingSummary(
        double average,
        int count
) {

    public static ShopRatingSummary empty() {
        return new ShopRatingSummary(0.0D, 0);
    }
}
