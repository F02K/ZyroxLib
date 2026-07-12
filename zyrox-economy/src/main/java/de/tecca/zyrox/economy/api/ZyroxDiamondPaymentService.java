package de.tecca.zyrox.economy.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ZyroxDiamondPaymentService {

    Material currencyMaterial();

    long countDiamonds(Player player);

    boolean hasDiamonds(Player player, long diamonds);

    PaymentResult withdrawDiamonds(Player player, long diamonds, DiamondCommit commit);

    PaymentResult giveDiamonds(Player player, long diamonds);

    String formatDiamonds(long diamonds);

    @FunctionalInterface
    interface DiamondCommit {
        void commit() throws Exception;
    }

    record PaymentResult(boolean success, long diamonds, long remainingDiamonds, String message) {

        public static PaymentResult success(long diamonds, long remainingDiamonds) {
            return new PaymentResult(true, diamonds, remainingDiamonds, "");
        }

        public static PaymentResult failure(String message, long remainingDiamonds) {
            return new PaymentResult(false, 0, remainingDiamonds, message);
        }
    }
}
