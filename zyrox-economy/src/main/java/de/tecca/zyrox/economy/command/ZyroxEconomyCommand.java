package de.tecca.zyrox.economy.command;

import de.tecca.zyrox.economy.plugin.ZyroxEconomyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class ZyroxEconomyCommand implements CommandExecutor, TabCompleter {

    private final ZyroxEconomyPlugin plugin;

    public ZyroxEconomyCommand(ZyroxEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            handleReload(sender);
            return true;
        }
        sendHelp(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(plugin.adminPermission())) {
            return List.of("reload").stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
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
        sender.sendMessage("Diamonds in your inventory are your money.");
        if (sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage("/" + label + " reload");
        }
    }
}
