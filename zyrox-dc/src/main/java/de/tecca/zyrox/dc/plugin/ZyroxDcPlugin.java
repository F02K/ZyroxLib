package de.tecca.zyrox.dc.plugin;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionParameter;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.api.web.ZyroxDashboardView;
import de.tecca.zyrox.dc.command.ZyroxDcCommand;
import de.tecca.zyrox.dc.discord.DiscordBotService;
import de.tecca.zyrox.dc.listener.ZyroxDcLoginListener;
import de.tecca.zyrox.dc.minecraft.MinecraftAccountService;
import de.tecca.zyrox.dc.service.VerificationService;
import de.tecca.zyrox.dc.storage.VerificationEventType;
import de.tecca.zyrox.dc.storage.ZyroxDcStorage;
import de.tecca.zyrox.dc.web.ZyroxDcDashboardRenderer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class ZyroxDcPlugin extends JavaPlugin {

    private ZyroxApi zyroxApi;
    private ZyroxDcStorage storage;
    private MinecraftAccountService minecraftAccounts;
    private VerificationService verificationService;
    private DiscordBotService discordBot;
    private ZyroxDcDashboardRenderer dashboardRenderer;

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
            storage = new ZyroxDcStorage(this, storageFile());
            storage.open();
        } catch (SQLException exception) {
            getLogger().severe("Could not open ZyroxDC storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        minecraftAccounts = new MinecraftAccountService(this);
        verificationService = new VerificationService(this, storage, minecraftAccounts);
        discordBot = new DiscordBotService(this, verificationService);
        verificationService.setDiscordBot(discordBot);
        dashboardRenderer = new ZyroxDcDashboardRenderer(storage, minecraftAccounts, discordBot);

        registerZyroxApiIntegrations();
        registerCommand();
        registerListeners();
        startDiscordBot();

        getLogger().info("ZyroxDC is enabled.");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.stop();
        }

        if (zyroxApi != null) {
            zyroxApi.modules().unregister("zyrox-dc");
            zyroxApi.dashboardViews().unregister("zyrox-dc-links");
            unregisterSettings();
        }

        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException exception) {
                getLogger().warning("Could not close ZyroxDC storage: " + exception.getMessage());
            }
        }

        getLogger().info("ZyroxDC is disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (discordBot != null) {
            startDiscordBot();
        }
    }

    public void sendVerificationMessage() {
        ensureDiscordRunning();
        discordBot.sendVerificationMessage(
                getConfig().getString("discord.verification-channel-id", ""),
                getConfig().getString("verification-message.title", "ZyroxMC Verification"),
                getConfig().getString("verification-message.description", "Click the button below and enter your Minecraft username."),
                getConfig().getString("verification-message.button-label", "Verify Minecraft Account")
        );
    }

    public void adminLink(String discordId, String minecraftUsername) throws SQLException {
        String discordName = discordBot != null && discordBot.isRunning() ? discordBot.displayName(discordId) : discordId;
        verificationService.link(discordId, discordName, minecraftUsername, VerificationEventType.ADMIN_LINKED);
    }

    public boolean adminUnlink(String discordId) throws SQLException {
        return verificationService.unlink(discordId);
    }

    public void adminSetWhitelisted(String minecraftUsername, boolean whitelisted, String actor) {
        verificationService.setWhitelisted(minecraftUsername, whitelisted, actor);
    }

    private void registerZyroxApiIntegrations() {
        String adminPermission = getConfig().getString("permissions.admin", "zyrox.dc.admin");
        String webPermission = getConfig().getString("permissions.web", "zyrox.dc.web");

        zyroxApi.modules().register(new ZyroxModule(
                "zyrox-dc",
                "ZyroxDC",
                getDescription().getVersion(),
                "Discord verification and Minecraft whitelist linking for ZyroxMC.",
                webPermission
        ));

        zyroxApi.dashboardViews().register(new ZyroxDashboardView(
                "zyrox-dc-links",
                "Discord Verification",
                webPermission,
                context -> dashboardRenderer.render()
        ));

        registerSettings(adminPermission);

        zyroxApi.actions().register("zyrox-dc", new ZyroxAction(
                "send-verify-message",
                "Send Verification Embed",
                "Sends the configured Discord verification embed.",
                adminPermission,
                List.of(),
                context -> {
                    sendVerificationMessage();
                    return ZyroxActionResult.success("Verification embed sent.");
                }
        ));

        zyroxApi.actions().register("zyrox-dc", new ZyroxAction(
                "link",
                "Link Account",
                "Links a Discord user id to a Minecraft username and whitelists the Minecraft account.",
                adminPermission,
                List.of(
                        ZyroxActionParameter.string("discord-id", "Discord User ID", true),
                        ZyroxActionParameter.string("minecraft-name", "Minecraft Username", true)
                ),
                context -> {
                    adminLink(requireParameter(context.parameters().get("discord-id"), "discord-id"),
                            requireParameter(context.parameters().get("minecraft-name"), "minecraft-name"));
                    return ZyroxActionResult.success("Account linked.");
                }
        ));

        zyroxApi.actions().register("zyrox-dc", new ZyroxAction(
                "unlink",
                "Unlink Account",
                "Removes a Discord to Minecraft link.",
                adminPermission,
                List.of(ZyroxActionParameter.string("discord-id", "Discord User ID", true)),
                context -> {
                    boolean removed = adminUnlink(requireParameter(context.parameters().get("discord-id"), "discord-id"));
                    return ZyroxActionResult.success(removed ? "Account unlinked." : "No link existed.");
                }
        ));

        zyroxApi.actions().register("zyrox-dc", new ZyroxAction(
                "whitelist",
                "Whitelist Minecraft User",
                "Adds a Minecraft username to the server whitelist.",
                adminPermission,
                List.of(ZyroxActionParameter.string("minecraft-name", "Minecraft Username", true)),
                context -> {
                    adminSetWhitelisted(requireParameter(context.parameters().get("minecraft-name"), "minecraft-name"), true, context.actorName());
                    return ZyroxActionResult.success("Minecraft account whitelisted.");
                }
        ));

        zyroxApi.actions().register("zyrox-dc", new ZyroxAction(
                "unwhitelist",
                "Unwhitelist Minecraft User",
                "Removes a Minecraft username from the server whitelist.",
                adminPermission,
                List.of(ZyroxActionParameter.string("minecraft-name", "Minecraft Username", true)),
                context -> {
                    adminSetWhitelisted(requireParameter(context.parameters().get("minecraft-name"), "minecraft-name"), false, context.actorName());
                    return ZyroxActionResult.success("Minecraft account removed from whitelist.");
                }
        ));
    }

    private void registerSettings(String adminPermission) {
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("discord", "Discord", 10));
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("verification-message", "Verification Message", 20));
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("behavior", "Behavior", 30));
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("join-gate", "Join Gate", 40));
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("ban-sync", "Ban Sync", 50));
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("storage", "Storage", 60));
        zyroxApi.settings().registerCategory("zyrox-dc", new ZyroxSettingCategory("permissions", "Permissions", 70));

        registerStringSetting("discord-token", "Discord Bot Token", "Bot token used to connect ZyroxDC to Discord.", "discord", ZyroxSettingType.SECRET, "discord.token", true, adminPermission, 10, false, true);
        registerStringSetting("discord-guild-id", "Guild ID", "Discord guild id where verification is managed.", "discord", ZyroxSettingType.STRING, "discord.guild-id", true, adminPermission, 20, false, true);
        registerStringSetting("discord-verification-channel-id", "Verification Channel ID", "Channel where the verification embed is sent.", "discord", ZyroxSettingType.STRING, "discord.verification-channel-id", true, adminPermission, 30, false, false);
        registerStringSetting("discord-verified-role-id", "Verified Role ID", "Role assigned after successful account verification.", "discord", ZyroxSettingType.STRING, "discord.verified-role-id", false, adminPermission, 40, false, false);

        registerStringSetting("verification-message-title", "Title", "Title of the Discord verification embed.", "verification-message", ZyroxSettingType.STRING, "verification-message.title", true, adminPermission, 10, false, false);
        registerStringSetting("verification-message-description", "Description", "Description of the Discord verification embed.", "verification-message", ZyroxSettingType.STRING, "verification-message.description", true, adminPermission, 20, false, false);
        registerStringSetting("verification-message-button-label", "Button Label", "Button label shown below the verification embed.", "verification-message", ZyroxSettingType.STRING, "verification-message.button-label", true, adminPermission, 30, false, false);

        registerBooleanSetting("behavior-auto-whitelist", "Auto Whitelist", "Automatically whitelist submitted Minecraft accounts.", "behavior", "behavior.auto-whitelist", adminPermission, 10);
        registerBooleanSetting("behavior-assign-verified-role", "Assign Verified Role", "Assign the configured Discord role after verification.", "behavior", "behavior.assign-verified-role", adminPermission, 20);

        registerBooleanSetting("join-gate-enabled", "Join Gate Enabled", "Blocks unverified players with a custom verification message.", "join-gate", "join-gate.enabled", adminPermission, 10);
        registerBooleanSetting("join-gate-require-without-whitelist", "Require Without Server Whitelist", "Requires Discord verification even when the Minecraft server whitelist is disabled.", "join-gate", "join-gate.require-verification-without-server-whitelist", adminPermission, 20);
        registerStringSetting("join-gate-kick-message", "Kick Message", "Message shown to unverified players when they try to join.", "join-gate", ZyroxSettingType.STRING, "join-gate.kick-message", true, adminPermission, 30, false, false);
        registerStringSetting("join-gate-error-message", "Error Message", "Message shown when the verification lookup fails.", "join-gate", ZyroxSettingType.STRING, "join-gate.error-message", true, adminPermission, 40, false, false);

        registerBooleanSetting("ban-sync-enabled", "Ban Sync Enabled", "Blocks verified Minecraft players when their linked Discord account is banned.", "ban-sync", "ban-sync.enabled", adminPermission, 10);
        registerBooleanSetting("ban-sync-existing-on-start", "Sync Existing Bans On Start", "Loads the current Discord ban list when the bot starts.", "ban-sync", "ban-sync.sync-existing-bans-on-start", adminPermission, 20);
        registerBooleanSetting("ban-sync-unwhitelist", "Unwhitelist On Discord Ban", "Removes linked Minecraft accounts from the native whitelist when Discord bans are synced.", "ban-sync", "ban-sync.unwhitelist-on-discord-ban", adminPermission, 30);
        registerStringSetting("ban-sync-kick-message", "Kick Message", "Message shown when a Discord-banned player tries to join.", "ban-sync", ZyroxSettingType.STRING, "ban-sync.kick-message", true, adminPermission, 40, false, false);

        registerStringSetting("storage-sqlite-file", "SQLite File", "SQLite database path used for account links.", "storage", ZyroxSettingType.STRING, "storage.sqlite-file", true, adminPermission, 10, true, false);

        registerStringSetting("permissions-admin", "Admin Permission", "Permission required to manage ZyroxDC.", "permissions", ZyroxSettingType.STRING, "permissions.admin", true, adminPermission, 10, true, false);
        registerStringSetting("permissions-web", "Web Permission", "Permission required to see ZyroxDC in the dashboard.", "permissions", ZyroxSettingType.STRING, "permissions.web", true, adminPermission, 20, true, false);
    }

    private void unregisterSettings() {
        zyroxApi.settings().unregister("zyrox-dc", "discord-token");
        zyroxApi.settings().unregister("zyrox-dc", "discord-guild-id");
        zyroxApi.settings().unregister("zyrox-dc", "discord-verification-channel-id");
        zyroxApi.settings().unregister("zyrox-dc", "discord-verified-role-id");
        zyroxApi.settings().unregister("zyrox-dc", "verification-message-title");
        zyroxApi.settings().unregister("zyrox-dc", "verification-message-description");
        zyroxApi.settings().unregister("zyrox-dc", "verification-message-button-label");
        zyroxApi.settings().unregister("zyrox-dc", "behavior-auto-whitelist");
        zyroxApi.settings().unregister("zyrox-dc", "behavior-assign-verified-role");
        zyroxApi.settings().unregister("zyrox-dc", "join-gate-enabled");
        zyroxApi.settings().unregister("zyrox-dc", "join-gate-require-without-whitelist");
        zyroxApi.settings().unregister("zyrox-dc", "join-gate-kick-message");
        zyroxApi.settings().unregister("zyrox-dc", "join-gate-error-message");
        zyroxApi.settings().unregister("zyrox-dc", "ban-sync-enabled");
        zyroxApi.settings().unregister("zyrox-dc", "ban-sync-existing-on-start");
        zyroxApi.settings().unregister("zyrox-dc", "ban-sync-unwhitelist");
        zyroxApi.settings().unregister("zyrox-dc", "ban-sync-kick-message");
        zyroxApi.settings().unregister("zyrox-dc", "storage-sqlite-file");
        zyroxApi.settings().unregister("zyrox-dc", "permissions-admin");
        zyroxApi.settings().unregister("zyrox-dc", "permissions-web");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "discord");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "verification-message");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "behavior");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "join-gate");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "ban-sync");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "storage");
        zyroxApi.settings().unregisterCategory("zyrox-dc", "permissions");
    }

    private void registerStringSetting(
            String id,
            String label,
            String description,
            String categoryId,
            ZyroxSettingType type,
            String configPath,
            boolean required,
            String permission,
            int order,
            boolean restartRequired,
            boolean restartDiscord
    ) {
        zyroxApi.settings().register("zyrox-dc", new ZyroxSetting(
                id,
                label,
                description,
                categoryId,
                type,
                required,
                permission,
                order,
                restartRequired,
                () -> getConfig().getString(configPath, ""),
                value -> saveConfigValue(configPath, value, restartDiscord)
        ));
    }

    private void registerBooleanSetting(
            String id,
            String label,
            String description,
            String categoryId,
            String configPath,
            String permission,
            int order
    ) {
        zyroxApi.settings().register("zyrox-dc", new ZyroxSetting(
                id,
                label,
                description,
                categoryId,
                ZyroxSettingType.BOOLEAN,
                false,
                permission,
                order,
                false,
                () -> getConfig().getBoolean(configPath, true),
                value -> saveConfigValue(configPath, Boolean.parseBoolean(value), false)
        ));
    }

    private ZyroxSettingResult saveConfigValue(String configPath, Object value, boolean restartDiscord) {
        getConfig().set(configPath, value);
        saveConfig();
        reloadConfig();
        if (restartDiscord) {
            startDiscordBot();
        }
        return ZyroxSettingResult.success("Setting saved.");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("zyroxdc");
        if (command == null) {
            getLogger().warning("Command /zyroxdc is missing from plugin.yml.");
            return;
        }

        ZyroxDcCommand executor = new ZyroxDcCommand(this, getConfig().getString("permissions.admin", "zyrox.dc.admin"));
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ZyroxDcLoginListener(this, storage, minecraftAccounts), this);
    }

    private void startDiscordBot() {
        String token = getConfig().getString("discord.token", "");
        String guildId = getConfig().getString("discord.guild-id", "");
        if (token == null || token.isBlank() || "CHANGE_ME".equals(token) || guildId == null || guildId.isBlank() || "0".equals(guildId)) {
            getLogger().warning("Discord bot token or guild id is not configured. ZyroxDC will run without Discord connection.");
            if (discordBot != null) {
                discordBot.stop();
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                discordBot.start(token, guildId);
            } catch (Exception exception) {
                getLogger().severe("Could not start Discord bot: " + exception.getMessage());
            }
        });
    }

    private ZyroxApi loadZyroxApi() {
        RegisteredServiceProvider<ZyroxApi> provider = getServer().getServicesManager().getRegistration(ZyroxApi.class);
        return provider == null ? null : provider.getProvider();
    }

    private File storageFile() {
        String configuredPath = getConfig().getString("storage.sqlite-file", "zyrox-dc.db");
        File file = new File(Objects.requireNonNull(configuredPath));
        return file.isAbsolute() ? file : new File(getDataFolder(), configuredPath);
    }

    private void ensureDiscordRunning() {
        if (discordBot == null || !discordBot.isRunning()) {
            throw new IllegalStateException("Discord bot is not connected.");
        }
    }

    private String requireParameter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        return value.trim();
    }
}
