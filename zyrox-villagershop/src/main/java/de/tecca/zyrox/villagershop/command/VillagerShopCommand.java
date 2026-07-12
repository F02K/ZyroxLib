package de.tecca.zyrox.villagershop.command;

import de.tecca.zyrox.villagershop.gui.VillagerShopGui;
import de.tecca.zyrox.villagershop.plugin.ZyroxVillagerShopPlugin;
import de.tecca.zyrox.villagershop.service.VillagerShopService;
import de.tecca.zyrox.villagershop.storage.VillagerShopRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class VillagerShopCommand implements CommandExecutor, TabCompleter {

    private final ZyroxVillagerShopPlugin plugin;
    private final VillagerShopService shops;

    public VillagerShopCommand(ZyroxVillagerShopPlugin plugin, VillagerShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage(plugin.text().message("no-admin-permission", Map.of()));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> handleSpawn(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "name" -> handleName(sender, args);
            case "text" -> handleText(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("spawn", "edit", "tp", "remove", "info", "list", "reload", "name", "text").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && List.of("edit", "tp", "remove", "info", "name", "text").contains(args[0].toLowerCase(Locale.ROOT))) {
            try {
                return shops.allShops().stream()
                        .map(shop -> Long.toString(shop.id()))
                        .filter(id -> id.startsWith(args[1]))
                        .toList();
            } catch (SQLException exception) {
                return List.of();
            }
        }
        return List.of();
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        String name = join(args, 1);
        try {
            VillagerShopRecord shop = shops.createShop(player, name);
            player.sendMessage(plugin.text().message("spawn-success", plugin.text().placeholders(shop)));
            player.openInventory(VillagerShopGui.editor(shop, plugin.text()));
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not spawn villager shop: " + exception.getMessage());
            player.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        Optional<VillagerShopRecord> shop = args.length >= 2 ? byId(args[1]) : shops.targetedShop(player);
        if (shop.isEmpty()) {
            player.sendMessage(plugin.text().message(args.length >= 2 ? "unknown-shop" : "look-at-villager", Map.of()));
            return;
        }
        player.openInventory(VillagerShopGui.editor(shop.get(), plugin.text()));
        player.sendMessage(plugin.text().message("edit-opened", plugin.text().placeholders(shop.get())));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        if (args.length < 2) {
            sendHelp(sender, "villagershop");
            return;
        }
        Optional<Long> id = parseId(args[1]);
        if (id.isEmpty()) {
            player.sendMessage(plugin.text().message("invalid-number", Map.of()));
            return;
        }
        try {
            VillagerShopRecord shop = shops.teleport(id.get(), player.getLocation());
            player.sendMessage(plugin.text().message("tp-success", plugin.text().placeholders(shop)));
        } catch (SQLException | java.util.NoSuchElementException exception) {
            player.sendMessage(plugin.text().message("unknown-shop", Map.of()));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        Optional<Long> id = args.length >= 2 ? parseId(args[1]) : Optional.empty();
        if (id.isEmpty()) {
            sender.sendMessage(plugin.text().message("invalid-number", Map.of()));
            return;
        }
        try {
            if (shops.remove(id.get())) {
                sender.sendMessage(plugin.text().message("remove-success", Map.of("shop_id", Long.toString(id.get()))));
            } else {
                sender.sendMessage(plugin.text().message("unknown-shop", Map.of()));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not remove villager shop: " + exception.getMessage());
            sender.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Optional<VillagerShopRecord> shop;
        if (args.length >= 2) {
            shop = byId(args[1]);
        } else if (sender instanceof Player player) {
            shop = shops.targetedShop(player);
        } else {
            sender.sendMessage(plugin.text().message("only-player-command", Map.of()));
            return;
        }
        if (shop.isEmpty()) {
            sender.sendMessage(plugin.text().message("unknown-shop", Map.of()));
            return;
        }
        VillagerShopRecord record = shop.get();
        sender.sendMessage(shops.formatInfoLine("trade-info-header", record));
        sender.sendMessage(shops.formatInfoLine("trade-info-position", record));
        sender.sendMessage(shops.formatInfoLine("trade-info-offer", record));
        sender.sendMessage(shops.formatInfoLine(record.trades().get(0).unlimited() ? "trade-info-unlimited" : "trade-info-limit", record));
    }

    private void handleList(CommandSender sender) {
        try {
            List<VillagerShopRecord> all = shops.allShops();
            if (all.isEmpty()) {
                sender.sendMessage("Keine Villager-Shops vorhanden.");
                return;
            }
            for (VillagerShopRecord shop : all) {
                sender.sendMessage("#" + shop.id() + " " + shop.displayName() + " (" + shop.worldName() + " "
                        + (int) shop.x() + " " + (int) shop.y() + " " + (int) shop.z() + ")");
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not list villager shops: " + exception.getMessage());
            sender.sendMessage(plugin.text().message("storage-failed", Map.of()));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(plugin.text().message("reload-success", Map.of()));
    }

    private void handleName(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelp(sender, "villagershop");
            return;
        }
        Optional<Long> id = parseId(args[1]);
        if (id.isEmpty()) {
            sender.sendMessage(plugin.text().message("invalid-number", Map.of()));
            return;
        }
        try {
            VillagerShopRecord shop = shops.rename(id.get(), join(args, 2));
            sender.sendMessage(plugin.text().message("name-success", plugin.text().placeholders(shop)));
        } catch (SQLException | java.util.NoSuchElementException exception) {
            sender.sendMessage(plugin.text().message("unknown-shop", Map.of()));
        }
    }

    private void handleText(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelp(sender, "villagershop");
            return;
        }
        Optional<Long> id = parseId(args[1]);
        if (id.isEmpty()) {
            sender.sendMessage(plugin.text().message("invalid-number", Map.of()));
            return;
        }
        try {
            VillagerShopRecord shop = shops.updateDisplayText(id.get(), join(args, 2));
            sender.sendMessage(plugin.text().message("text-success", plugin.text().placeholders(shop)));
        } catch (SQLException | java.util.NoSuchElementException exception) {
            sender.sendMessage(plugin.text().message("unknown-shop", Map.of()));
        }
    }

    private Optional<VillagerShopRecord> byId(String value) {
        return parseId(value).flatMap(shops::findById);
    }

    private Optional<Long> parseId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String join(String[] args, int start) {
        List<String> parts = new ArrayList<>();
        for (int index = start; index < args.length; index++) {
            if (!args[index].isBlank()) {
                parts.add(args[index]);
            }
        }
        return String.join(" ", parts);
    }

    private void sendHelp(CommandSender sender, String label) {
        for (String key : List.of(
                "command-help-spawn",
                "command-help-edit",
                "command-help-tp",
                "command-help-remove",
                "command-help-info",
                "command-help-list",
                "command-help-name",
                "command-help-text",
                "command-help-reload"
        )) {
            sender.sendMessage(plugin.text().message(key, Map.of()).replace("/villagershop", "/" + label));
        }
    }
}
