package de.tecca.zyrox.dc.command;

import de.tecca.zyrox.dc.plugin.ZyroxDcPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class ZyroxDcCommand implements CommandExecutor, TabCompleter {

    private final ZyroxDcPlugin plugin;
    private final String adminPermission;

    public ZyroxDcCommand(ZyroxDcPlugin plugin, String adminPermission) {
        this.plugin = plugin;
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(adminPermission)) {
            sender.sendMessage("You do not have permission to use Discord verification admin commands.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "verify-message" -> {
                    plugin.sendVerificationMessage();
                    sender.sendMessage("Verification embed sent.");
                }
                case "link" -> {
                    if (args.length < 3) {
                        sender.sendMessage("Usage: /" + label + " link <discordId> <minecraftName>");
                        return true;
                    }
                    plugin.adminLink(args[1], args[2]);
                    sender.sendMessage("Account linked and whitelisted.");
                }
                case "unlink" -> {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: /" + label + " unlink <discordId>");
                        return true;
                    }
                    boolean removed = plugin.adminUnlink(args[1]);
                    sender.sendMessage(removed ? "All accounts for that Discord id unlinked." : "No link existed for that Discord id.");
                }
                case "reload" -> {
                    plugin.reloadPlugin();
                    sender.sendMessage("ZyroxDC reloaded.");
                }
                default -> sendHelp(sender, label);
            }
        } catch (Exception exception) {
            sender.sendMessage("ZyroxDC command failed: " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("verify-message", "link", "unlink", "reload").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("/" + label + " verify-message");
        sender.sendMessage("/" + label + " link <discordId> <minecraftName>");
        sender.sendMessage("/" + label + " unlink <discordId>");
        sender.sendMessage("/" + label + " reload");
    }
}
