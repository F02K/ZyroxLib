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
import de.tecca.zyrox.economy.command.ZyroxEconomyCommand;
import de.tecca.zyrox.economy.service.DiamondEconomyService;
import de.tecca.zyrox.economy.storage.EconomyStorage;
import de.tecca.zyrox.economy.vault.ZyroxVaultEconomy;
import de.tecca.zyrox.economy.web.ZyroxEconomyDashboardRenderer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class ZyroxEconomyPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-economy";
    private static final String VIEW_ID = "zyrox-economy-ledger";

    private ZyroxApi zyroxApi;
    private EconomyStorage storage;
    private DiamondEconomyService economyService;
    private ZyroxVaultEconomy vaultEconomy;

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
            getLogger().severe("Could not open ZyroxEconomy storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyService = new DiamondEconomyService(this, storage);
        vaultEconomy = new ZyroxVaultEconomy(this, economyService);

        registerVaultEconomy();
        registerCommand();
        registerZyroxApiIntegrations();

        getLogger().info("ZyroxEconomy is enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);

        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }

        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close ZyroxEconomy storage: " + exception.getMessage());
            }
        }

        getLogger().info("ZyroxEconomy is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        economyService.reload();
        registerDashboardView();
    }

    public String usePermission() {
        return getConfig().getString("permissions.use", "zyrox.economy.use");
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.economy.admin");
    }

    public EconomyStorage storage() {
        return storage;
    }

    public DiamondEconomyService economyService() {
        return economyService;
    }

    private void registerVaultEconomy() {
        getServer().getServicesManager().register(Economy.class, vaultEconomy, this, ServicePriority.Highest);
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
                "Diamond-backed Vault economy for ZyroxMC shops.",
                webPermission
        ));

        registerDashboardView();
        registerSettings();
        registerActions();
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.modules().unregister(MODULE_ID);
        zyroxApi.dashboardViews().unregister(VIEW_ID);
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        unregisterSettings();
    }

    private void registerDashboardView() {
        zyroxApi.dashboardViews().unregister(VIEW_ID);
        zyroxApi.dashboardViews().register(new ZyroxDashboardView(
                VIEW_ID,
                "Economy Ledger",
                getConfig().getString("permissions.web", "zyrox.economy.web"),
                context -> new ZyroxEconomyDashboardRenderer(storage, economyService.material()).render()
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

        registerStringSetting("economy-material", "Material", "Inventory item used as Vault cash.", "economy", ZyroxSettingType.STRING, "economy.material", true, 10, true);
        registerStringSetting("economy-debug-vault", "Debug Vault", "Logs Vault economy calls for shop diagnostics.", "economy", ZyroxSettingType.BOOLEAN, "economy.debug-vault", false, 20, false);
        registerStringSetting("storage-sqlite-file", "SQLite File", "SQLite database path used for the economy ledger.", "storage", ZyroxSettingType.STRING, "storage.sqlite-file", true, 10, true);
        registerStringSetting("permission-use", "Use Permission", "Permission required to use economy player commands.", "permissions", ZyroxSettingType.STRING, "permissions.use", true, 10, true);
        registerStringSetting("permission-admin", "Admin Permission", "Permission required to manage ZyroxEconomy.", "permissions", ZyroxSettingType.STRING, "permissions.admin", true, 20, true);
        registerStringSetting("permission-web", "Web Permission", "Permission required to view ZyroxEconomy in the dashboard.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, 30, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of(
                "economy-material",
                "economy-debug-vault",
                "storage-sqlite-file",
                "permission-use",
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
                () -> readConfigValue(type, configPath),
                value -> saveConfigValue(configPath, typedValue(type, value))
        ));
    }

    private Object readConfigValue(ZyroxSettingType type, String configPath) {
        if (type == ZyroxSettingType.BOOLEAN) {
            return getConfig().getBoolean(configPath, false);
        }
        return getConfig().getString(configPath, "");
    }

    private ZyroxSettingResult saveConfigValue(String configPath, Object value) {
        getConfig().set(configPath, value);
        saveConfig();
        reloadPlugin();
        return ZyroxSettingResult.success("Setting saved.");
    }

    private Object typedValue(ZyroxSettingType type, String value) {
        if (type == ZyroxSettingType.BOOLEAN) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private void warnIfLegacyFractionalBalancesExist() throws SQLException {
        if (storage.hasAnyLedgerBalances()) {
            getLogger().warning("Existing ZyroxEconomy ledger balances are present. "
                    + "Review them if players already triggered the old shop payout bug.");
        }
        if (storage.hasFractionalBalances(de.tecca.zyrox.economy.service.EconomyFormatter.DIAMOND_UNIT)) {
            getLogger().warning("Existing ZyroxEconomy ledger contains non-whole diamond balances. "
                    + "Whole-diamond cashout will ignore fractional leftovers until they are reviewed manually.");
        }
    }

    private File storageFile() {
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-economy.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }
}
