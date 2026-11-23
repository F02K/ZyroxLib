package de.tecca.zyrox.shop.command;

import de.tecca.zyrox.shop.plugin.ZyroxShopPlugin;
import de.tecca.zyrox.shop.service.ShopActionResult;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopTransactionRecord;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
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
            case "history" -> handleHistory(sender, args);
            case "favorite" -> handleFavorite(sender);
            case "favorites" -> handleFavorites(sender, args);
            case "rate" -> handleRate(sender, args);
            case "discount" -> handleDiscount(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("info", "remove", "reload", "history", "favorite", "favorites", "rate", "discount").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && "discount".equalsIgnoreCase(args[0])) {
            return List.of("set", "clear").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT)))
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

    private void handleHistory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<ShopRecord> shop = targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-shop", Map.of()));
            return;
        }
        if (!player.hasPermission(plugin.adminPermission()) && !shop.get().isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.text().message("only-owner-edit", plugin.text().placeholders(shop.get())));
            return;
        }
        int page = parsePage(args, 1);
        List<ShopTransactionRecord> history = plugin.shopService().purchaseHistory(shop.get(), page, 8);
        Map<String, String> placeholders = new HashMap<>(plugin.text().placeholders(shop.get()));
        placeholders.put("page", Integer.toString(page));
        player.sendMessage(plugin.text().message("history-header", placeholders));
        if (history.isEmpty()) {
            player.sendMessage(plugin.text().message("history-empty", placeholders));
            return;
        }
        for (ShopTransactionRecord transaction : history) {
            Map<String, String> line = new HashMap<>(placeholders);
            line.put("buyer", transaction.actorName() == null ? "-" : transaction.actorName());
            line.put("amount", Long.toString(transaction.itemAmount()));
            line.put("price", Long.toString(transaction.diamondAmount()));
            line.put("created_at", transaction.createdAt());
            player.sendMessage(plugin.text().message("history-entry", line));
        }
    }

    private void handleFavorite(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<ShopRecord> shop = targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-shop", Map.of()));
            return;
        }
        ShopActionResult result = plugin.shopService().toggleFavorite(shop.get(), player);
        player.sendMessage(result.message());
    }

    private void handleFavorites(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        int page = parsePage(args, 1);
        List<ShopRecord> favorites = plugin.shopService().favorites(player, page, 8);
        player.sendMessage(plugin.text().message("favorites-header", Map.of("page", Integer.toString(page))));
        if (favorites.isEmpty()) {
            player.sendMessage(plugin.text().message("favorites-empty", Map.of()));
            return;
        }
        for (ShopRecord favorite : favorites) {
            player.sendMessage(plugin.text().message("favorites-entry", plugin.text().placeholders(favorite)));
        }
    }

    private void handleRate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<ShopRecord> shop = targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-shop", Map.of()));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.text().message("rating-usage", plugin.text().placeholders(shop.get())));
            return;
        }
        int rating;
        try {
            rating = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(plugin.text().message("rating-invalid", plugin.text().placeholders(shop.get())));
            return;
        }
        if (rating < 1 || rating > 5) {
            player.sendMessage(plugin.text().message("rating-invalid", plugin.text().placeholders(shop.get())));
            return;
        }
        ShopActionResult result = plugin.shopService().rateShop(shop.get(), player, rating);
        player.sendMessage(result.message());
    }

    private void handleDiscount(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<ShopRecord> shop = targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message("look-at-shop", Map.of()));
            return;
        }
        if (!player.hasPermission(plugin.adminPermission()) && !shop.get().isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.text().message("only-owner-edit", plugin.text().placeholders(shop.get())));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.text().message("discount-usage", plugin.text().placeholders(shop.get())));
            return;
        }
        ShopActionResult result;
        if ("clear".equalsIgnoreCase(args[1])) {
            result = plugin.shopService().clearDiscount(shop.get());
        } else if ("set".equalsIgnoreCase(args[1]) && args.length >= 3) {
            result = handleDiscountSet(player, shop.get(), args);
        } else {
            player.sendMessage(plugin.text().message("discount-usage", plugin.text().placeholders(shop.get())));
            return;
        }
        player.sendMessage(result.message());
    }

    private ShopActionResult handleDiscountSet(Player player, ShopRecord shop, String[] args) {
        int percent;
        try {
            percent = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            return ShopActionResult.failure(plugin.text().message("discount-invalid-percent", plugin.text().placeholders(shop)), shop);
        }
        if (percent < 1 || percent > 90) {
            return ShopActionResult.failure(plugin.text().message("discount-invalid-percent", plugin.text().placeholders(shop)), shop);
        }
        Duration duration = null;
        if (args.length >= 4) {
            try {
                duration = parseDuration(args[3]);
            } catch (DateTimeParseException | IllegalArgumentException exception) {
                return ShopActionResult.failure(plugin.text().message("discount-invalid-duration", plugin.text().placeholders(shop)), shop);
            }
        }
        return plugin.shopService().setDiscount(shop, percent, duration);
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
        sender.sendMessage(plugin.text().message("command-help-history", Map.of()).replace("/shop", "/" + label));
        sender.sendMessage(plugin.text().message("command-help-favorite", Map.of()).replace("/shop", "/" + label));
        sender.sendMessage(plugin.text().message("command-help-favorites", Map.of()).replace("/shop", "/" + label));
        sender.sendMessage(plugin.text().message("command-help-rate", Map.of()).replace("/shop", "/" + label));
        sender.sendMessage(plugin.text().message("command-help-discount", Map.of()).replace("/shop", "/" + label));
        if (sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage(plugin.text().message("command-help-reload", Map.of()).replace("/shop", "/" + label));
        }
    }

    private int parsePage(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[index]));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private Duration parseDuration(String value) {
        if (value == null || value.length() < 2) {
            throw new IllegalArgumentException("Duration is missing.");
        }
        char unit = Character.toLowerCase(value.charAt(value.length() - 1));
        long amount = Long.parseLong(value.substring(0, value.length() - 1));
        if (amount <= 0) {
            throw new IllegalArgumentException("Duration must be positive.");
        }
        return switch (unit) {
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit.");
        };
    }
}
