package de.tecca.zyrox.economy.service;

public record CashoutResult(
        boolean success,
        String message,
        long paidCents,
        int items
) {

    public static CashoutResult success(long paidCents, int items) {
        return new CashoutResult(true, "Paid out " + items + " diamond(s).", paidCents, items);
    }

    public static CashoutResult failure(String message) {
        return new CashoutResult(false, message, 0, 0);
    }
}
