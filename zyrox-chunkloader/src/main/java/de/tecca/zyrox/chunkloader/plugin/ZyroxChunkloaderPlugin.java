package de.tecca.zyrox.chunkloader.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.config.ConfigUpdater;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.chunkloader.command.ChunkloaderCommand;
import de.tecca.zyrox.chunkloader.item.ChunkloaderItems;
import de.tecca.zyrox.chunkloader.listener.ChunkloaderListener;
import de.tecca.zyrox.chunkloader.service.ChunkloaderService;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderStorage;
import de.tecca.zyrox.chunkloader.text.ChunkloaderText;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class ZyroxChunkloaderPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-chunkloader";

    private ZyroxApi zyroxApi;
    private ChunkloaderStorage storage;
    private ChunkloaderItems items;
    private ChunkloaderText text;
    private ChunkloaderService chunkloaders;
    private BukkitTask statusTask;
    private BukkitTask visualTask;

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
            storage = new ChunkloaderStorage(this, storageFile());
            storage.open();
            int paused = storage.pausePaidTimeSinceLastShutdown();
            if (paused > 0) {
                getLogger().info("Paused paid chunkloader time across downtime for " + paused + " loader(s).");
            }
        } catch (SQLException exception) {
            getLogger().severe("Could not open ZyroxChunkloader storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        items = new ChunkloaderItems(this);
        text = new ChunkloaderText(this);
        chunkloaders = new ChunkloaderService(this, storage, items, text);

        registerRecipe();
        registerCommand();
        registerListeners();
        registerZyroxApiIntegrations();
        startTasks();
        chunkloaders.refreshAll();

        getLogger().info("ZyroxChunkloader is enabled.");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (chunkloaders != null) {
            chunkloaders.clearTickets();
        }
        getServer().removeRecipe(items == null ? new org.bukkit.NamespacedKey(this, "chunkloader_recipe") : items.recipeKey());
        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }
        if (storage != null) {
            try {
                storage.markShutdownTime();
                storage.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close ZyroxChunkloader storage: " + exception.getMessage());
            }
        }
        getLogger().info("ZyroxChunkloader is disabled.");
    }

    public void reloadPlugin() {
        ConfigUpdater.update(this, "config.yml");
        reloadConfig();
        stopTasks();
        if (chunkloaders != null) {
            chunkloaders.clearTickets();
        }
        items = new ChunkloaderItems(this);
        text = new ChunkloaderText(this);
        chunkloaders = new ChunkloaderService(this, storage, items, text);
        registerRecipe();
        startTasks();
        chunkloaders.refreshAll();
    }

    public ChunkloaderService chunkloaders() {
        return chunkloaders;
    }

    public ChunkloaderItems items() {
        return items;
    }

    public ChunkloaderText text() {
        return text;
    }

    public String usePermission() {
        return getConfig().getString("permissions.use", "zyrox.chunkloader.use");
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.chunkloader.admin");
    }

    private void registerRecipe() {
        getServer().removeRecipe(items.recipeKey());
        getServer().addRecipe(items.recipe());
    }

    private void registerCommand() {
        PluginCommand command = getCommand("chunkloader");
        if (command == null) {
            getLogger().warning("Command /chunkloader is missing from plugin.yml.");
            return;
        }
        ChunkloaderCommand executor = new ChunkloaderCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChunkloaderListener(this, chunkloaders, items), this);
    }

    private void startTasks() {
        long statusInterval = Math.max(1L, getConfig().getLong("chunkloader.status-task-interval-ticks", 20L));
        long visualInterval = Math.max(1L, getConfig().getLong("chunkloader.visual-task-interval-ticks", 20L));
        statusTask = getServer().getScheduler().runTaskTimer(this, () -> {
            chunkloaders.refreshAll();
            chunkloaders.sendActionBars();
        }, statusInterval, statusInterval);
        visualTask = getServer().getScheduler().runTaskTimer(this, () -> chunkloaders.spawnVisuals(), visualInterval, visualInterval);
    }

    private void stopTasks() {
        if (statusTask != null) {
            statusTask.cancel();
            statusTask = null;
        }
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
    }

    private void registerZyroxApiIntegrations() {
        String webPermission = getConfig().getString("permissions.web", "zyrox.chunkloader.web");
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxChunkloader",
                getDescription().getVersion(),
                "Diamond-block powered chunkloaders.",
                webPermission
        ));
        registerSettings();
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload Chunkloader",
                "Reloads ZyroxChunkloader config.",
                adminPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxChunkloader reloaded.");
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
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("chunkloader", "Chunkloader", 20));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("permissions", "Permissions", 30));

        registerStringSetting("storage-sqlite-file", "SQLite File", "SQLite database path used for chunkloaders.", "storage", "storage.sqlite-file", true, 10, true);
        registerStringSetting("chunkloader-block-material", "Block Material", "Block used for chunkloaders.", "chunkloader", "chunkloader.block-material", true, 10, true);
        registerStringSetting("chunkloader-base-cost", "Base Cost", "Diamond blocks per hour for rank 1.", "chunkloader", "chunkloader.base-cost-diamond-blocks-per-hour", true, 20, false);
        registerStringSetting("chunkloader-billing-cycle", "Billing Cycle Minutes", "Minutes per prepaid cycle.", "chunkloader", "chunkloader.billing-cycle-minutes", true, 30, false);
        registerStringSetting("permission-use", "Use Permission", "Permission required to place and use own chunkloaders.", "permissions", "permissions.use", true, 10, true);
        registerStringSetting("permission-admin", "Admin Permission", "Permission required to administer chunkloaders.", "permissions", "permissions.admin", true, 20, true);
        registerStringSetting("permission-web", "Web Permission", "Permission required to see the module.", "permissions", "permissions.web", true, 30, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of(
                "storage-sqlite-file",
                "chunkloader-block-material",
                "chunkloader-base-cost",
                "chunkloader-billing-cycle",
                "permission-use",
                "permission-admin",
                "permission-web"
        )) {
            zyroxApi.settings().unregister(MODULE_ID, settingId);
        }
        zyroxApi.settings().unregisterCategory(MODULE_ID, "storage");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "chunkloader");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "permissions");
    }

    private void registerStringSetting(
            String id,
            String label,
            String description,
            String categoryId,
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
                ZyroxSettingType.STRING,
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
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-chunkloader.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }
}
