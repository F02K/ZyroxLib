package de.tecca.zyrox.enchants.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.enchants.command.ZyroxEnchantsCommand;
import de.tecca.zyrox.enchants.service.EnchantBookService;
import de.tecca.zyrox.enchants.text.EnchantText;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ZyroxEnchantsPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-enchants";

    private ZyroxApi zyroxApi;
    private EnchantBookService enchants;
    private EnchantText text;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        enchants = new EnchantBookService();
        text = new EnchantText(this);

        registerCommand();

        zyroxApi = loadZyroxApi();
        if (zyroxApi != null) {
            registerZyroxApiIntegrations();
        } else {
            getLogger().info("ZyroxAPI was not found. Dashboard integrations are disabled.");
        }

        getLogger().info("ZyroxEnchants is enabled.");
    }

    @Override
    public void onDisable() {
        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }
        getLogger().info("ZyroxEnchants is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        text = new EnchantText(this);
    }

    public EnchantBookService enchants() {
        return enchants;
    }

    public EnchantText text() {
        return text;
    }

    public int maxLevel() {
        return Math.max(1, getConfig().getInt("enchanting.max-level", 10));
    }

    public String givePermission() {
        return getConfig().getString("permissions.give", "zyrox.enchants.give");
    }

    public String reloadPermission() {
        return getConfig().getString("permissions.reload", "zyrox.enchants.reload");
    }

    public String webPermission() {
        return getConfig().getString("permissions.web", "zyrox.enchants.web");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("zenchants");
        if (command == null) {
            getLogger().warning("Command /zenchants is missing from plugin.yml.");
            return;
        }

        ZyroxEnchantsCommand executor = new ZyroxEnchantsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerZyroxApiIntegrations() {
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxEnchants",
                getDescription().getVersion(),
                "Command-only overleveled vanilla enchanted books.",
                webPermission()
        ));
        registerSettings();
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload Enchants",
                "Reloads ZyroxEnchants config.",
                reloadPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxEnchants reloaded.");
                }
        ));
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.modules().unregister(MODULE_ID);
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        unregisterSettings();
    }

    private void registerSettings() {
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("enchanting", "Enchanting", 10));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("permissions", "Permissions", 20));

        registerSetting("max-level", "Max Level", "Maximum level allowed for command-created books.", "enchanting", ZyroxSettingType.INTEGER, "enchanting.max-level", true, 10, false);
        registerSetting("permission-give", "Give Permission", "Permission required to give enchanted books.", "permissions", ZyroxSettingType.STRING, "permissions.give", true, 10, true);
        registerSetting("permission-reload", "Reload Permission", "Permission required to reload ZyroxEnchants.", "permissions", ZyroxSettingType.STRING, "permissions.reload", true, 20, true);
        registerSetting("permission-web", "Web Permission", "Permission required to see the module.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, 30, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of("max-level", "permission-give", "permission-reload", "permission-web")) {
            zyroxApi.settings().unregister(MODULE_ID, settingId);
        }
        zyroxApi.settings().unregisterCategory(MODULE_ID, "enchanting");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "permissions");
    }

    private void registerSetting(
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
                reloadPermission(),
                order,
                restartRequired,
                () -> type == ZyroxSettingType.INTEGER ? getConfig().getInt(configPath, 10) : getConfig().getString(configPath, ""),
                value -> saveConfigValue(configPath, type, value)
        ));
    }

    private ZyroxSettingResult saveConfigValue(String configPath, ZyroxSettingType type, Object value) {
        if (type == ZyroxSettingType.INTEGER) {
            try {
                int parsed = Integer.parseInt(String.valueOf(value));
                if (parsed < 1) {
                    return ZyroxSettingResult.failure("Max level must be at least 1.");
                }
                getConfig().set(configPath, parsed);
            } catch (NumberFormatException exception) {
                return ZyroxSettingResult.failure("Max level must be a whole number.");
            }
        } else {
            getConfig().set(configPath, value);
        }

        saveConfig();
        reloadPlugin();
        return ZyroxSettingResult.success("Setting saved.");
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }
}
