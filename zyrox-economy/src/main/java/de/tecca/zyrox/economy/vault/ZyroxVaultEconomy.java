package de.tecca.zyrox.economy.vault;

import de.tecca.zyrox.economy.service.DiamondEconomyService;
import de.tecca.zyrox.economy.service.EconomyFormatter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ZyroxVaultEconomy implements Economy {

    private final JavaPlugin plugin;
    private final DiamondEconomyService economy;

    public ZyroxVaultEconomy(JavaPlugin plugin, DiamondEconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "ZyroxEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        try {
            return EconomyFormatter.diamonds(EconomyFormatter.units(amount));
        } catch (Exception exception) {
            return EconomyFormatter.diamonds(0);
        }
    }

    @Override
    public String currencyNamePlural() {
        return "diamonds";
    }

    @Override
    public String currencyNameSingular() {
        return "diamond";
    }

    @Override
    public boolean hasAccount(String playerName) {
        return playerName != null && !playerName.isBlank();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(offline(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return EconomyFormatter.amount(economy.inventoryBalanceCents(player));
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(offline(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Long units = units(amount);
        if (units == null) {
            logRejected("balance check", player, amount);
            return false;
        }
        return economy.hasInventoryBalance(player, units);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(offline(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        Long units = units(amount);
        if (units == null) {
            logRejected("withdrawal", player, amount);
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be whole diamonds.");
        }
        DiamondEconomyService.EconomyOperationResult result = economy.withdraw(player, units);
        return response(result);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(offline(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        Long units = units(amount);
        if (units == null) {
            logRejected("deposit", player, amount);
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be whole diamonds.");
        }
        DiamondEconomyService.EconomyOperationResult result = economy.deposit(player, units);
        return response(result);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse response(DiamondEconomyService.EconomyOperationResult result) {
        EconomyResponse.ResponseType type = result.success()
                ? EconomyResponse.ResponseType.SUCCESS
                : EconomyResponse.ResponseType.FAILURE;
        return new EconomyResponse(result.amount(), result.balance(), type, result.message());
    }

    private EconomyResponse bankUnsupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "ZyroxEconomy does not support banks.");
    }

    private Long units(double amount) {
        try {
            return EconomyFormatter.units(amount);
        } catch (Exception exception) {
            return null;
        }
    }

    private void logRejected(String operation, OfflinePlayer player, double amount) {
        String playerName = player == null ? "unknown" : player.getName();
        plugin.getLogger().warning("Rejected fractional Vault " + operation + ": player="
                + playerName + " amount=" + amount);
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer offline(String playerName) {
        return plugin.getServer().getOfflinePlayer(playerName);
    }
}
