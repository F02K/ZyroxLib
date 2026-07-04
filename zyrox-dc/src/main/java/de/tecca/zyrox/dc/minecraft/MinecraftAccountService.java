package de.tecca.zyrox.dc.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

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

    public record ResolvedMinecraftAccount(String username, UUID uniqueId) {
    }

    public record LinkedMinecraftIdentity(String minecraftUsername, String minecraftUuid) {
    }
}

