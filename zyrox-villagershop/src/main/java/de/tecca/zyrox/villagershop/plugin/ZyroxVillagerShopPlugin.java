package de.tecca.zyrox.villagershop.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.config.ConfigUpdater;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.villagershop.command.VillagerShopCommand;
import de.tecca.zyrox.villagershop.listener.VillagerShopListener;
import de.tecca.zyrox.villagershop.service.VillagerShopService;
import de.tecca.zyrox.villagershop.storage.VillagerShopStorage;
import de.tecca.zyrox.villagershop.text.VillagerShopText;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class ZyroxVillagerShopPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-villagershop";

    private ZyroxApi zyroxApi;
    private VillagerShopStorage storage;
    private VillagerShopText text;
    private VillagerShopService shopService;

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
            storage = new VillagerShopStorage(storageFile());
            storage.open();
        } catch (SQLException exception) {
            getLogger().severe("Could not open ZyroxVillagerShop storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        text = new VillagerShopText(this);
        shopService = new VillagerShopService(this, storage, text);

        registerZyroxApiIntegrations();
        registerCommand();
        registerListeners();
        shopService.restoreAll();
        shopService.startLockTask();

        getLogger().info("ZyroxVillagerShop is enabled.");
    }

    @Override
    public void onDisable() {
        if (shopService != null) {
            shopService.stopLockTask();
        }
        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close ZyroxVillagerShop storage: " + exception.getMessage());
            }
        }
        getLogger().info("ZyroxVillagerShop is disabled.");
    }

    public void reloadPlugin() {
        ConfigUpdater.update(this, "config.yml");
        reloadConfig();
        if (shopService != null) {
            shopService.restoreAll();
            shopService.startLockTask();
        }
    }

    public VillagerShopText text() {
        return text;
    }

    public VillagerShopService shopService() {
        return shopService;
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.villagershop.admin");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("villagershop");
        if (command == null) {
            getLogger().warning("Command /villagershop is missing from plugin.yml.");
            return;
        }
        VillagerShopCommand executor = new VillagerShopCommand(this, shopService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new VillagerShopListener(this, shopService), this);
    }

    private void registerZyroxApiIntegrations() {
        String webPermission = getConfig().getString("permissions.web", "zyrox.villagershop.web");
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxVillagerShop",
                getDescription().getVersion(),
                "Admin-managed locked villagers with custom native trades.",
                webPermission
        ));
        registerSettings();
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload VillagerShop",
                "Reloads ZyroxVillagerShop config and reapplies managed villagers.",
                adminPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxVillagerShop reloaded.");
                }
        ));
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        unregisterSettings();
        zyroxApi.modules().unregister(MODULE_ID);
    }

    private void registerSettings() {
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("storage", "Storage", 10));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("villager-shop", "Villager Shop", 20));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("permissions", "Permissions", 30));

        registerStringSetting("storage-sqlite-file", "SQLite File", "SQLite database path used for villager shops.", "storage", ZyroxSettingType.STRING, "storage.sqlite-file", true, 10, true);
        registerStringSetting("default-name", "Default Name", "Display name for newly spawned villager shops.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.default-name", true, 10, false);
        registerStringSetting("default-profession", "Default Profession", "Profession for newly spawned villager shops.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.default-profession", true, 20, false);
        registerStringSetting("default-input-material", "Default Input", "Material used as the default first input.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.default-input-material", true, 30, false);
        registerStringSetting("default-output-material", "Default Output", "Material used as the default output.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.default-output-material", true, 40, false);
        registerStringSetting("default-max-uses", "Default Max Uses", "Default maximum uses for limited new trades.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.default-max-uses", true, 50, false);
        registerStringSetting("display-offset-y", "Display Y Offset", "Vertical offset for the text above villager shops.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.display-offset-y", true, 60, false);
        registerStringSetting("lock-period-ticks", "Lock Period", "Ticks between lock enforcement runs.", "villager-shop", ZyroxSettingType.STRING, "villager-shop.lock-period-ticks", true, 70, false);
        registerStringSetting("permission-admin", "Admin Permission", "Permission required to manage villager shops.", "permissions", ZyroxSettingType.STRING, "permissions.admin", true, 10, true);
        registerStringSetting("permission-web", "Web Permission", "Permission required to see this module.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, 20, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of(
                "storage-sqlite-file",
                "default-name",
                "default-profession",
                "default-input-material",
                "default-output-material",
                "default-max-uses",
                "display-offset-y",
                "lock-period-ticks",
                "permission-admin",
                "permission-web"
        )) {
            zyroxApi.settings().unregister(MODULE_ID, settingId);
        }
        zyroxApi.settings().unregisterCategory(MODULE_ID, "storage");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "villager-shop");
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

    private File storageFile() {
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-villagershop.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }
}
