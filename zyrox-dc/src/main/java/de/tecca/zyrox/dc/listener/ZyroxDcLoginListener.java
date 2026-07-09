package de.tecca.zyrox.dc.listener;

import de.tecca.zyrox.dc.minecraft.MinecraftAccountService;
import de.tecca.zyrox.dc.service.VerificationService;
import de.tecca.zyrox.dc.storage.DiscordBan;
import de.tecca.zyrox.dc.storage.LinkedAccount;
import de.tecca.zyrox.dc.storage.ZyroxDcStorage;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Optional;

public final class ZyroxDcLoginListener implements Listener {

    private final JavaPlugin plugin;
    private final ZyroxDcStorage storage;
    private final MinecraftAccountService minecraftAccounts;
    private final VerificationService verificationService;

    public ZyroxDcLoginListener(JavaPlugin plugin, ZyroxDcStorage storage, MinecraftAccountService minecraftAccounts, VerificationService verificationService) {
        this.plugin = plugin;
        this.storage = storage;
        this.minecraftAccounts = minecraftAccounts;
        this.verificationService = verificationService;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        boolean joinGateEnabled = plugin.getConfig().getBoolean("join-gate.enabled", true);
        boolean banSyncEnabled = plugin.getConfig().getBoolean("ban-sync.enabled", true);
        if (!joinGateEnabled && !banSyncEnabled) {
            return;
        }

        PlayerLoginEvent.Result result = event.getResult();
        boolean whitelistRejected = result == PlayerLoginEvent.Result.KICK_WHITELIST;
        boolean allowedLogin = result == PlayerLoginEvent.Result.ALLOWED;
        boolean bannedLogin = result == PlayerLoginEvent.Result.KICK_BANNED;
        boolean shouldCheckVerification = joinGateEnabled && (whitelistRejected || (allowedLogin
                && plugin.getConfig().getBoolean("join-gate.require-verification-without-server-whitelist", true)));
        boolean shouldCheckBan = banSyncEnabled && (whitelistRejected || allowedLogin || bannedLogin);

        if (!shouldCheckVerification && !shouldCheckBan) {
            return;
        }

        Optional<LinkedAccount> linkedAccount;
        try {
            linkedAccount = storage.findByMinecraftIdentity(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not check verification status for " + event.getPlayer().getName() + ": " + exception.getMessage());
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, formatMessage(plugin.getConfig().getString(
                    "join-gate.error-message",
                    "&cYour verification status could not be checked. Please try again in a moment."
            )));
            return;
        }

        if (linkedAccount.isEmpty()) {
            if (shouldCheckVerification) {
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, formatMessage(plugin.getConfig().getString(
                        "join-gate.kick-message",
                        "&cYou are not verified yet.\n&7Please verify your Minecraft account in our Discord."
                )));
            }
            return;
        }

        if (shouldCheckBan && verificationService.propagateNativeMinecraftBanIfNeeded(linkedAccount.get())) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, formatMessage(plugin.getConfig().getString(
                    "ban-sync.minecraft-ban-kick-message",
                    "&cA linked Minecraft account is banned.\n&7Please contact support if you think this is a mistake."
            )));
            return;
        }

        if (shouldCheckBan && isDiscordBanned(linkedAccount.get())) {
            if (plugin.getConfig().getBoolean("ban-sync.unwhitelist-on-discord-ban", true)) {
                minecraftAccounts.setWhitelisted(linkedAccount.get().minecraftUsername(), false);
            }
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, formatMessage(plugin.getConfig().getString(
                    "ban-sync.kick-message",
                    "&cYour Discord account is banned.\n&7Please contact support in Discord if you think this is a mistake."
            )));
            return;
        }

        if (plugin.getConfig().getBoolean("behavior.auto-whitelist", true)) {
            minecraftAccounts.setWhitelisted(linkedAccount.get().minecraftUsername(), true);
        }

        if (whitelistRejected) {
            event.allow();
        }
    }

    @SuppressWarnings("deprecation")
    private String formatMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private boolean isDiscordBanned(LinkedAccount linkedAccount) {
        try {
            Optional<DiscordBan> ban = storage.findDiscordBan(linkedAccount.discordId());
            return ban.isPresent();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not check Discord ban status for " + linkedAccount.discordId() + ": " + exception.getMessage());
            return false;
        }
    }
}
