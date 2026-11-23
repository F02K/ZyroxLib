package de.tecca.zyrox.dc.service;

import de.tecca.zyrox.dc.discord.DiscordBotService;
import de.tecca.zyrox.dc.minecraft.MinecraftAccountService;
import de.tecca.zyrox.dc.storage.DiscordBan;
import de.tecca.zyrox.dc.storage.LinkedAccount;
import de.tecca.zyrox.dc.storage.VerificationEventType;
import de.tecca.zyrox.dc.storage.ZyroxDcStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class VerificationService {

    private static final String GROUP_BAN_REASON = "Linked Minecraft account is banned.";
    private static final String GROUP_BAN_SOURCE = "ZyroxDC";

    private final JavaPlugin plugin;
    private final ZyroxDcStorage storage;
    private final MinecraftAccountService minecraftAccounts;
    private DiscordBotService discordBot;

    public VerificationService(JavaPlugin plugin, ZyroxDcStorage storage, MinecraftAccountService minecraftAccounts) {
        this.plugin = plugin;
        this.storage = storage;
        this.minecraftAccounts = minecraftAccounts;
    }

    public void setDiscordBot(DiscordBotService discordBot) {
        this.discordBot = discordBot;
    }

    public void verifyFromDiscord(String discordId, String discordName, String minecraftUsername, Consumer<String> callback) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                link(discordId, discordName, minecraftUsername, VerificationEventType.VERIFY_SUBMITTED);
                callback.accept("Your Discord account is now linked to Minecraft account `" + minecraftUsername.trim() + "`.");
            } catch (Exception exception) {
                storage.recordEvent(VerificationEventType.ERROR, discordId, minecraftUsername, exception.getMessage());
                callback.accept("Verification failed: " + exception.getMessage());
            }
        });
    }

    public void link(String discordId, String discordName, String minecraftUsername, VerificationEventType eventType) throws SQLException {
        Objects.requireNonNull(discordId, "discordId");
        Objects.requireNonNull(discordName, "discordName");

        MinecraftAccountService.ResolvedMinecraftAccount account = minecraftAccounts.resolve(minecraftUsername);
        UUID minecraftUuid = account.uniqueId();
        storage.upsertLink(discordId, discordName, account.username(), minecraftUuid == null ? "" : minecraftUuid.toString());

        if (plugin.getConfig().getBoolean("behavior.auto-whitelist", true)) {
            minecraftAccounts.setWhitelisted(account.username(), true);
            storage.recordEvent(VerificationEventType.WHITELISTED, discordId, account.username(), "Auto-whitelisted after linking.");
        }

        if (discordBot != null && plugin.getConfig().getBoolean("behavior.assign-verified-role", true)) {
            discordBot.syncVerifiedRole(discordId, plugin.getConfig().getString("discord.verified-role-id", ""));
            storage.recordEvent(VerificationEventType.ROLE_SYNCED, discordId, account.username(), "Verified role synced.");
        }

        if (plugin.getConfig().getBoolean("ban-sync.enabled", true) && storage.findDiscordBan(discordId).isPresent()) {
            unwhitelistAllLinkedDiscordAccounts(discordId);
        }

        storage.recordEvent(eventType, discordId, account.username(), "Linked Discord account to Minecraft account.");
    }

    public boolean unlink(String discordId) throws SQLException {
        boolean deleted = storage.deleteLink(discordId);
        storage.recordEvent(VerificationEventType.ADMIN_UNLINKED, discordId, null, deleted ? "Unlinked all accounts for Discord user." : "No link existed.");
        return deleted;
    }

    public void setWhitelisted(String minecraftUsername, boolean whitelisted, String actor) {
        minecraftAccounts.setWhitelisted(minecraftUsername, whitelisted);
        storage.recordEvent(
                whitelisted ? VerificationEventType.WHITELISTED : VerificationEventType.UNWHITELISTED,
                actor,
                minecraftUsername,
                whitelisted ? "Whitelisted by admin action." : "Removed from whitelist by admin action."
        );
    }

    public void syncDiscordBan(String discordId, String reason) {
        if (!plugin.getConfig().getBoolean("ban-sync.enabled", true)) {
            return;
        }

        try {
            storage.upsertDiscordBan(discordId, reason);
            storage.recordEvent(VerificationEventType.DISCORD_BANNED, discordId, null, safeReason(reason));
            unwhitelistAllLinkedDiscordAccounts(discordId);
        } catch (SQLException exception) {
            storage.recordEvent(VerificationEventType.ERROR, discordId, null, "Could not sync Discord ban: " + exception.getMessage());
            plugin.getLogger().warning("Could not sync Discord ban for " + discordId + ": " + exception.getMessage());
        }
    }

    public void syncDiscordUnban(String discordId) {
        if (!plugin.getConfig().getBoolean("ban-sync.enabled", true)) {
            return;
        }

        try {
            boolean removed = storage.removeDiscordBan(discordId);
            storage.recordEvent(
                    VerificationEventType.DISCORD_UNBANNED,
                    discordId,
                    null,
                    removed ? "Discord ban removed from local cache." : "Discord unban received without a local cache entry."
            );
            restoreLinkedMinecraftAccess(discordId, storage.findAllByDiscordId(discordId));
        } catch (SQLException exception) {
            storage.recordEvent(VerificationEventType.ERROR, discordId, null, "Could not sync Discord unban: " + exception.getMessage());
            plugin.getLogger().warning("Could not sync Discord unban for " + discordId + ": " + exception.getMessage());
        }
    }

    public void replaceDiscordBans(List<DiscordBan> bans) {
        if (!plugin.getConfig().getBoolean("ban-sync.enabled", true)) {
            return;
        }

        try {
            List<DiscordBan> currentBans = bans == null ? List.of() : List.copyOf(bans);
            List<DiscordBan> previousBans = storage.findAllDiscordBans();
            storage.replaceDiscordBans(currentBans);
            storage.recordEvent(VerificationEventType.BAN_SYNCED, null, null, "Synced " + currentBans.size() + " Discord bans.");
            for (DiscordBan ban : currentBans) {
                unwhitelistAllLinkedDiscordAccounts(ban.discordId());
            }
            for (String discordId : removedDiscordBanIds(previousBans, currentBans)) {
                storage.recordEvent(VerificationEventType.DISCORD_UNBANNED, discordId, null, "Discord ban missing from fresh Discord ban sync.");
                restoreLinkedMinecraftAccess(discordId, storage.findAllByDiscordId(discordId));
            }
        } catch (SQLException exception) {
            storage.recordEvent(VerificationEventType.ERROR, null, null, "Could not replace Discord ban cache: " + exception.getMessage());
            plugin.getLogger().warning("Could not replace Discord ban cache: " + exception.getMessage());
        }
    }

    public void reconcileNativeMinecraftBans() {
        if (!plugin.getConfig().getBoolean("ban-sync.enabled", true)) {
            return;
        }

        try {
            for (Map.Entry<String, List<LinkedAccount>> entry : linkedAccountsByDiscordId().entrySet()) {
                reconcileNativeMinecraftBanGroup(entry.getKey(), entry.getValue());
            }
        } catch (SQLException exception) {
            storage.recordEvent(VerificationEventType.ERROR, null, null, "Could not reconcile native Minecraft bans: " + exception.getMessage());
            plugin.getLogger().warning("Could not reconcile native Minecraft bans: " + exception.getMessage());
        }
    }

    public boolean propagateNativeMinecraftBanIfNeeded(LinkedAccount linkedAccount) {
        if (!plugin.getConfig().getBoolean("ban-sync.enabled", true)) {
            return false;
        }

        try {
            return reconcileNativeMinecraftBanGroup(linkedAccount.discordId(), storage.findAllByDiscordId(linkedAccount.discordId()));
        } catch (SQLException exception) {
            storage.recordEvent(VerificationEventType.ERROR, linkedAccount.discordId(), linkedAccount.minecraftUsername(), "Could not propagate native Minecraft ban: " + exception.getMessage());
            plugin.getLogger().warning("Could not propagate native Minecraft ban for " + linkedAccount.minecraftUsername() + ": " + exception.getMessage());
            return true;
        }
    }

    private boolean reconcileNativeMinecraftBanGroup(String discordId, List<LinkedAccount> linkedAccounts) throws SQLException {
        if (linkedAccounts.isEmpty()) {
            return false;
        }

        LinkedAccount externalBanSource = null;
        boolean hasZyroxPropagatedBan = false;
        for (LinkedAccount linkedAccount : linkedAccounts) {
            MinecraftAccountService.LinkedMinecraftIdentity identity = identity(linkedAccount);
            if (!minecraftAccounts.isBanned(identity)) {
                continue;
            }
            if (minecraftAccounts.isZyroxDcPropagatedBan(identity, GROUP_BAN_REASON, GROUP_BAN_SOURCE)) {
                hasZyroxPropagatedBan = true;
                continue;
            }
            externalBanSource = linkedAccount;
            break;
        }

        if (externalBanSource != null) {
            propagateNativeMinecraftBan(externalBanSource);
            return true;
        }

        if (hasZyroxPropagatedBan) {
            clearPropagatedMinecraftBansAndRestoreAccess(discordId, linkedAccounts);
        }
        return false;
    }

    private Map<String, List<LinkedAccount>> linkedAccountsByDiscordId() throws SQLException {
        Map<String, List<LinkedAccount>> groups = new HashMap<>();
        for (LinkedAccount linkedAccount : storage.findAllLinks()) {
            groups.computeIfAbsent(linkedAccount.discordId(), ignored -> new ArrayList<>()).add(linkedAccount);
        }
        return groups;
    }

    private void unwhitelistAllLinkedDiscordAccounts(String discordId) throws SQLException {
        if (!plugin.getConfig().getBoolean("ban-sync.unwhitelist-on-discord-ban", true)) {
            return;
        }

        List<LinkedAccount> linkedAccounts = storage.findAllByDiscordId(discordId);
        if (linkedAccounts.isEmpty()) {
            return;
        }

        Runnable task = () -> {
            for (LinkedAccount linkedAccount : linkedAccounts) {
                if (!minecraftAccounts.isWhitelisted(identity(linkedAccount))) {
                    continue;
                }
                minecraftAccounts.setWhitelisted(linkedAccount.minecraftUsername(), false);
                storage.recordEvent(
                        VerificationEventType.BAN_SYNCED,
                        discordId,
                        linkedAccount.minecraftUsername(),
                        "Removed native whitelist because Discord account is banned."
                );
            }
        };

        runMinecraftTask(task);
    }

    private void clearPropagatedMinecraftBansAndRestoreAccess(String discordId, List<LinkedAccount> linkedAccounts) throws SQLException {
        boolean shouldRestoreWhitelist = shouldRestoreWhitelist(discordId);
        runMinecraftTask(() -> {
            for (LinkedAccount linkedAccount : linkedAccounts) {
                MinecraftAccountService.LinkedMinecraftIdentity identity = identity(linkedAccount);
                if (minecraftAccounts.pardonIfZyroxDcPropagatedBan(identity, GROUP_BAN_REASON, GROUP_BAN_SOURCE)) {
                    storage.recordEvent(
                            VerificationEventType.BAN_SYNCED,
                            discordId,
                            linkedAccount.minecraftUsername(),
                            "Cleared propagated Minecraft ban because no linked source ban remains."
                    );
                }
            }
            if (shouldRestoreWhitelist) {
                restoreLinkedMinecraftAccessOnMain(discordId, linkedAccounts);
            }
        });
    }

    private void restoreLinkedMinecraftAccess(String discordId, List<LinkedAccount> linkedAccounts) throws SQLException {
        if (!shouldRestoreWhitelist(discordId)) {
            return;
        }
        runMinecraftTask(() -> restoreLinkedMinecraftAccessOnMain(discordId, linkedAccounts));
    }

    private boolean shouldRestoreWhitelist(String discordId) throws SQLException {
        return plugin.getConfig().getBoolean("behavior.auto-whitelist", true)
                && storage.findDiscordBan(discordId).isEmpty();
    }

    private void restoreLinkedMinecraftAccessOnMain(String discordId, List<LinkedAccount> linkedAccounts) {
        for (LinkedAccount linkedAccount : linkedAccounts) {
            MinecraftAccountService.LinkedMinecraftIdentity identity = identity(linkedAccount);
            if (minecraftAccounts.isBanned(identity) || minecraftAccounts.isWhitelisted(identity)) {
                continue;
            }
            minecraftAccounts.setWhitelisted(linkedAccount.minecraftUsername(), true);
            storage.recordEvent(
                    VerificationEventType.WHITELISTED,
                    discordId,
                    linkedAccount.minecraftUsername(),
                    "Restored whitelist after ban sync cleared."
            );
        }
    }

    private void propagateNativeMinecraftBan(LinkedAccount bannedAccount) throws SQLException {
        List<LinkedAccount> linkedAccounts = storage.findAllByDiscordId(bannedAccount.discordId());
        if (linkedAccounts.isEmpty()) {
            return;
        }

        Runnable task = () -> {
            for (LinkedAccount linkedAccount : linkedAccounts) {
                MinecraftAccountService.LinkedMinecraftIdentity identity = identity(linkedAccount);
                boolean changed = false;
                if (!minecraftAccounts.isBanned(identity)) {
                    minecraftAccounts.ban(identity, GROUP_BAN_REASON, GROUP_BAN_SOURCE);
                    changed = true;
                }
                if (plugin.getConfig().getBoolean("ban-sync.unwhitelist-on-discord-ban", true)
                        && minecraftAccounts.isWhitelisted(identity)) {
                    minecraftAccounts.setWhitelisted(linkedAccount.minecraftUsername(), false);
                    changed = true;
                }
                if (changed) {
                    storage.recordEvent(
                            VerificationEventType.BAN_SYNCED,
                            linkedAccount.discordId(),
                            linkedAccount.minecraftUsername(),
                            "Propagated native Minecraft ban from " + bannedAccount.minecraftUsername() + "."
                    );
                }
            }
        };

        runMinecraftTask(task);
    }

    private List<String> removedDiscordBanIds(List<DiscordBan> previousBans, List<DiscordBan> currentBans) {
        List<String> currentIds = currentBans.stream()
                .map(DiscordBan::discordId)
                .toList();
        return previousBans.stream()
                .map(DiscordBan::discordId)
                .filter(discordId -> !currentIds.contains(discordId))
                .toList();
    }

    private void runMinecraftTask(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private MinecraftAccountService.LinkedMinecraftIdentity identity(LinkedAccount linkedAccount) {
        return new MinecraftAccountService.LinkedMinecraftIdentity(linkedAccount.minecraftUsername(), linkedAccount.minecraftUuid());
    }

    private String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "Discord ban synced." : reason;
    }
}
