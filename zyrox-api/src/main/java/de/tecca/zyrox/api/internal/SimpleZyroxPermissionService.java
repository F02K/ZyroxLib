package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.permission.ZyroxPermissionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

public final class SimpleZyroxPermissionService implements ZyroxPermissionService {

    private final JavaPlugin plugin;
    private final Object luckPerms;

    public SimpleZyroxPermissionService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPerms = loadLuckPerms();
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        Objects.requireNonNull(player, "player");
        if (isBlankPermission(permission)) {
            return true;
        }

        Boolean luckPermsResult = checkLuckPerms(player.getUniqueId(), permission);
        if (luckPermsResult != null) {
            return luckPermsResult;
        }

        return player.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(UUID uniqueId, String permission) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        if (isBlankPermission(permission)) {
            return true;
        }

        Boolean luckPermsResult = checkLuckPerms(uniqueId, permission);
        if (luckPermsResult != null) {
            return luckPermsResult;
        }

        Player player = Bukkit.getPlayer(uniqueId);
        return player != null && player.hasPermission(permission);
    }

    private Object loadLuckPerms() {
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(luckPermsClass);
            if (provider == null) {
                plugin.getLogger().info("LuckPerms was not detected. Falling back to Bukkit permissions.");
                return null;
            }

            plugin.getLogger().info("LuckPerms detected. Using LuckPerms permission data where available.");
            return provider.getProvider();
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().info("LuckPerms API is not present. Falling back to Bukkit permissions.");
            return null;
        }
    }

    private Boolean checkLuckPerms(UUID uniqueId, String permission) {
        if (luckPerms == null) {
            return null;
        }

        try {
            Method getUserManager = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManager.invoke(luckPerms);
            Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, uniqueId);
            if (user == null) {
                return null;
            }

            Method getCachedData = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedData.invoke(user);
            Method getPermissionData = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionData.invoke(cachedData);
            Method checkPermission = permissionData.getClass().getMethod("checkPermission", String.class);
            Object tristate = checkPermission.invoke(permissionData, permission);
            Method asBoolean = tristate.getClass().getMethod("asBoolean");
            return (Boolean) asBoolean.invoke(tristate);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("LuckPerms permission lookup failed, using Bukkit fallback: " + exception.getMessage());
            return null;
        }
    }

    private static boolean isBlankPermission(String permission) {
        return permission == null || permission.isBlank();
    }
}
