package de.tecca.zyrox.api.permission;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface ZyroxPermissionService {

    boolean hasPermission(Player player, String permission);

    boolean hasPermission(UUID uniqueId, String permission);
}

