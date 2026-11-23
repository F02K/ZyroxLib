package de.tecca.zyrox.chunkloader.service;

import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;

public record ChunkloaderActionResult(boolean success, String message, ChunkloaderRecord loader) {

    public static ChunkloaderActionResult success(String message, ChunkloaderRecord loader) {
        return new ChunkloaderActionResult(true, message, loader);
    }

    public static ChunkloaderActionResult failure(String message, ChunkloaderRecord loader) {
        return new ChunkloaderActionResult(false, message, loader);
    }
}
