package de.tecca.zyrox.protect.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionParameter;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.action.ZyroxActionSource;
import de.tecca.zyrox.api.audit.ZyroxAuditSink;
import de.tecca.zyrox.api.config.ConfigUpdater;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.web.ZyroxDashboardView;
import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpoint;
import de.tecca.zyrox.protect.audit.AuditExporter;
import de.tecca.zyrox.protect.audit.AuditQuery;
import de.tecca.zyrox.protect.audit.AuditRecord;
import de.tecca.zyrox.protect.audit.AuditStorage;
import de.tecca.zyrox.protect.auth.ProtectAuthService;
import de.tecca.zyrox.protect.command.ZyroxProtectCommand;
import de.tecca.zyrox.protect.listener.SecurityEventListener;
import de.tecca.zyrox.protect.luckperms.LuckPermsAuditBridge;
import de.tecca.zyrox.protect.packet.PacketAuditBridge;
import de.tecca.zyrox.protect.web.ProtectEndpointController;
import de.tecca.zyrox.protect.web.ZyroxProtectDashboardRenderer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ZyroxProtectPlugin extends JavaPlugin implements ZyroxAuditSink {

    private static final String MODULE_ID = "zyrox-protect";
    private static final String VIEW_ID = "zyrox-protect-audit";

    private ZyroxApi zyroxApi;
    private AuditStorage storage;
    private ProtectAuthService authService;
    private PacketAuditBridge packetAuditBridge;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this, "config.yml");

        zyroxApi = loadZyroxApi();
        if (zyroxApi == null) {
            getLogger().severe("ZyroxAPI is required but was not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            File file = storageFile();
            storage = new AuditStorage(this, file);
            storage.open();
            authService = new ProtectAuthService(this, file);
            authService.open();
            reloadAuthSettings();
        } catch (SQLException exception) {
            getLogger().severe("Could not open ZyroxProtect storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerZyroxApiIntegrations();
        registerCommand();
        registerListeners();
        new LuckPermsAuditBridge(this, storage).register();
        packetAuditBridge = new PacketAuditBridge(this, storage, getConfig().getInt("audit.packet-rate-limit-per-minute", 30));
        packetAuditBridge.register();

        storage.record(AuditRecord.system("PLUGIN", "INFO", "ENABLED", "ZyroxProtect enabled.", Map.of("version", getDescription().getVersion())));
        getLogger().info("ZyroxProtect is enabled.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.record(AuditRecord.system("PLUGIN", "INFO", "DISABLED", "ZyroxProtect disabled.", Map.of()));
        }
        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }
        closeQuietly(authService);
        closeQuietly(storage);
        getLogger().info("ZyroxProtect is disabled.");
    }

    public void reloadPlugin() {
        ConfigUpdater.update(this, "config.yml");
        reloadConfig();
        reloadAuthSettings();
        if (packetAuditBridge != null) {
            packetAuditBridge.reload(getConfig().getInt("audit.packet-rate-limit-per-minute", 30));
        }
    }

    @Override
    public void recordAdminEvent(
            String category,
            String severity,
            String action,
            String actorSource,
            Optional<UUID> actorUniqueId,
            String actorName,
            String targetType,
            String targetName,
            String summary,
            Map<String, String> details
    ) {
        storage.record(new AuditRecord(
                category,
                severity,
                actorSource,
                actorUniqueId,
                actorName,
                targetType,
                "",
                targetName,
                action,
                summary,
                details,
                ""
        ));
    }

    private void registerZyroxApiIntegrations() {
        String adminPermission = adminPermission();
        String webPermission = webPermission();
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxProtect",
                getDescription().getVersion(),
                "High-signal security audit logging for ZyroxMC.",
                webPermission
        ));
        zyroxApi.dashboardViews().register(new ZyroxDashboardView(
                VIEW_ID,
                "Security Audit",
                webPermission,
                context -> new ZyroxProtectDashboardRenderer().render()
        ));
        ProtectEndpointController endpoints = new ProtectEndpointController(storage, authService);
        zyroxApi.dashboardEndpoints().register(MODULE_ID, new ZyroxDashboardEndpoint("status", webPermission, endpoints::status));
        zyroxApi.dashboardEndpoints().register(MODULE_ID, new ZyroxDashboardEndpoint("unlock", webPermission, endpoints::unlock));
        zyroxApi.dashboardEndpoints().register(MODULE_ID, new ZyroxDashboardEndpoint("lock", webPermission, endpoints::lock));
        zyroxApi.dashboardEndpoints().register(MODULE_ID, new ZyroxDashboardEndpoint("events", webPermission, endpoints::events));
        zyroxApi.dashboardEndpoints().register(MODULE_ID, new ZyroxDashboardEndpoint("export", webPermission, endpoints::export));
        zyroxApi.services().register(ZyroxAuditSink.class, this);
        registerActions(adminPermission);
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.modules().unregister(MODULE_ID);
        zyroxApi.dashboardViews().unregister(VIEW_ID);
        for (String endpoint : List.of("status", "unlock", "lock", "events", "export")) {
            zyroxApi.dashboardEndpoints().unregister(MODULE_ID, endpoint);
        }
        for (String action : List.of("setup-password", "lock", "unlock-status", "export", "purge-expired", "reload")) {
            zyroxApi.actions().unregister(MODULE_ID, action);
        }
        zyroxApi.services().unregister(ZyroxAuditSink.class);
    }

    private void registerActions(String adminPermission) {
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "setup-password",
                "Setup Audit Password",
                "Console-only password setup for ZyroxProtect dashboard unlock.",
                adminPermission,
                List.of(ZyroxActionParameter.string("password", "Password", true)),
                context -> {
                    if (context.source() != ZyroxActionSource.COMMAND || context.actorUniqueId().isPresent()) {
                        return ZyroxActionResult.failure("Password setup is console-only.");
                    }
                    authService.setPassword(require(context.parameters().get("password")).toCharArray());
                    storage.record(AuditRecord.system("AUTH", "CRITICAL", "PASSWORD_SET", "ZyroxProtect password was set through console action.", Map.of()));
                    return ZyroxActionResult.success("ZyroxProtect password saved.");
                }
        ));
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "lock",
                "Lock Audit Dashboard",
                "Locks the current dashboard session through the web interface.",
                adminPermission,
                List.of(),
                context -> ZyroxActionResult.success("Use the ZyroxProtect dashboard Lock button to lock a web session.")
        ));
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "unlock-status",
                "Unlock Status",
                "Shows password configuration and audit chain status.",
                adminPermission,
                List.of(),
                context -> ZyroxActionResult.success("Password configured: " + authService.isPasswordConfigured()
                        + ", " + storage.verifyHashChain().message())
        ));
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "export",
                "Export Recent Audit",
                "Exports recent audit events as JSON in the command result.",
                adminPermission,
                List.of(),
                context -> ZyroxActionResult.success(new AuditExporter().json(storage.query(AuditQuery.recent(25)), storage.verifyHashChain()))
        ));
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "purge-expired",
                "Purge Expired Audit Events",
                "Purges events older than the configured retention days.",
                adminPermission,
                List.of(),
                context -> {
                    int days = getConfig().getInt("audit.retained-days", 0);
                    int purged = storage.purgeOlderThanDays(days);
                    storage.record(AuditRecord.system("AUDIT", "WARNING", "PURGE", "Purged old audit events.", Map.of("days", Integer.toString(days), "purged", Integer.toString(purged))));
                    return ZyroxActionResult.success("Purged " + purged + " audit events.");
                }
        ));
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload Protect",
                "Reloads ZyroxProtect config.",
                adminPermission,
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxProtect reloaded.");
                }
        ));
    }

    private void registerCommand() {
        PluginCommand command = getCommand("protect");
        if (command == null) {
            getLogger().warning("Command /protect is missing from plugin.yml.");
            return;
        }
        ZyroxProtectCommand executor = new ZyroxProtectCommand(this, storage, authService, adminPermission());
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SecurityEventListener(storage, sensitiveCommands()), this);
    }

    private void reloadAuthSettings() {
        if (authService == null) {
            return;
        }
        authService.reload(
                Duration.ofMinutes(getConfig().getLong("auth.unlock-ttl-minutes", 15L)),
                Duration.ofSeconds(getConfig().getLong("auth.failed-attempt-window-seconds", 60L)),
                getConfig().getInt("auth.max-failed-attempts", 5)
        );
    }

    private Set<String> sensitiveCommands() {
        return getConfig().getStringList("audit.sensitive-commands").stream()
                .filter(Objects::nonNull)
                .map(command -> command.toLowerCase(java.util.Locale.ROOT).trim())
                .filter(command -> !command.isBlank())
                .collect(Collectors.toSet());
    }

    private String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required value.");
        }
        return value;
    }

    private String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.protect.admin");
    }

    private String webPermission() {
        return getConfig().getString("permissions.web", "zyrox.protect.web");
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private File storageFile() {
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-protect.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            getLogger().warning("Could not close ZyroxProtect resource: " + exception.getMessage());
        }
    }
}
