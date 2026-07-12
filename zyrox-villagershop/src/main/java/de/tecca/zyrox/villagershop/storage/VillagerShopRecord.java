package de.tecca.zyrox.villagershop.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

public record VillagerShopRecord(
        long id,
        UUID entityUuid,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String displayName,
        String displayText,
        String profession,
        List<VillagerTradeRecord> trades,
        String createdAt,
        String updatedAt
) {

    public Location location() {
        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }

    public List<VillagerTradeRecord> trades() {
        return List.copyOf(trades);
    }

    public VillagerTradeRecord tradeAt(int index) {
        if (trades.isEmpty()) {
            throw new IllegalStateException("Villager shop has no trades.");
        }
        return trades.get(Math.max(0, Math.min(index, trades.size() - 1)));
    }
}
