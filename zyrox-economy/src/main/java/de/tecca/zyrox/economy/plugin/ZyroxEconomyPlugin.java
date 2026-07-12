package de.tecca.zyrox.economy.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.api.web.ZyroxDashboardView;
import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.economy.command.ZyroxEconomyCommand;
import de.tecca.zyrox.economy.service.DiamondEconomyService;
import de.tecca.zyrox.economy.service.EconomyFormatter;
import de.tecca.zyrox.economy.storage.EconomyStorage;
import de.tecca.zyrox.economy.web.ZyroxEconomyDashboardRenderer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class ZyroxEconomyPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-economy";
    private static final String VIEW_ID = "zyrox-economy-payments";

    private ZyroxApi zyroxApi;
    private EconomyStorage storage;
    private DiamondEconomyService economyService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        zyroxApi = loadZyroxApi();
        if (zyroxApi == null) {
            getLogger().severe("ZyroxAPI is required but was not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            storage = new EconomyStorage(this, storageFile());
            storage.open();
            warnIfLegacyFractionalBalancesExist();
        } catch (SQLException exception) {
            getLogger().severe("Could not open ZyroxEconomy legacy storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyService = new DiamondEconomyService(this);
        registerCommand();
        registerZyroxApiIntegrations();

        getLogger().info("ZyroxEconomy is enabled.");
    }

    @Override
    public void onDisable() {
        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }

        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close ZyroxEconomy legacy storage: " + exception.getMessage());
            }
        }

        getLogger().info("ZyroxEconomy is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        economyService.reload();
        registerDashboardView();
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.economy.admin");
    }

    public DiamondEconomyService economyService() {
        return economyService;
    }

    private void registerCommand() {
        PluginCommand command = getCommand("economy");
        if (command == null) {
            getLogger().warning("Command /economy is missing from plugin.yml.");
            return;
        }

        ZyroxEconomyCommand executor = new ZyroxEconomyCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerZyroxApiIntegrations() {
        String webPermission = getConfig().getString("permissions.web", "zyrox.economy.web");
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxEconomy",
                getDescription().getVersion(),
                "Physical inventory-diamond payments for ZyroxMC shops.",
                webPermission
        ));

        zyroxApi.services().register(ZyroxDiamondPaymentService.class, economyService);
        registerDashboardView();
        registerSettings();
        registerActions();
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.modules().unregister(MODULE_ID);
        zyroxApi.services().unregister(ZyroxDiamondPaymentService.class);
        zyroxApi.dashboardViews().unregister(VIEW_ID);
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        unregisterSettings();
    }

    private void registerDashboardView() {
        zyroxApi.dashboardViews().unregister(VIEW_ID);
        zyroxApi.dashboardViews().register(new ZyroxDashboardView(
                VIEW_ID,
                "Diamond Payments",
                getConfig().getString("permissions.web", "zyrox.economy.web"),
                context -> new ZyroxEconomyDashboardRenderer(economyService.material()).render()
        ));
    }

    private void registerActions() {
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload Economy",
                "Reloads ZyroxEconomy config.",
                adminPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxEconomy reloaded.");
                }
        ));
    }

    private void registerSettings() {
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("economy", "Economy", 10));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("storage", "Storage", 20));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("permissions", "Permissions", 30));

        registerStringSetting("economy-material", "Material", "Physical inventory item used as money.", "economy", ZyroxSettingType.STRING, "economy.material", true, 10, true);
        registerStringSetting("storage-sqlite-file", "SQLite File", "Legacy SQLite database path kept for old ledger review.", "storage", ZyroxSettingType.STRING, "storage.sqlite-file", true, 10, true);
        registerStringSetting("permission-admin", "Admin Permission", "Permission required to manage ZyroxEconomy.", "permissions", ZyroxSettingType.STRING, "permissions.admin", true, 20, true);
        registerStringSetting("permission-web", "Web Permission", "Permission required to view ZyroxEconomy in the dashboard.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, 30, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of(
                "economy-material",
                "storage-sqlite-file",
                "permission-admin",
                "permission-web"
        )) {
            zyroxApi.settings().unregister(MODULE_ID, settingId);
        }
        zyroxApi.settings().unregisterCategory(MODULE_ID, "economy");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "storage");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "permissions");
    }

    private void registerStringSetting(
            String id,
            String label,
            String description,
            String categoryId,
            ZyroxSettingType type,
            String configPath,
            boolean required,
            int order,
            boolean restartRequired
    ) {
        zyroxApi.settings().register(MODULE_ID, new ZyroxSetting(
                id,
                label,
                description,
                categoryId,
                type,
                required,
                adminPermission(),
                order,
                restartRequired,
                () -> getConfig().getString(configPath, ""),
                value -> saveConfigValue(configPath, value)
        ));
    }

    private ZyroxSettingResult saveConfigValue(String configPath, Object value) {
        getConfig().set(configPath, value);
        saveConfig();
        reloadPlugin();
        return ZyroxSettingResult.success("Setting saved.");
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private void warnIfLegacyFractionalBalancesExist() throws SQLException {
        if (storage.hasAnyLedgerBalances()) {
            getLogger().warning("Legacy ZyroxEconomy ledger balances are present. New shops use physical diamonds only.");
        }
        if (storage.hasFractionalBalances(EconomyFormatter.DIAMOND_UNIT)) {
            getLogger().warning("Legacy ZyroxEconomy ledger contains non-whole diamond balances.");
        }
    }

    private File storageFile() {
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-economy.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }
}
