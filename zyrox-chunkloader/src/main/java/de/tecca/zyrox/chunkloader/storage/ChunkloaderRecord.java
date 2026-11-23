package de.tecca.zyrox.chunkloader.storage;

import java.util.UUID;

public record ChunkloaderRecord(
        long id,
        String worldName,
        int x,
        int y,
        int z,
        int chunkX,
        int chunkZ,
        UUID ownerUuid,
        String ownerName,
        int priority,
        boolean enabled,
        long reserveBlocks,
        String paidUntil,
        String createdAt,
        String updatedAt
) {

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public LocationKey locationKey() {
        return new LocationKey(worldName, x, y, z);
    }
}
