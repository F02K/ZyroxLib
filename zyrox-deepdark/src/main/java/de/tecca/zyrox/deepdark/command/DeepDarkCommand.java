package de.tecca.zyrox.deepdark.command;

import de.tecca.zyrox.deepdark.plugin.ZyroxDeepDarkPlugin;
import de.tecca.zyrox.deepdark.portal.DeepDarkPortal;
import de.tecca.zyrox.deepdark.portal.PortalAxis;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class DeepDarkCommand implements CommandExecutor, TabCompleter {

    private final ZyroxDeepDarkPlugin plugin;

    public DeepDarkCommand(ZyroxDeepDarkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage("You do not have permission to manage the DeepDark.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage("ZyroxDeepDark reloaded.");
            }
            case "tp" -> handleTeleport(sender);
            case "create-portal" -> handleCreatePortal(sender, args);
            case "delete-portal" -> handleDeletePortal(sender, label, args);
            case "list-portals" -> handleListPortals(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "create-portal", "delete-portal", "list-portals", "tp"), args[0]);
        }
        if (args.length == 2 && "delete-portal".equalsIgnoreCase(args[0])) {
            return filter(plugin.portalService().allPortals().stream().map(DeepDarkPortal::id).toList(), args[1]);
        }
        return List.of();
    }

    private void handleTeleport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can teleport into the DeepDark.");
            return;
        }
        player.teleport(plugin.worldService().safeSpawnLocation(plugin.worldService().world()));
        player.sendMessage("Teleported to the DeepDark.");
    }

    private void handleCreatePortal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can create a portal at their location.");
            return;
        }
        String id = args.length >= 2 ? args[1] : "";
        PortalAxis axis = axisFor(player.getLocation());
        DeepDarkPortal portal = plugin.portalService().createPortalAt(player.getLocation(), axis, id, true);
        player.sendMessage("Created DeepDark portal: " + portal.id());
    }

    private void handleDeletePortal(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " delete-portal <id>");
            return;
        }
        boolean removed = plugin.portalService().deletePortal(args[1]);
        sender.sendMessage(removed ? "DeepDark portal deleted." : "Unknown DeepDark portal.");
    }

    private void handleListPortals(CommandSender sender) {
        if (plugin.portalService().allPortals().isEmpty()) {
            sender.sendMessage("No DeepDark portals are registered.");
            return;
        }
        for (DeepDarkPortal portal : plugin.portalService().allPortals()) {
            sender.sendMessage(portal.id() + " -> " + portal.worldName() + " [" + portal.originX() + ", " + portal.originY() + ", " + portal.originZ() + "]");
        }
    }

    private PortalAxis axisFor(Location location) {
        float yaw = Math.abs(location.getYaw()) % 180;
        return yaw > 45 && yaw < 135 ? PortalAxis.Z : PortalAxis.X;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("/" + label + " reload");
        sender.sendMessage("/" + label + " create-portal [id]");
        sender.sendMessage("/" + label + " delete-portal <id>");
        sender.sendMessage("/" + label + " list-portals");
        sender.sendMessage("/" + label + " tp");
    }

    private List<String> filter(List<String> options, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerInput))
                .toList();
    }
}
