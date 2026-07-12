package de.tecca.zyrox.shop.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.economy.api.ZyroxDiamondPaymentService;
import de.tecca.zyrox.shop.command.ShopCommand;
import de.tecca.zyrox.shop.hologram.ShopHologramService;
import de.tecca.zyrox.shop.hologram.ShopHolograms;
import de.tecca.zyrox.shop.listener.ShopListener;
import de.tecca.zyrox.shop.service.ShopService;
import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopStorage;
import de.tecca.zyrox.shop.text.ShopText;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class ZyroxShopPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-shop";

    private ZyroxApi zyroxApi;
    private ZyroxDiamondPaymentService payments;
    private ShopStorage storage;
    private ShopHologramService holograms;
    private ShopService shopService;
    private ShopText text;
    private BukkitTask hologramRepairTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        zyroxApi = loadZyroxApi();
        if (zyroxApi == null) {
            getLogger().severe("ZyroxAPI is required but was not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        payments = zyroxApi.services().get(ZyroxDiamondPaymentService.class).orElse(null);
        if (payments == null) {
            getLogger().severe("ZyroxEconomy payment service is required but was not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            storage = new ShopStorage(this, storageFile());
            storage.open();
        } catch (SQLException exception) {
            getLogger().severe("Could not open ZyroxShop storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        text = new ShopText(this, payments);
        holograms = ShopHolograms.create(this, text);
        shopService = new ShopService(this, storage, payments, holograms, text);

        registerZyroxApiIntegrations();
        registerCommand();
        registerListeners();
        restoreHolograms();
        startHologramRepairTask();

        getLogger().info("ZyroxShop is enabled.");
    }

    @Override
    public void onDisable() {
        if (hologramRepairTask != null) {
            hologramRepairTask.cancel();
            hologramRepairTask = null;
        }
        if (holograms != null) {
            holograms.clear();
        }
        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close ZyroxShop storage: " + exception.getMessage());
            }
        }
        getLogger().info("ZyroxShop is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (hologramRepairTask != null) {
            hologramRepairTask.cancel();
        }
        if (holograms != null) {
            holograms.clear();
        }
        text = new ShopText(this, payments);
        holograms = ShopHolograms.create(this, text);
        shopService = new ShopService(this, storage, payments, holograms, text);
        restoreHolograms();
        startHologramRepairTask();
    }

    public ShopService shopService() {
        return shopService;
    }

    public ShopText text() {
        return text;
    }

    public String usePermission() {
        return getConfig().getString("permissions.use", "zyrox.shop.use");
    }

    public String createPermission() {
        return getConfig().getString("permissions.create", "zyrox.shop.create");
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.shop.admin");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("shop");
        if (command == null) {
            getLogger().warning("Command /shop is missing from plugin.yml.");
            return;
        }
        ShopCommand executor = new ShopCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ShopListener(this, shopService, payments), this);
    }

    private void registerZyroxApiIntegrations() {
        String webPermission = getConfig().getString("permissions.web", "zyrox.shop.web");
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxShop",
                getDescription().getVersion(),
                "Virtual-stock chest shops paid with physical diamonds.",
                webPermission
        ));
        registerSettings();
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload Shop",
                "Reloads ZyroxShop config and holograms.",
                adminPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxShop reloaded.");
                }
        ));
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.modules().unregister(MODULE_ID);
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        unregisterSettings();
    }

    private void registerSettings() {
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("storage", "Storage", 10));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("shop", "Shop", 20));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("holograms", "Holograms", 30));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("permissions", "Permissions", 40));

        registerStringSetting("storage-sqlite-file", "SQLite File", "SQLite database path used for shops.", "storage", ZyroxSettingType.STRING, "storage.sqlite-file", true, 10, true);
        registerStringSetting("shop-default-price", "Default Price", "Default diamond price for new shops.", "shop", ZyroxSettingType.STRING, "shop.default-price-diamonds", true, 10, false);
        registerStringSetting("shop-hologram-text-offset-y", "Hologram Text Y Offset", "Vertical hologram text offset above the chest.", "shop", ZyroxSettingType.STRING, "shop.hologram-text-offset-y", true, 20, false);
        registerStringSetting("shop-hologram-text-scale", "Hologram Text Scale", "Scale multiplier for TextDisplay hologram text.", "shop", ZyroxSettingType.STRING, "shop.hologram-text-scale", true, 30, false);
        registerStringSetting("shop-item-display-offset-y", "Item Display Y Offset", "Vertical item display offset above the chest.", "shop", ZyroxSettingType.STRING, "shop.item-display-offset-y", true, 40, false);
        registerStringSetting("shop-item-display-scale", "Item Display Scale", "Scale multiplier for the displayed shop item.", "shop", ZyroxSettingType.STRING, "shop.item-display-scale", true, 50, false);
        registerStringSetting("holograms-backend", "Backend", "auto, decent, textdisplay, or none.", "holograms", ZyroxSettingType.STRING, "holograms.backend", true, 10, false);
        registerStringSetting("permission-use", "Use Permission", "Permission required to buy from shops.", "permissions", ZyroxSettingType.STRING, "permissions.use", true, 10, true);
        registerStringSetting("permission-create", "Create Permission", "Permission required to create shops.", "permissions", ZyroxSettingType.STRING, "permissions.create", true, 20, true);
        registerStringSetting("permission-admin", "Admin Permission", "Permission required to administer shops.", "permissions", ZyroxSettingType.STRING, "permissions.admin", true, 30, true);
        registerStringSetting("permission-web", "Web Permission", "Permission required to see the module.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, 40, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of(
                "storage-sqlite-file",
                "shop-default-price",
                "shop-hologram-text-offset-y",
                "shop-hologram-text-scale",
                "shop-item-display-offset-y",
                "shop-item-display-scale",
                "holograms-backend",
                "permission-use",
                "permission-create",
                "permission-admin",
                "permission-web"
        )) {
            zyroxApi.settings().unregister(MODULE_ID, settingId);
        }
        zyroxApi.settings().unregisterCategory(MODULE_ID, "storage");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "shop");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "holograms");
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

    private void restoreHolograms() {
        try {
            for (ShopRecord shop : storage.allShops()) {
                holograms.upsert(shop);
            }
        } catch (SQLException exception) {
            getLogger().warning("Could not restore shop holograms: " + exception.getMessage());
        }
    }

    private void startHologramRepairTask() {
        hologramRepairTask = getServer().getScheduler().runTaskTimer(this, this::restoreHolograms, 20L * 60L, 20L * 300L);
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private File storageFile() {
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-shop.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }
}
