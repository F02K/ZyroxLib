package de.tecca.zyrox.economy.service;

import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class DiamondEconomyService implements ZyroxDiamondPaymentService {

    private final JavaPlugin plugin;
    private Material material = Material.DIAMOND;

    public DiamondEconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String materialName = plugin.getConfig().getString("economy.material", "DIAMOND");
        Material configuredMaterial = Material.matchMaterial(materialName == null ? "DIAMOND" : materialName);
        if (configuredMaterial == null || !configuredMaterial.isItem()) {
            plugin.getLogger().warning("Invalid economy material '" + materialName + "'. Falling back to DIAMOND.");
            configuredMaterial = Material.DIAMOND;
        }
        material = configuredMaterial;
    }

    @Override
    public Material currencyMaterial() {
        return material;
    }

    public Material material() {
        return currencyMaterial();
    }

    @Override
    public long countDiamonds(Player player) {
        if (player == null) {
            return 0;
        }
        return DiamondInventoryTransaction.count(player.getInventory(), material);
    }

    @Override
    public boolean hasDiamonds(Player player, long diamonds) {
        return player != null && diamonds >= 0 && countDiamonds(player) >= diamonds;
    }

    @Override
    public PaymentResult withdrawDiamonds(Player player, long diamonds, DiamondCommit commit) {
        if (player == null) {
            return PaymentResult.failure("Player must be online to pay with diamonds.", 0);
        }
        if (diamonds < 0) {
            return PaymentResult.failure("Cannot withdraw a negative diamond amount.", countDiamonds(player));
        }
        if (diamonds == 0) {
            return PaymentResult.success(0, countDiamonds(player));
        }
        if (diamonds > Integer.MAX_VALUE) {
            return PaymentResult.failure("Diamond amount is too large.", countDiamonds(player));
        }

        DiamondInventoryTransaction.Result transaction = DiamondInventoryTransaction.withdraw(
                player.getInventory(),
                material,
                Math.toIntExact(diamonds),
                commit::commit
        );
        if (transaction.success()) {
            return PaymentResult.success(diamonds, countDiamonds(player));
        }

        logWithdrawalFailure(player, diamonds, transaction);
        String message = switch (transaction.status()) {
            case INSUFFICIENT_ITEMS -> "Not enough diamonds.";
            case COMMIT_FAILED -> "Could not complete the diamond payment.";
            case INVALID_AMOUNT -> "Diamond amount must be positive.";
            default -> "Could not remove the exact diamond amount.";
        };
        return PaymentResult.failure(message, countDiamonds(player));
    }

    @Override
    public PaymentResult giveDiamonds(Player player, long diamonds) {
        if (player == null) {
            return PaymentResult.failure("Player must be online to receive diamonds.", 0);
        }
        if (diamonds < 0) {
            return PaymentResult.failure("Cannot give a negative diamond amount.", countDiamonds(player));
        }
        if (diamonds == 0) {
            return PaymentResult.success(0, countDiamonds(player));
        }
        if (diamonds > Integer.MAX_VALUE) {
            return PaymentResult.failure("Diamond amount is too large.", countDiamonds(player));
        }

        ItemStack payout = new ItemStack(material, Math.toIntExact(diamonds));
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(payout);
        if (!leftovers.isEmpty()) {
            int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int paid = Math.toIntExact(diamonds) - leftoverAmount;
            if (paid > 0) {
                player.getInventory().removeItem(new ItemStack(material, paid));
            }
            return PaymentResult.failure("You need more inventory space for these diamonds.", countDiamonds(player));
        }
        return PaymentResult.success(diamonds, countDiamonds(player));
    }

    @Override
    public String formatDiamonds(long diamonds) {
        return diamonds + " diamond" + (diamonds == 1 ? "" : "s");
    }

    private void logWithdrawalFailure(
            Player player,
            long requestedDiamonds,
            DiamondInventoryTransaction.Result transaction
    ) {
        String cause = transaction.cause() == null
                ? ""
                : " cause=" + transaction.cause().getMessage();
        plugin.getLogger().severe("Diamond withdrawal failed: player=" + player.getName()
                + " requested=" + formatDiamonds(requestedDiamonds)
                + " status=" + transaction.status()
                + " before=" + transaction.beforeItems()
                + " observedAfter=" + transaction.observedAfterItems()
                + " final=" + transaction.finalItems()
                + " rollbackVerified=" + transaction.rollbackVerified()
                + cause);
    }
}
