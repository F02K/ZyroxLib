package de.tecca.zyrox.economy.command;

import de.tecca.zyrox.economy.plugin.ZyroxEconomyPlugin;
import de.tecca.zyrox.economy.service.CashoutResult;
import de.tecca.zyrox.economy.service.EconomyFormatter;
import de.tecca.zyrox.economy.storage.EconomyAccount;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ZyroxEconomyCommand implements CommandExecutor, TabCompleter {

    private final ZyroxEconomyPlugin plugin;

    public ZyroxEconomyCommand(ZyroxEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "balance" -> handleBalance(sender);
                case "cashout" -> handleCashout(sender, args);
                case "reload" -> handleReload(sender);
                default -> sendHelp(sender, label);
            }
        } catch (Exception exception) {
            sender.sendMessage("ZyroxEconomy command failed: " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("balance", "cashout", "reload"), args[0]);
        }
        if (args.length == 2 && "cashout".equalsIgnoreCase(args[0])) {
            return filter(List.of("all"), args[1]);
        }
        return List.of();
    }

    private void handleBalance(CommandSender sender) throws Exception {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have an inventory diamond balance.");
            return;
        }
        if (!sender.hasPermission(plugin.usePermission())) {
            sender.sendMessage("You do not have permission to use economy commands.");
            return;
        }

        long inventoryCents = plugin.economyService().inventoryBalanceCents(player);
        EconomyAccount account = plugin.storage().findAccount(player).orElse(null);
        long changeCents = account == null ? 0 : account.changeBalanceCents();
        long payoutCents = account == null ? 0 : account.payoutBalanceCents();

        sender.sendMessage("Inventory diamonds: " + EconomyFormatter.diamonds(inventoryCents));
        sender.sendMessage("Bank change: " + EconomyFormatter.diamonds(changeCents));
        sender.sendMessage("Cashout bank: " + EconomyFormatter.diamonds(payoutCents));
    }

    private void handleCashout(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can cash out economy balances.");
            return;
        }
        if (!sender.hasPermission(plugin.usePermission())) {
            sender.sendMessage("You do not have permission to use economy commands.");
            return;
        }

        Optional<Long> requestedCents = Optional.empty();
        if (args.length >= 2 && !"all".equalsIgnoreCase(args[1])) {
            requestedCents = Optional.of(EconomyFormatter.units(args[1]));
        }

        CashoutResult result = plugin.economyService().cashout(player, requestedCents);
        sender.sendMessage(result.success()
                ? "Cashed out " + EconomyFormatter.diamonds(result.paidCents()) + " as " + result.items() + " item(s)."
                : result.message());
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage("You do not have permission to manage ZyroxEconomy.");
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage("ZyroxEconomy reloaded.");
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("/" + label + " balance");
        sender.sendMessage("/" + label + " cashout [amount|all]");
        if (sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage("/" + label + " reload");
        }
    }

    private List<String> filter(List<String> options, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerInput))
                .toList();
    }
}
