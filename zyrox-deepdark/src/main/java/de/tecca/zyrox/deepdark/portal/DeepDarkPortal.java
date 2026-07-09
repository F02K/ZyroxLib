package de.tecca.zyrox.deepdark.portal;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.Optional;

public record DeepDarkPortal(
        String id,
        String worldName,
        int originX,
        int originY,
        int originZ,
        PortalAxis axis,
        String linkedPortalId,
        long lastUsedAt
) {

    public static final int FRAME_WIDTH = 22;
    public static final int FRAME_HEIGHT = 8;
    public static final int INTERIOR_MIN_WIDTH = 1;
    public static final int INTERIOR_MAX_WIDTH = FRAME_WIDTH - 2;
    public static final int INTERIOR_MIN_HEIGHT = 1;
    public static final int INTERIOR_MAX_HEIGHT = FRAME_HEIGHT - 2;

    public DeepDarkPortal {
        id = requireText(id, "id");
        worldName = requireText(worldName, "worldName");
        axis = Objects.requireNonNull(axis, "axis");
        linkedPortalId = linkedPortalId == null ? "" : linkedPortalId;
    }

    public Location centerLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        if (axis == PortalAxis.X) {
            return new Location(world, originX + (FRAME_WIDTH / 2.0), originY + INTERIOR_MIN_HEIGHT, originZ + 0.5);
        }
        return new Location(world, originX + 0.5, originY + INTERIOR_MIN_HEIGHT, originZ + (FRAME_WIDTH / 2.0));
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !worldName.equals(location.getWorld().getName())) {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y < originY + INTERIOR_MIN_HEIGHT || y > originY + INTERIOR_MAX_HEIGHT) {
            return false;
        }
        if (axis == PortalAxis.X) {
            return z == originZ && x >= originX + INTERIOR_MIN_WIDTH && x <= originX + INTERIOR_MAX_WIDTH;
        }
        return x == originX && z >= originZ + INTERIOR_MIN_WIDTH && z <= originZ + INTERIOR_MAX_WIDTH;
    }

    public DeepDarkPortal withLinkedPortalId(String linkedPortalId) {
        return new DeepDarkPortal(id, worldName, originX, originY, originZ, axis, linkedPortalId, lastUsedAt);
    }

    public DeepDarkPortal withLastUsedAt(long lastUsedAt) {
        return new DeepDarkPortal(id, worldName, originX, originY, originZ, axis, linkedPortalId, lastUsedAt);
    }

    public void write(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("origin.x", originX);
        section.set("origin.y", originY);
        section.set("origin.z", originZ);
        section.set("axis", axis.name());
        section.set("linked-portal-id", linkedPortalId);
        section.set("last-used-at", lastUsedAt);
    }

    public static Optional<DeepDarkPortal> read(String id, ConfigurationSection section) {
        if (section == null) {
            return Optional.empty();
        }

        String world = section.getString("world", "");
        String axisName = section.getString("axis", PortalAxis.X.name());
        try {
            return Optional.of(new DeepDarkPortal(
                    id,
                    world,
                    section.getInt("origin.x"),
                    section.getInt("origin.y"),
                    section.getInt("origin.z"),
                    PortalAxis.valueOf(axisName),
                    section.getString("linked-portal-id", ""),
                    section.getLong("last-used-at", 0L)
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return text;
    }
}
