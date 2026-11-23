package de.tecca.zyrox.chunkloader.service;

import java.time.Instant;

public record ChunkloaderStatus(
        boolean active,
        int rank,
        long nextCost,
        long reserveBlocks,
        Instant paidUntil
) {

    public static ChunkloaderStatus inactive(long reserveBlocks) {
        return new ChunkloaderStatus(false, 0, 0, reserveBlocks, null);
    }
}
