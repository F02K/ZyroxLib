package de.tecca.zyrox.dc.minecraft;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public final class MinecraftAccountService {

    private final JavaPlugin plugin;

    public MinecraftAccountService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    public ResolvedMinecraftAccount resolve(String username) {
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.isEmpty()) {
            throw new IllegalArgumentException("Minecraft username cannot be blank.");
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(cleanUsername);
        UUID uniqueId = offlinePlayer.getUniqueId();
        String knownName = offlinePlayer.getName();
        return new ResolvedMinecraftAccount(knownName == null ? cleanUsername : knownName, uniqueId);
    }

    @SuppressWarnings("deprecation")
    public void setWhitelisted(String username, boolean whitelisted) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        offlinePlayer.setWhitelisted(whitelisted);
    }

    public boolean isWhitelisted(LinkedMinecraftIdentity identity) {
        Optional<OfflinePlayer> player = findOfflinePlayer(identity);
        return player.map(OfflinePlayer::isWhitelisted).orElse(false);
    }

    public boolean isBanned(LinkedMinecraftIdentity identity) {
        Optional<OfflinePlayer> player = findOfflinePlayer(identity);
        return player.map(OfflinePlayer::isBanned).orElse(false);
    }

    public Optional<MinecraftBan> findBan(LinkedMinecraftIdentity identity) {
        Optional<OfflinePlayer> player = findOfflinePlayer(identity);
        if (player.isEmpty()) {
            return Optional.empty();
        }

        Optional<? extends BanEntry<?>> banEntry = findBanEntry(player.get());
        if (banEntry.isPresent()) {
            BanEntry<?> entry = banEntry.get();
            return Optional.of(new MinecraftBan(entry.getReason(), entry.getSource()));
        }

        if (player.get().isBanned()) {
            return Optional.of(new MinecraftBan("", ""));
        }
        return Optional.empty();
    }

    public boolean isZyroxDcPropagatedBan(LinkedMinecraftIdentity identity, String expectedReason, String expectedSource) {
        return findBan(identity)
                .map(ban -> ban.matches(expectedReason, expectedSource))
                .orElse(false);
    }

    public void ban(LinkedMinecraftIdentity identity, String reason, String source) {
        Optional<OfflinePlayer> player = findOfflinePlayer(identity);
        player.ifPresent(offlinePlayer -> offlinePlayer.ban(reason, (Date) null, source));
    }

    public boolean pardonIfZyroxDcPropagatedBan(LinkedMinecraftIdentity identity, String expectedReason, String expectedSource) {
        Optional<OfflinePlayer> player = findOfflinePlayer(identity);
        if (player.isEmpty()) {
            return false;
        }

        Optional<? extends BanEntry<?>> banEntry = findBanEntry(player.get());
        if (banEntry.isEmpty() || !new MinecraftBan(banEntry.get().getReason(), banEntry.get().getSource()).matches(expectedReason, expectedSource)) {
            return false;
        }

        pardon(player.get());
        return true;
    }

    public Optional<OfflinePlayer> findOfflinePlayer(LinkedMinecraftIdentity identity) {
        if (identity.minecraftUuid() != null && !identity.minecraftUuid().isBlank()) {
            try {
                return Optional.of(Bukkit.getOfflinePlayer(UUID.fromString(identity.minecraftUuid())));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid stored Minecraft UUID: " + identity.minecraftUuid());
            }
        }

        if (identity.minecraftUsername() == null || identity.minecraftUsername().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Bukkit.getOfflinePlayer(identity.minecraftUsername()));
    }

    @SuppressWarnings("deprecation")
    private Optional<? extends BanEntry<?>> findBanEntry(OfflinePlayer player) {
        ProfileBanList profileBanList = Bukkit.getBanList(BanList.Type.PROFILE);
        BanEntry<?> profileEntry = profileBanList.getBanEntry(player.getPlayerProfile());
        if (profileEntry != null) {
            return Optional.of(profileEntry);
        }

        String playerName = player.getName();
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }

        BanList<?> nameBanList = Bukkit.getBanList(BanList.Type.NAME);
        BanEntry<?> nameEntry = nameBanList.getBanEntry(playerName);
        return Optional.ofNullable(nameEntry);
    }

    @SuppressWarnings("deprecation")
    private void pardon(OfflinePlayer player) {
        ProfileBanList profileBanList = Bukkit.getBanList(BanList.Type.PROFILE);
        profileBanList.pardon(player.getPlayerProfile());

        String playerName = player.getName();
        if (playerName != null && !playerName.isBlank()) {
            Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
        }
    }

    public record MinecraftBan(String reason, String source) {

        public boolean matches(String expectedReason, String expectedSource) {
            return normalize(reason).equals(normalize(expectedReason))
                    && normalize(source).equals(normalize(expectedSource));
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record ResolvedMinecraftAccount(String username, UUID uniqueId) {
    }

    public record LinkedMinecraftIdentity(String minecraftUsername, String minecraftUuid) {
    }
}
