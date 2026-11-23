package de.tecca.zyrox.protect.command;

import de.tecca.zyrox.protect.audit.AuditRecord;
import de.tecca.zyrox.protect.audit.AuditExporter;
import de.tecca.zyrox.protect.audit.AuditQuery;
import de.tecca.zyrox.protect.audit.AuditStorage;
import de.tecca.zyrox.protect.audit.HashChainStatus;
import de.tecca.zyrox.protect.auth.ProtectAuthService;
import de.tecca.zyrox.protect.plugin.ZyroxProtectPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ZyroxProtectCommand implements CommandExecutor, TabCompleter {

    private final ZyroxProtectPlugin plugin;
    private final AuditStorage storage;
    private final ProtectAuthService authService;
    private final String adminPermission;

    public ZyroxProtectCommand(ZyroxProtectPlugin plugin, AuditStorage storage, ProtectAuthService authService, String adminPermission) {
        this.plugin = plugin;
        this.storage = storage;
        this.authService = authService;
        this.adminPermission = adminPermission;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(adminPermission) && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("You do not have permission to manage ZyroxProtect.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (subCommand) {
                case "status" -> status(sender);
                case "setup-password" -> setupPassword(sender, args);
                case "lock" -> lock(sender);
                case "export" -> export(sender, args);
                case "purge-expired" -> purgeExpired(sender, args);
                case "reload" -> reload(sender);
                default -> {
                    sendHelp(sender, label);
                    yield true;
                }
            };
        } catch (Exception exception) {
            sender.sendMessage("ZyroxProtect command failed: " + exception.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("status", "setup-password", "lock", "export", "purge-expired", "reload").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private boolean status(CommandSender sender) throws SQLException {
        HashChainStatus status = storage.verifyHashChain();
        sender.sendMessage("ZyroxProtect password configured: " + authService.isPasswordConfigured());
        sender.sendMessage("Audit events: " + storage.countEvents());
        sender.sendMessage(status.message());
        return true;
    }

    private boolean setupPassword(CommandSender sender, String[] args) throws SQLException {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("ZyroxProtect password setup is console-only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /protect setup-password <newPassword>");
            return true;
        }
        authService.setPassword(args[1].toCharArray());
        storage.record(AuditRecord.system("AUTH", "CRITICAL", "PASSWORD_SET", "ZyroxProtect dashboard password was set from console.", Map.of()));
        sender.sendMessage("ZyroxProtect dashboard password saved.");
        return true;
    }

    private boolean lock(CommandSender sender) {
        authService.lockAll();
        storage.record(AuditRecord.system("AUTH", "INFO", "LOCK_ALL", "ZyroxProtect dashboard sessions were locked.", Map.of("actor", sender.getName())));
        sender.sendMessage("All active ZyroxProtect dashboard unlock sessions were locked.");
        return true;
    }

    private boolean export(CommandSender sender, String[] args) throws Exception {
        String format = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "json";
        AuditExporter exporter = new AuditExporter();
        String content = "csv".equals(format)
                ? exporter.csv(storage.query(AuditQuery.recent(500)))
                : exporter.json(storage.query(AuditQuery.recent(500)), storage.verifyHashChain());
        String extension = "csv".equals(format) ? "csv" : "json";
        Path output = plugin.getDataFolder().toPath().resolve("audit-export-" + Instant.now().toEpochMilli() + "." + extension);
        Files.createDirectories(output.getParent());
        Files.writeString(output, content, StandardCharsets.UTF_8);
        storage.record(AuditRecord.system("AUDIT", "INFO", "EXPORT", "Audit export written.", Map.of("file", output.toString(), "format", extension)));
        sender.sendMessage("Audit export written to " + output);
        return true;
    }

    private boolean purgeExpired(CommandSender sender, String[] args) throws SQLException {
        int days = args.length >= 2 ? Integer.parseInt(args[1]) : plugin.getConfig().getInt("audit.retained-days", 0);
        int purged = storage.purgeOlderThanDays(days);
        storage.record(AuditRecord.system("AUDIT", "WARNING", "PURGE", "Purged old audit events.", Map.of("days", Integer.toString(days), "purged", Integer.toString(purged))));
        sender.sendMessage("Purged " + purged + " audit events older than " + days + " days.");
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadPlugin();
        storage.record(AuditRecord.system("CONFIG", "INFO", "RELOAD", "ZyroxProtect reloaded.", Map.of("actor", sender.getName())));
        sender.sendMessage("ZyroxProtect reloaded.");
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("/" + label + " status");
        sender.sendMessage("/" + label + " setup-password <newPassword>  (console only)");
        sender.sendMessage("/" + label + " export [json|csv]");
        sender.sendMessage("/" + label + " purge-expired [days]");
        sender.sendMessage("/" + label + " reload");
    }
}
