package de.tecca.zyrox.dc.discord;

record MarketCommandFilters(String item, String owner, boolean inStockOnly) {

    MarketCommandFilters {
        item = normalize(item);
        owner = normalize(owner);
    }

    boolean hasAnyFilter() {
        return !item.isBlank() || !owner.isBlank() || !inStockOnly;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
