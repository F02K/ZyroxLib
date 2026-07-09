package de.tecca.zyrox.end.service;

import java.util.Optional;
import java.util.UUID;

public record DragonEggTrackingState(
        DragonEggTrackingForm form,
        UUID worldUniqueId,
        String worldName,
        int blockX,
        int blockY,
        int blockZ,
        UUID entityUniqueId,
        long updatedAtEpochMillis
) {

    public Optional<UUID> worldId() {
        return Optional.ofNullable(worldUniqueId);
    }

    public Optional<String> world() {
        return Optional.ofNullable(worldName).filter(name -> !name.isBlank());
    }

    public Optional<UUID> entityId() {
        return Optional.ofNullable(entityUniqueId);
    }

    public String description() {
        return switch (form) {
            case PLACED -> world().orElse("?") + " " + blockX + "," + blockY + "," + blockZ;
            case FALLING -> world().orElse("?") + " entity=" + entityId().map(UUID::toString).orElse("?");
            case ITEM -> entityId().map(id -> "entity=" + id).orElse("inventory/container");
            case LOST -> "lost";
        };
    }
}
