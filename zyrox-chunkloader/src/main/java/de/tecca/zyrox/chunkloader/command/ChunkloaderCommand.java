package de.tecca.zyrox.chunkloader.command;

import de.tecca.zyrox.chunkloader.plugin.ZyroxChunkloaderPlugin;
import de.tecca.zyrox.chunkloader.service.ChunkloaderActionResult;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ChunkloaderCommand implements CommandExecutor, TabCompleter {

    private final ZyroxChunkloaderPlugin plugin;

    public ChunkloaderCommand(ZyroxChunkloaderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "give" -> handleGive(sender, args);
            case "remove" -> handleRemove(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "give", "remove", "reload").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage(plugin.text().message("no-admin-permission", Map.of()));
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage(plugin.text().message("reload-success", Map.of()));
    }

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        List<ChunkloaderRecord> loaders = plugin.chunkloaders().loadersByOwner(player.getUniqueId());
        player.sendMessage(plugin.text().message("list-header", Map.of()));
        if (loaders.isEmpty()) {
            player.sendMessage(plugin.text().message("list-empty", Map.of()));
            return;
        }
        for (ChunkloaderRecord loader : loaders) {
            player.sendMessage(plugin.text().message("list-entry", plugin.chunkloaders().placeholders(loader)));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage(plugin.text().message("no-admin-permission", Map.of()));
            return;
        }
        if (args.length < 2) {
            sendHelp(sender, "chunkloader");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        target.getInventory().addItem(plugin.items().createItem(amount));
        sender.sendMessage(plugin.text().message("give-success", Map.of("amount", Integer.toString(amount))));
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        if (!player.hasPermission(plugin.adminPermission())) {
            player.sendMessage(plugin.text().message("no-admin-permission", Map.of()));
            return;
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            player.sendMessage(plugin.text().message("look-at-loader", Map.of()));
            return;
        }
        Optional<ChunkloaderRecord> loader = plugin.chunkloaders().findLoader(block.getLocation());
        if (loader.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-loader", Map.of()));
            return;
        }
        ChunkloaderActionResult result = plugin.chunkloaders().delete(loader.get());
        player.sendMessage(result.message());
        if (result.success()) {
            block.setType(org.bukkit.Material.AIR);
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(plugin.text().message("command-help-list", Map.of()).replace("/chunkloader", "/" + label));
        if (sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage(plugin.text().message("command-help-give", Map.of()).replace("/chunkloader", "/" + label));
            sender.sendMessage(plugin.text().message("command-help-remove", Map.of()).replace("/chunkloader", "/" + label));
            sender.sendMessage(plugin.text().message("command-help-reload", Map.of()).replace("/chunkloader", "/" + label));
        }
    }
}
