package de.tecca.zyrox.api.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.config.ConfigUpdater;
import de.tecca.zyrox.api.internal.SimpleZyroxActionRegistry;
import de.tecca.zyrox.api.internal.SimpleZyroxApi;
import de.tecca.zyrox.api.internal.SimpleZyroxDashboardViewRegistry;
import de.tecca.zyrox.api.internal.SimpleZyroxModuleRegistry;
import de.tecca.zyrox.api.internal.SimpleZyroxPermissionService;
import de.tecca.zyrox.api.internal.SimpleZyroxServiceRegistry;
import de.tecca.zyrox.api.internal.SimpleZyroxSettingRegistry;
import de.tecca.zyrox.api.internal.command.ZyroxCommand;
import de.tecca.zyrox.api.internal.web.SimpleZyroxWebPanel;
import de.tecca.zyrox.api.internal.web.WebSessionStore;
import de.tecca.zyrox.api.module.ZyroxModule;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

import java.time.Duration;
import java.util.List;

public final class ZyroxApiPlugin extends JavaPlugin {

    private ZyroxApi api;
    private SimpleZyroxWebPanel webPanel;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this, "config.yml");

        SimpleZyroxServiceRegistry serviceRegistry = new SimpleZyroxServiceRegistry();
        SimpleZyroxModuleRegistry moduleRegistry = new SimpleZyroxModuleRegistry();
        SimpleZyroxActionRegistry actionRegistry = new SimpleZyroxActionRegistry();
        SimpleZyroxSettingRegistry settingRegistry = new SimpleZyroxSettingRegistry();
        SimpleZyroxDashboardViewRegistry dashboardViewRegistry = new SimpleZyroxDashboardViewRegistry();
        SimpleZyroxPermissionService permissionService = new SimpleZyroxPermissionService(this);
        WebSessionStore webSessionStore = new WebSessionStore();

        webPanel = new SimpleZyroxWebPanel(
                this,
                getConfig().getString("web.bind-address", "127.0.0.1"),
                getConfig().getInt("web.port", 8080),
                getConfig().getString("web.public-url", "http://localhost:8080"),
                Duration.ofMinutes(getConfig().getLong("web.token-expiry-minutes", 10)),
                webSessionStore
        );

        api = new SimpleZyroxApi(
                getDescription().getVersion(),
                serviceRegistry,
                moduleRegistry,
                actionRegistry,
                settingRegistry,
                webPanel,
                dashboardViewRegistry,
                permissionService
        );

        registerSelfModule(moduleRegistry, actionRegistry);

        try {
            webPanel.start(api);
        } catch (Exception exception) {
            getLogger().severe("Failed to start ZyroxAPI dashboard server: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getServicesManager().register(ZyroxApi.class, api, this, ServicePriority.Normal);
        registerCommand();
        getLogger().info("ZyroxAPI is enabled.");
    }

    @Override
    public void onDisable() {
        if (webPanel != null) {
            webPanel.stop();
            webPanel = null;
        }

        getServer().getServicesManager().unregisterAll(this);
        api = null;

        getLogger().info("ZyroxAPI is disabled.");
    }

    private void registerSelfModule(SimpleZyroxModuleRegistry moduleRegistry, SimpleZyroxActionRegistry actionRegistry) {
        moduleRegistry.register(new ZyroxModule(
                "zyrox-api",
                "ZyroxAPI",
                getDescription().getVersion(),
                "Central platform API and dashboard for ZyroxMC plugins.",
                "zyrox.web.open"
        ));

        actionRegistry.register("zyrox-api", new ZyroxAction(
                "status",
                "Show API Status",
                "Returns basic ZyroxAPI runtime status.",
                "zyrox.web.open",
                List.of(),
                context -> ZyroxActionResult.success("ZyroxAPI " + getDescription().getVersion() + " is running.")
        ));
    }

    private void registerCommand() {
        PluginCommand command = getCommand("dashboard");
        if (command == null) {
            getLogger().warning("Command /dashboard is missing from plugin.yml.");
            return;
        }

        ZyroxCommand executor = new ZyroxCommand(api, getConfig().getString("web.command-permission", "zyrox.web.open"));
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
