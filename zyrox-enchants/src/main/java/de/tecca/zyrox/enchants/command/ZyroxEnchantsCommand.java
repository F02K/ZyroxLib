package de.tecca.zyrox.enchants.command;

import de.tecca.zyrox.enchants.plugin.ZyroxEnchantsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ZyroxEnchantsCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_LEVEL_SUGGESTIONS = 10;

    private final ZyroxEnchantsPlugin plugin;

    public ZyroxEnchantsCommand(ZyroxEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> handleGive(sender, label, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(plugin.givePermission())) {
                options.add("give");
            }
            if (sender.hasPermission(plugin.reloadPermission())) {
                options.add("reload");
            }
            return filter(options, args[0]);
        }

        if (!"give".equalsIgnoreCase(args[0]) || !sender.hasPermission(plugin.givePermission())) {
            return List.of();
        }

        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            options.addAll(plugin.enchants().vanillaSuggestions());
            return filter(options, args[1]);
        }

        if (args.length == 3) {
            if (Bukkit.getPlayerExact(args[1]) != null) {
                return filter(plugin.enchants().vanillaSuggestions(), args[2]);
            }
            return filter(levelSuggestions(), args[2]);
        }

        if (args.length == 4 && Bukkit.getPlayerExact(args[1]) != null) {
            return filter(levelSuggestions(), args[3]);
        }

        return List.of();
    }

    private void handleGive(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(plugin.givePermission())) {
            sender.sendMessage(plugin.text().message("no-give-permission", Map.of()));
            return;
        }

        Player target;
        String enchantmentInput;
        String levelInput;

        if (args.length == 3) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.text().message("only-player-self-command", Map.of()));
                return;
            }
            target = player;
            enchantmentInput = args[1];
            levelInput = args[2];
        } else if (args.length == 4) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.text().message("player-not-found", Map.of("player", args[1])));
                return;
            }
            enchantmentInput = args[2];
            levelInput = args[3];
        } else {
            sendHelp(sender, label);
            return;
        }

        Optional<Enchantment> enchantment = plugin.enchants().resolveVanilla(enchantmentInput);
        if (enchantment.isEmpty()) {
            sender.sendMessage(plugin.text().message("invalid-enchantment", Map.of("enchantment", enchantmentInput)));
            return;
        }

        int maxLevel = plugin.maxLevel();
        int level;
        try {
            level = Integer.parseInt(levelInput);
        } catch (NumberFormatException exception) {
            sender.sendMessage(plugin.text().message("invalid-level", Map.of("max_level", String.valueOf(maxLevel))));
            return;
        }

        if (!plugin.enchants().isValidLevel(level, maxLevel)) {
            sender.sendMessage(plugin.text().message("invalid-level", Map.of("max_level", String.valueOf(maxLevel))));
            return;
        }

        if (target.getInventory().firstEmpty() < 0) {
            sender.sendMessage(plugin.text().message("inventory-full", Map.of("player", target.getName())));
            return;
        }

        ItemStack book = plugin.enchants().createBook(enchantment.get(), level);
        target.getInventory().addItem(book);
        sender.sendMessage(plugin.text().message("give-success", Map.of(
                "player", target.getName(),
                "enchantment", plugin.enchants().displayKey(enchantment.get()),
                "level", String.valueOf(level)
        )));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(plugin.reloadPermission())) {
            sender.sendMessage(plugin.text().message("no-reload-permission", Map.of()));
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage(plugin.text().message("reload-success", Map.of()));
    }

    private void sendHelp(CommandSender sender, String label) {
        if (sender instanceof Player && sender.hasPermission(plugin.givePermission())) {
            sender.sendMessage(plugin.text().message("command-help-give-self", Map.of()).replace("/zenchants", "/" + label));
        }
        if (sender.hasPermission(plugin.givePermission())) {
            sender.sendMessage(plugin.text().message("command-help-give-player", Map.of()).replace("/zenchants", "/" + label));
        }
        if (sender.hasPermission(plugin.reloadPermission())) {
            sender.sendMessage(plugin.text().message("command-help-reload", Map.of()).replace("/zenchants", "/" + label));
        }
    }

    private List<String> levelSuggestions() {
        int maxLevel = plugin.maxLevel();
        int suggestionCount = Math.min(maxLevel, MAX_LEVEL_SUGGESTIONS);
        List<String> suggestions = new ArrayList<>(suggestionCount);
        for (int level = 1; level <= suggestionCount; level++) {
            suggestions.add(String.valueOf(level));
        }
        if (maxLevel > suggestionCount) {
            suggestions.add(String.valueOf(maxLevel));
        }
        return suggestions;
    }

    private List<String> filter(List<String> options, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerInput))
                .toList();
    }
}
