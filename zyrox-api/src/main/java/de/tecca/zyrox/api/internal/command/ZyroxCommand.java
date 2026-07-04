package de.tecca.zyrox.api.internal.command;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionParameter;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.action.ZyroxActionSource;
import de.tecca.zyrox.api.internal.SimpleZyroxActionContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZyroxCommand implements CommandExecutor, TabCompleter {

    private final ZyroxApi api;
    private final String webPermission;

    public ZyroxCommand(ZyroxApi api, String webPermission) {
        this.api = api;
        this.webPermission = webPermission;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if ("web".equals(subCommand)) {
            return handleWeb(sender);
        }
        if ("action".equals(subCommand)) {
            return handleAction(sender, label, args);
        }

        sendHelp(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("web", "action"), args[0]);
        }
        if (args.length == 2 && "action".equalsIgnoreCase(args[0])) {
            return filter(api.modules().all().stream().map(module -> module.id()).toList(), args[1]);
        }
        if (args.length == 3 && "action".equalsIgnoreCase(args[0])) {
            return filter(api.actions().allForModule(args[1]).stream().map(ZyroxAction::id).toList(), args[2]);
        }
        return List.of();
    }

    private boolean handleWeb(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can create Zyrox dashboard session links.");
            return true;
        }

        if (!api.permissions().hasPermission(player, webPermission)) {
            player.sendMessage(Component.text("You do not have permission to open the Zyrox dashboard.", NamedTextColor.RED));
            return true;
        }

        URI sessionLink = api.webPanel().createSessionLink(player);
        player.sendMessage(Component.text("Open Zyrox dashboard: ", NamedTextColor.GREEN)
                .append(Component.text(sessionLink.toString(), NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(sessionLink.toString()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to open the ZyroxMC dashboard", NamedTextColor.GRAY)))));
        return true;
    }

    private boolean handleAction(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label + " action <module> <action> [key=value...]");
            return true;
        }

        String moduleId = args[1];
        String actionId = args[2];
        Optional<ZyroxAction> action = api.actions().get(moduleId, actionId);
        if (action.isEmpty()) {
            sender.sendMessage("Unknown Zyrox action: " + moduleId + "." + actionId);
            return true;
        }

        ZyroxAction zyroxAction = action.get();
        if (zyroxAction.hasPermission() && !senderHasPermission(sender, zyroxAction.permission())) {
            sender.sendMessage("You do not have permission to run this action.");
            return true;
        }

        Map<String, String> parameters = parseParameters(zyroxAction, args);
        UUID actorUniqueId = sender instanceof Player player ? player.getUniqueId() : null;
        SimpleZyroxActionContext context = new SimpleZyroxActionContext(
                api,
                ZyroxActionSource.COMMAND,
                actorUniqueId,
                sender.getName(),
                parameters
        );

        try {
            ZyroxActionResult result = zyroxAction.handler().execute(context);
            sender.sendMessage(result.message());
        } catch (Exception exception) {
            sender.sendMessage("Action failed: " + exception.getMessage());
        }
        return true;
    }

    private boolean senderHasPermission(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (sender instanceof Player player) {
            return api.permissions().hasPermission(player, permission);
        }
        return sender.hasPermission(permission) || sender.isOp();
    }

    private Map<String, String> parseParameters(ZyroxAction action, String[] args) {
        Map<String, String> parameters = new LinkedHashMap<>();
        List<ZyroxActionParameter> positionalParameters = new ArrayList<>(action.parameters());
        int positionalIndex = 0;

        for (int index = 3; index < args.length; index++) {
            String argument = args[index];
            int separator = argument.indexOf('=');
            if (separator > 0) {
                parameters.put(argument.substring(0, separator), argument.substring(separator + 1));
                continue;
            }

            if (positionalIndex < positionalParameters.size()) {
                parameters.put(positionalParameters.get(positionalIndex).id(), argument);
                positionalIndex++;
            }
        }

        return parameters;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("/" + label + " web");
        sender.sendMessage("/" + label + " action <module> <action> [key=value...]");
    }

    private List<String> filter(List<String> options, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowerInput))
                .toList();
    }
}
