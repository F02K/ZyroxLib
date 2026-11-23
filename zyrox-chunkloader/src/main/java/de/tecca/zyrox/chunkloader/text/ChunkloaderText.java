package de.tecca.zyrox.chunkloader.text;

import de.tecca.zyrox.chunkloader.service.ChunkloaderStatus;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class ChunkloaderText {

    private final JavaPlugin plugin;

    public ChunkloaderText(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String message(String path, Map<String, String> placeholders) {
        return format(plugin.getConfig().getString("messages." + path, path), placeholders);
    }

    public Map<String, String> placeholders(ChunkloaderRecord loader, ChunkloaderStatus status) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", Long.toString(loader.id()));
        placeholders.put("world", loader.worldName());
        placeholders.put("x", Integer.toString(loader.x()));
        placeholders.put("y", Integer.toString(loader.y()));
        placeholders.put("z", Integer.toString(loader.z()));
        placeholders.put("chunk_x", Integer.toString(loader.chunkX()));
        placeholders.put("chunk_z", Integer.toString(loader.chunkZ()));
        placeholders.put("owner", loader.ownerName());
        placeholders.put("priority", Integer.toString(loader.priority()));
        placeholders.put("reserve", Long.toString(status == null ? loader.reserveBlocks() : status.reserveBlocks()));
        placeholders.put("rank", status == null || status.rank() <= 0 ? "-" : Integer.toString(status.rank()));
        placeholders.put("next_cost", status == null || status.nextCost() <= 0 ? "-" : Long.toString(status.nextCost()));
        placeholders.put("status", status != null && status.active() ? "aktiv" : "inaktiv");
        placeholders.put("paid_time_left", timeLeft(status == null ? null : status.paidUntil()));
        return placeholders;
    }

    public String format(String value, Map<String, String> placeholders) {
        String formatted = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return formatted;
    }

    public String timeLeft(Instant instant) {
        if (instant == null) {
            return "-";
        }
        Duration duration = Duration.between(Instant.now(), instant);
        if (duration.isNegative() || duration.isZero()) {
            return "faellig";
        }
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }
}
