package de.tecca.zyrox.economy.service;

import de.tecca.zyrox.economy.storage.EconomyAccount;
import de.tecca.zyrox.economy.storage.EconomyStorage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class DiamondEconomyService {

    private final JavaPlugin plugin;
    private final EconomyStorage storage;
    private Material material = Material.DIAMOND;
    private boolean debugVault;

    public DiamondEconomyService(JavaPlugin plugin, EconomyStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
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
        debugVault = plugin.getConfig().getBoolean("economy.debug-vault", false);
    }

    public Material material() {
        return material;
    }

    public long inventoryBalanceCents(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            return 0;
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return 0;
        }
        return EconomyFormatter.diamondUnits(countItems(player.getInventory()));
    }

    public boolean hasInventoryBalance(OfflinePlayer offlinePlayer, long amountCents) {
        if (offlinePlayer == null) {
            return false;
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null || amountCents < 0) {
            return false;
        }
        try {
            return countItems(player.getInventory()) >= exactRequiredItems(amountCents);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return false;
        }
    }

    public EconomyOperationResult withdraw(OfflinePlayer offlinePlayer, long amountCents) {
        if (amountCents < 0) {
            return EconomyOperationResult.failure("Cannot withdraw a negative amount.", inventoryBalanceCents(offlinePlayer));
        }
        if (amountCents == 0) {
            return EconomyOperationResult.success(0, inventoryBalanceCents(offlinePlayer));
        }
        if (amountCents % EconomyFormatter.DIAMOND_UNIT != 0) {
            return EconomyOperationResult.failure("Withdrawal amount must be whole diamonds.", inventoryBalanceCents(offlinePlayer));
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return EconomyOperationResult.failure("Player must be online to pay with inventory diamonds.", 0);
        }

        int requiredItems;
        try {
            requiredItems = exactRequiredItems(amountCents);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return EconomyOperationResult.failure("Withdrawal amount must be whole diamonds.", inventoryBalanceCents(player));
        }

        int availableItems = countItems(player.getInventory());
        if (availableItems < requiredItems) {
            return EconomyOperationResult.failure("Not enough inventory diamonds.", inventoryBalanceCents(player));
        }

        int removedItems = removeItems(player.getInventory(), requiredItems);
        if (removedItems != requiredItems) {
            refundItems(player, removedItems);
            return EconomyOperationResult.failure("Could not remove the exact diamond amount.", inventoryBalanceCents(player));
        }

        try {
            storage.recordWithdrawal(player, amountCents, 0);
        } catch (SQLException exception) {
            refundItems(player, requiredItems);
            plugin.getLogger().warning("Could not record economy withdrawal: " + exception.getMessage());
            return EconomyOperationResult.failure("Could not record economy withdrawal.", inventoryBalanceCents(player));
        }

        if (debugVault) {
            int afterItems = countItems(player.getInventory());
            plugin.getLogger().info("Vault withdraw: player=" + player.getName()
                    + " amount=" + EconomyFormatter.diamonds(amountCents)
                    + " before=" + availableItems
                    + " removed=" + removedItems
                    + " after=" + afterItems);
        }

        return EconomyOperationResult.success(EconomyFormatter.amount(amountCents), inventoryBalanceCents(player));
    }

    public EconomyOperationResult deposit(OfflinePlayer offlinePlayer, long amountCents) {
        if (amountCents < 0) {
            return EconomyOperationResult.failure("Cannot deposit a negative amount.", inventoryBalanceCents(offlinePlayer));
        }
        if (amountCents == 0) {
            return EconomyOperationResult.success(0, inventoryBalanceCents(offlinePlayer));
        }
        if (amountCents % EconomyFormatter.DIAMOND_UNIT != 0) {
            return EconomyOperationResult.failure("Deposit amount must be whole diamonds.", inventoryBalanceCents(offlinePlayer));
        }

        long reserveBefore = -1;
        try {
            if (debugVault) {
                reserveBefore = storage.shopReserveBalance();
            }
            boolean stored = storage.recordDeposit(offlinePlayer, amountCents);
            if (!stored) {
                return EconomyOperationResult.failure("Deposit is not covered by collected shop reserve.", inventoryBalanceCents(offlinePlayer));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not record economy deposit: " + exception.getMessage());
            return EconomyOperationResult.failure("Could not record economy deposit.", inventoryBalanceCents(offlinePlayer));
        }

        if (debugVault) {
            try {
                long reserveAfter = storage.shopReserveBalance();
                plugin.getLogger().info("Vault deposit: player=" + offlinePlayer.getName()
                        + " amount=" + EconomyFormatter.diamonds(amountCents)
                        + " reserveBefore=" + EconomyFormatter.diamonds(reserveBefore)
                        + " reserveAfter=" + EconomyFormatter.diamonds(reserveAfter));
            } catch (SQLException exception) {
                plugin.getLogger().info("Vault deposit: player=" + offlinePlayer.getName()
                        + " amount=" + EconomyFormatter.diamonds(amountCents));
            }
        }

        return EconomyOperationResult.success(EconomyFormatter.amount(amountCents), inventoryBalanceCents(offlinePlayer));
    }

    public Optional<EconomyAccount> account(OfflinePlayer player) throws SQLException {
        return storage.findAccount(player);
    }

    public CashoutResult cashout(Player player, Optional<Long> requestedCents) {
        long availableCents;
        try {
            EconomyAccount account = storage.findAccount(player).orElse(null);
            availableCents = account == null ? 0 : account.changeBalanceCents() + account.payoutBalanceCents();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load economy account: " + exception.getMessage());
            return CashoutResult.failure("Could not load your economy account.");
        }

        long targetCents = requestedCents.orElse(availableCents);
        long wholeCents = (Math.min(availableCents, targetCents) / EconomyFormatter.DIAMOND_UNIT) * EconomyFormatter.DIAMOND_UNIT;
        if (wholeCents <= 0) {
            return CashoutResult.failure("No whole " + material.name().toLowerCase() + " payout is available.");
        }

        int items = Math.toIntExact(wholeCents / EconomyFormatter.DIAMOND_UNIT);
        if (freeItemCapacity(player.getInventory()) < items) {
            return CashoutResult.failure("You need space for " + items + " item(s) before cashing out.");
        }

        long paidCents;
        try {
            paidCents = storage.withdrawCashout(player, wholeCents, EconomyFormatter.DIAMOND_UNIT);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not record economy cashout: " + exception.getMessage());
            return CashoutResult.failure("Could not record economy cashout.");
        }

        if (paidCents <= 0) {
            return CashoutResult.failure("No whole " + material.name().toLowerCase() + " payout is available.");
        }

        int paidItems = Math.toIntExact(paidCents / EconomyFormatter.DIAMOND_UNIT);
        player.getInventory().addItem(new ItemStack(material, paidItems));
        return CashoutResult.success(paidCents, paidItems);
    }

    private int exactRequiredItems(long amountCents) {
        if (amountCents < 0 || amountCents % EconomyFormatter.DIAMOND_UNIT != 0) {
            throw new IllegalArgumentException("Amount must be whole diamonds.");
        }
        return Math.toIntExact(amountCents / EconomyFormatter.DIAMOND_UNIT);
    }

    private int countItems(Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int removeItems(Inventory inventory, int amount) {
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (item == null || item.getType() != material) {
                continue;
            }

            int taken = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - taken);
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= taken;
        }
        inventory.setStorageContents(contents);
        return amount - remaining;
    }

    private void refundItems(Player player, int amount) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private int freeItemCapacity(Inventory inventory) {
        int capacity = 0;
        int maxStackSize = material.getMaxStackSize();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                capacity += maxStackSize;
            } else if (item.getType() == material) {
                capacity += Math.max(0, maxStackSize - item.getAmount());
            }
        }
        return capacity;
    }

    public record EconomyOperationResult(boolean success, double amount, double balance, String message) {

        public static EconomyOperationResult success(double amount, long balanceCents) {
            return new EconomyOperationResult(true, amount, EconomyFormatter.amount(balanceCents), "");
        }

        public static EconomyOperationResult failure(String message, long balanceCents) {
            return new EconomyOperationResult(false, 0, EconomyFormatter.amount(balanceCents), message);
        }
    }
}
