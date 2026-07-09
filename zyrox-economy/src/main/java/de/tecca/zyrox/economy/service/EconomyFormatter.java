package de.tecca.zyrox.economy.service;

import java.math.BigDecimal;

public final class EconomyFormatter {

    public static final long DIAMOND_UNIT = 100;

    private EconomyFormatter() {
    }

    public static long units(double amount) {
        long diamonds = wholeDiamonds(amount);
        return diamondUnits(diamonds);
    }

    public static long units(String amount) {
        long diamonds = wholeDiamonds(amount);
        return diamondUnits(diamonds);
    }

    public static long wholeDiamonds(double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Amount must be finite.");
        }
        return wholeDiamonds(BigDecimal.valueOf(amount));
    }

    public static long wholeDiamonds(String amount) {
        return wholeDiamonds(new BigDecimal(amount.trim()));
    }

    public static long diamondUnits(long diamonds) {
        return Math.multiplyExact(diamonds, DIAMOND_UNIT);
    }

    public static double amount(long units) {
        return BigDecimal.valueOf(units)
                .divide(BigDecimal.valueOf(DIAMOND_UNIT))
                .doubleValue();
    }

    public static String diamonds(long units) {
        long wholeDiamonds = units / DIAMOND_UNIT;
        long remainder = Math.abs(units % DIAMOND_UNIT);
        if (remainder == 0) {
            return wholeDiamonds + " diamond" + (wholeDiamonds == 1 ? "" : "s");
        }
        return amount(units) + " diamonds";
    }

    private static long wholeDiamonds(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > 0) {
            throw new IllegalArgumentException("Amount must be a whole diamond value.");
        }
        long diamonds = stripped.longValueExact();
        if (diamonds < 0) {
            throw new IllegalArgumentException("Amount cannot be negative.");
        }
        return diamonds;
    }
}
