package de.tecca.zyrox.chunkloader.storage;

import org.bukkit.Location;

public record LocationKey(String worldName, int x, int y, int z) {

    public static LocationKey from(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location must have a world.");
        }
        return new LocationKey(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
