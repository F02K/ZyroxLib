package de.tecca.zyrox.protect.listener;

import de.tecca.zyrox.protect.audit.AuditRecord;
import de.tecca.zyrox.protect.audit.AuditStorage;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SecurityEventListener implements Listener {

    private final AuditStorage storage;
    private final Set<String> sensitiveCommands;

    public SecurityEventListener(AuditStorage storage, Set<String> sensitiveCommands) {
        this.storage = storage;
        this.sensitiveCommands = Set.copyOf(sensitiveCommands);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = rootCommand(event.getMessage());
        if (!sensitiveCommands.contains(command)) {
            return;
        }
        Player player = event.getPlayer();
        storage.record(new AuditRecord(
                "COMMAND",
                severityFor(command),
                "PLAYER",
                Optional.of(player.getUniqueId()),
                player.getName(),
                "COMMAND",
                "",
                command,
                "COMMAND_EXECUTED",
                "Sensitive player command executed: /" + command,
                Map.of("cancelled", Boolean.toString(event.isCancelled()), "command", redact(event.getMessage())),
                ""
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        String command = rootCommand(event.getCommand());
        if (!sensitiveCommands.contains(command)) {
            return;
        }
        String source = event.getSender() instanceof ConsoleCommandSender ? "CONSOLE" : "COMMAND_BLOCK";
        storage.record(new AuditRecord(
                "COMMAND",
                severityFor(command),
                source,
                Optional.empty(),
                event.getSender().getName(),
                "COMMAND",
                "",
                command,
                "COMMAND_EXECUTED",
                "Sensitive server command executed: /" + command,
                Map.of("command", redact(event.getCommand())),
                ""
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        PlayerLoginEvent.Result result = event.getResult();
        if (result == PlayerLoginEvent.Result.ALLOWED) {
            return;
        }
        storage.record(new AuditRecord(
                "LOGIN",
                result == PlayerLoginEvent.Result.KICK_BANNED ? "CRITICAL" : "WARNING",
                "SERVER",
                Optional.of(event.getPlayer().getUniqueId()),
                event.getPlayer().getName(),
                "PLAYER",
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                "LOGIN_DENIED",
                "Player login was denied: " + result.name(),
                Map.of("result", result.name(), "address", event.getAddress() == null ? "" : event.getAddress().getHostAddress()),
                ""
        ));
    }

    private String rootCommand(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int space = text.indexOf(' ');
        return (space >= 0 ? text.substring(0, space) : text).toLowerCase(Locale.ROOT);
    }

    private String severityFor(String command) {
        return switch (command) {
            case "op", "deop", "lp", "luckperms", "permission", "permissions", "protect" -> "CRITICAL";
            case "ban", "ban-ip", "pardon", "pardon-ip", "whitelist" -> "WARNING";
            default -> "INFO";
        };
    }

    private String redact(String command) {
        String text = command == null ? "" : command;
        if (text.toLowerCase(Locale.ROOT).contains("setup-password")) {
            return "/protect setup-password [redacted]";
        }
        return text;
    }
}
