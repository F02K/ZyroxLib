package de.tecca.zyrox.deepdark.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.api.web.ZyroxDashboardView;
import de.tecca.zyrox.deepdark.command.DeepDarkCommand;
import de.tecca.zyrox.deepdark.listener.DeepDarkPortalListener;
import de.tecca.zyrox.deepdark.portal.DeepDarkPortal;
import de.tecca.zyrox.deepdark.portal.DeepDarkPortalService;
import de.tecca.zyrox.deepdark.portal.PortalAxis;
import de.tecca.zyrox.deepdark.web.DeepDarkDashboardRenderer;
import de.tecca.zyrox.deepdark.world.DeepDarkWorldService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class ZyroxDeepDarkPlugin extends JavaPlugin {

    private static final String MODULE_ID = "zyrox-deepdark";
    private static final String VIEW_ID = "zyrox-deepdark-portals";

    private ZyroxApi zyroxApi;
    private DeepDarkWorldService worldService;
    private DeepDarkPortalService portalService;
    private DeepDarkDashboardRenderer dashboardRenderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensurePortalResource();

        zyroxApi = loadZyroxApi();
        if (zyroxApi == null) {
            getLogger().severe("ZyroxAPI is required but was not available.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        worldService = new DeepDarkWorldService(this);
        worldService.ensureWorld();

        portalService = new DeepDarkPortalService(this, worldService);
        portalService.load();
        portalService.startEffects();
        dashboardRenderer = new DeepDarkDashboardRenderer(this, portalService);

        registerCommand();
        registerListeners();
        registerZyroxApiIntegrations();

        getLogger().info("ZyroxDeepDark is enabled.");
    }

    @Override
    public void onDisable() {
        if (portalService != null) {
            portalService.save();
            portalService.stopEffects();
        }

        if (zyroxApi != null) {
            unregisterZyroxApiIntegrations();
        }

        getLogger().info("ZyroxDeepDark is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (portalService != null) {
            portalService.load();
        }
        worldService.ensureWorld();
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "zyrox.deepdark.admin");
    }

    public DeepDarkWorldService worldService() {
        return worldService;
    }

    public DeepDarkPortalService portalService() {
        return portalService;
    }

    private void registerCommand() {
        PluginCommand command = getCommand("deepdark");
        if (command == null) {
            getLogger().warning("Command /deepdark is missing from plugin.yml.");
            return;
        }

        DeepDarkCommand executor = new DeepDarkCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new DeepDarkPortalListener(portalService), this);
    }

    private void registerZyroxApiIntegrations() {
        String webPermission = getConfig().getString("permissions.web", "zyrox.deepdark.web");
        zyroxApi.modules().register(new ZyroxModule(
                MODULE_ID,
                "ZyroxDeepDark",
                getDescription().getVersion(),
                "Deep Dark dimension and Ancient City portal system for ZyroxMC.",
                webPermission
        ));

        zyroxApi.dashboardViews().register(new ZyroxDashboardView(
                VIEW_ID,
                "DeepDark Portals",
                webPermission,
                context -> dashboardRenderer.render()
        ));

        registerSettings();
        registerActions();
    }

    private void unregisterZyroxApiIntegrations() {
        zyroxApi.modules().unregister(MODULE_ID);
        zyroxApi.dashboardViews().unregister(VIEW_ID);
        zyroxApi.actions().unregister(MODULE_ID, "reload");
        zyroxApi.actions().unregister(MODULE_ID, "create-portal-here");
        unregisterSettings();
    }

    private void registerActions() {
        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "reload",
                "Reload DeepDark",
                "Reloads DeepDark config and portal storage.",
                adminPermission(),
                List.of(),
                context -> {
                    reloadPlugin();
                    return ZyroxActionResult.success("ZyroxDeepDark reloaded.");
                }
        ));

        zyroxApi.actions().register(MODULE_ID, new ZyroxAction(
                "create-portal-here",
                "Create Portal Here",
                "Creates a DeepDark portal at the web actor's current player location.",
                adminPermission(),
                List.of(),
                context -> {
                    if (context.actorUniqueId().isEmpty()) {
                        return ZyroxActionResult.failure("This action must be run by a player.");
                    }

                    Player player = Bukkit.getPlayer(context.actorUniqueId().get());
                    if (player == null) {
                        return ZyroxActionResult.failure("The actor is not online.");
                    }

                    PortalAxis axis = Math.abs(player.getLocation().getYaw()) % 180 > 45
                            && Math.abs(player.getLocation().getYaw()) % 180 < 135 ? PortalAxis.Z : PortalAxis.X;
                    DeepDarkPortal portal = portalService.createPortalAt(player.getLocation(), axis, "", true);
                    return ZyroxActionResult.success("Created DeepDark portal: " + portal.id());
                }
        ));
    }

    private void registerSettings() {
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("world", "World", 10));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("portal", "Portal", 20));
        zyroxApi.settings().registerCategory(MODULE_ID, new ZyroxSettingCategory("permissions", "Permissions", 30));

        registerStringSetting("world-name", "World Name", "Name of the generated DeepDark world.", "world", ZyroxSettingType.STRING, "world.name", true, 10, true);
        registerStringSetting("world-seed", "World Seed", "Seed used when the DeepDark world is first generated.", "world", ZyroxSettingType.INTEGER, "world.seed", true, 20, true);
        registerStringSetting("world-cavern-height", "Cavern Height", "Controls the vertical size of DeepDark caverns.", "world", ZyroxSettingType.INTEGER, "world.cavern-height", true, 30, true);
        registerStringSetting("world-hall-density", "Hall Density", "Controls how often large DeepDark halls appear.", "world", ZyroxSettingType.INTEGER, "world.hall-density", true, 40, true);
        registerStringSetting("world-tunnel-density", "Tunnel Density", "Controls how often side tunnels cut through the cavern mass.", "world", ZyroxSettingType.INTEGER, "world.tunnel-density", true, 50, true);
        registerStringSetting("world-floor-roughness", "Floor Roughness", "Controls how uneven cavern floors are.", "world", ZyroxSettingType.INTEGER, "world.floor-roughness", true, 60, true);
        registerStringSetting("world-ceiling-roughness", "Ceiling Roughness", "Controls how uneven cavern ceilings are.", "world", ZyroxSettingType.INTEGER, "world.ceiling-roughness", true, 70, true);
        registerStringSetting("world-sculk-density", "Sculk Density", "Controls how much sculk appears in patches.", "world", ZyroxSettingType.INTEGER, "world.sculk-density", true, 80, true);

        registerStringSetting("portal-cooldown", "Portal Cooldown", "Teleport cooldown in seconds.", "portal", ZyroxSettingType.INTEGER, "portal.cooldown-seconds", true, 10, false);
        registerBooleanSetting("portal-require-permission", "Require Permission", "Requires zyrox.deepdark.enter to use portals.", "portal", "portal.require-permission", 20);
        registerStringSetting("portal-activation-item", "Activation Item", "Material used to activate a Reinforced Deepslate frame.", "portal", ZyroxSettingType.STRING, "portal.activation-item", true, 30, false);
        registerBooleanSetting("portal-auto-return", "Auto Create Return Portal", "Automatically creates a linked return portal in the DeepDark world.", "portal", "portal.auto-create-return-portal", 40);
        registerStringSetting("portal-fill-material", "Fill Material", "Material used inside activated portals.", "portal", ZyroxSettingType.STRING, "portal.fill-material", true, 50, false);
        registerBooleanSetting("portal-prevent-nether", "Prevent Vanilla Nether Travel", "Cancels vanilla Nether portal travel for DeepDark portals.", "portal", "portal.prevent-vanilla-nether-travel", 60);
        registerBooleanSetting("portal-block-deepdark-nether", "Block Nether Portals In DeepDark", "Blocks vanilla Nether portal travel anywhere inside the DeepDark world.", "portal", "portal.deepdark-block-all-nether-portals", 70);
        registerStringSetting("portal-platform-radius", "Platform Radius", "Extra platform radius around auto-created DeepDark return portals.", "portal", ZyroxSettingType.INTEGER, "portal.platform-radius", true, 80, false);

        registerStringSetting("permission-admin", "Admin Permission", "Permission required to manage DeepDark.", "permissions", ZyroxSettingType.STRING, "permissions.admin", true, 10, true);
        registerStringSetting("permission-enter", "Enter Permission", "Permission required to enter DeepDark when enabled.", "permissions", ZyroxSettingType.STRING, "permissions.enter", true, 20, true);
        registerStringSetting("permission-web", "Web Permission", "Permission required to view DeepDark in the dashboard.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, 30, true);
    }

    private void unregisterSettings() {
        for (String settingId : List.of(
                "world-name",
                "world-seed",
                "world-cavern-height",
                "world-hall-density",
                "world-tunnel-density",
                "world-floor-roughness",
                "world-ceiling-roughness",
                "world-sculk-density",
                "portal-cooldown",
                "portal-require-permission",
                "portal-activation-item",
                "portal-auto-return",
                "portal-fill-material",
                "portal-prevent-nether",
                "portal-block-deepdark-nether",
                "portal-platform-radius",
                "permission-admin",
                "permission-enter",
                "permission-web"
        )) {
            zyroxApi.settings().unregister(MODULE_ID, settingId);
        }
        zyroxApi.settings().unregisterCategory(MODULE_ID, "world");
        zyroxApi.settings().unregisterCategory(MODULE_ID, "portal");
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
                value -> saveConfigValue(configPath, typedValue(type, value))
        ));
    }

    private void registerBooleanSetting(String id, String label, String description, String categoryId, String configPath, int order) {
        zyroxApi.settings().register(MODULE_ID, new ZyroxSetting(
                id,
                label,
                description,
                categoryId,
                ZyroxSettingType.BOOLEAN,
                false,
                adminPermission(),
                order,
                false,
                () -> getConfig().getBoolean(configPath, true),
                value -> saveConfigValue(configPath, Boolean.parseBoolean(value))
        ));
    }

    private ZyroxSettingResult saveConfigValue(String configPath, Object value) {
        getConfig().set(configPath, value);
        saveConfig();
        reloadConfig();
        return ZyroxSettingResult.success("Setting saved.");
    }

    private Object typedValue(ZyroxSettingType type, String value) {
        if (type == ZyroxSettingType.INTEGER) {
            return Long.parseLong(value);
        }
        return value;
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private void ensurePortalResource() {
        File file = new File(getDataFolder(), "portals.yml");
        if (!file.exists()) {
            saveResource("portals.yml", false);
        }
    }
}
