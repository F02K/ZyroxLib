package de.tecca.zyrox.shop.command;

import de.tecca.zyrox.shop.plugin.ZyroxShopPlugin;
import de.tecca.zyrox.shop.service.ShopActionResult;
import de.tecca.zyrox.shop.storage.ShopRecord;
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

public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ZyroxShopPlugin plugin;

    public ShopCommand(ZyroxShopPlugin plugin) {
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
            case "info" -> handleInfo(sender);
            case "remove" -> handleRemove(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("info", "remove", "reload").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
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

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<ShopRecord> shop = targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-shop", Map.of()));
            return;
        }
        ShopRecord record = shop.get();
        Map<String, String> placeholders = plugin.text().placeholders(record);
        player.sendMessage(plugin.text().message("shop-info-header", placeholders));
        player.sendMessage(plugin.text().message("shop-info-offer", placeholders));
        player.sendMessage(plugin.text().message("shop-info-stock", placeholders));
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<ShopRecord> shop = targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-shop", Map.of()));
            return;
        }
        boolean admin = player.hasPermission(plugin.adminPermission());
        if (!admin && !shop.get().isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.text().message("only-owner-edit", plugin.text().placeholders(shop.get())));
            return;
        }
        ShopActionResult result = plugin.shopService().deleteShop(shop.get(), player, admin);
        player.sendMessage(result.message());
    }

    private Optional<ShopRecord> targetedShop(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (!plugin.shopService().isChest(block)) {
            return Optional.empty();
        }
        return plugin.shopService().findShop(block.getLocation());
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(plugin.text().message("command-help-info", Map.of()).replace("/shop", "/" + label));
        sender.sendMessage(plugin.text().message("command-help-remove", Map.of()).replace("/shop", "/" + label));
        if (sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage(plugin.text().message("command-help-reload", Map.of()).replace("/shop", "/" + label));
        }
    }
}
