package de.tecca.zyrox.end.service;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class DragonEggStateStorage {

    private final File file;
    private final Logger logger;

    public DragonEggStateStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public FirstDragonEggState load() {
        FirstDragonEggState state = new FirstDragonEggState();
        if (!file.exists()) {
            return state;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getBoolean("first-dragon.killed", false)) {
            state.markFirstDragonKilled(
                    yaml.getString("first-dragon.world", ""),
                    yaml.getLong("first-dragon.killed-at-epoch-ms", 0L)
            );
        }

        FirstEndRitualStatus ritualStatus = ritualStatus(yaml.getString("first-ritual.status"));
        if (ritualStatus == FirstEndRitualStatus.IN_PROGRESS) {
            state.markRitualInProgress(
                    yaml.getString("first-ritual.world", ""),
                    yaml.getLong("first-ritual.started-at-epoch-ms", 0L)
            );
        } else if (ritualStatus == FirstEndRitualStatus.COMPLETED || state.firstDragonKilled()) {
            state.markRitualCompleted(
                    yaml.getString("first-ritual.world", state.firstDragonWorldName().orElse("")),
                    yaml.getLong("first-ritual.completed-at-epoch-ms", state.firstDragonKilledAtEpochMillis())
            );
        }

        if (yaml.getBoolean("egg.claimed", false)) {
            UUID eggId = uuidOrNull(yaml.getString("egg.id"));
            UUID ownerUniqueId = uuidOrNull(yaml.getString("egg.owner-uuid"));
            String ownerName = yaml.getString("egg.owner-name", "");
            long claimedAtEpochMillis = yaml.getLong("egg.claimed-at-epoch-ms", 0L);

            if (eggId != null && ownerUniqueId != null) {
                state.markEggClaimed(new DragonEggClaim(eggId, ownerUniqueId, ownerName, claimedAtEpochMillis));
            } else {
                logger.warning("Ignoring incomplete ZyroxEnd egg claim data in " + file.getName() + ".");
            }
        }

        DragonEggClaim pendingClaim = claim(
                yaml.getString("egg.pending.id"),
                yaml.getString("egg.pending.owner-uuid"),
                yaml.getString("egg.pending.owner-name", ""),
                yaml.getLong("egg.pending.claimed-at-epoch-ms", 0L)
        );
        if (!state.eggClaimed() && pendingClaim != null) {
            state.reserveEggClaim(pendingClaim);
        }

        DragonEggTrackingState tracking = tracking(yaml);
        if (tracking != null && (state.eggClaimed() || state.pendingEggClaim().isPresent())) {
            state.trackEgg(tracking);
        } else if (state.eggClaimed()) {
            state.trackEgg(new DragonEggTrackingState(
                    DragonEggTrackingForm.ITEM,
                    null,
                    null,
                    0,
                    0,
                    0,
                    null,
                    0L
            ));
        }

        List<String> layoutPositions = yaml.getStringList("crystal-layout.positions");
        if (!layoutPositions.isEmpty()) {
            state.recordCrystalLayout(
                    yaml.getString("crystal-layout.world", ""),
                    yaml.getLong("crystal-layout.scanned-at-epoch-ms", 0L),
                    layoutPositions
            );
        }

        return state;
    }

    public void save(FirstDragonEggState state) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warning("Could not create ZyroxEnd data directory for " + file.getAbsolutePath() + ".");
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("first-dragon.killed", state.firstDragonKilled());
        yaml.set("first-dragon.world", state.firstDragonWorldName().orElse(""));
        yaml.set("first-dragon.killed-at-epoch-ms", state.firstDragonKilledAtEpochMillis());
        yaml.set("egg.claimed", state.eggClaimed());
        yaml.set("egg.id", state.eggId().map(UUID::toString).orElse(""));
        yaml.set("egg.owner-uuid", state.ownerUniqueId().map(UUID::toString).orElse(""));
        yaml.set("egg.owner-name", state.ownerName().orElse(""));
        yaml.set("egg.claimed-at-epoch-ms", state.claimedAtEpochMillis());
        DragonEggClaim pending = state.pendingEggClaim().orElse(null);
        yaml.set("egg.pending.id", pending == null ? "" : pending.eggId().toString());
        yaml.set("egg.pending.owner-uuid", pending == null ? "" : pending.ownerUniqueId().toString());
        yaml.set("egg.pending.owner-name", pending == null ? "" : pending.ownerName());
        yaml.set("egg.pending.claimed-at-epoch-ms", pending == null ? 0L : pending.claimedAtEpochMillis());
        DragonEggTrackingState tracking = state.eggTracking().orElse(null);
        yaml.set("egg.tracking.form", tracking == null ? "" : tracking.form().name());
        yaml.set("egg.tracking.world-uuid", tracking == null ? "" : tracking.worldId().map(UUID::toString).orElse(""));
        yaml.set("egg.tracking.world", tracking == null ? "" : tracking.world().orElse(""));
        yaml.set("egg.tracking.x", tracking == null ? 0 : tracking.blockX());
        yaml.set("egg.tracking.y", tracking == null ? 0 : tracking.blockY());
        yaml.set("egg.tracking.z", tracking == null ? 0 : tracking.blockZ());
        yaml.set("egg.tracking.entity-uuid", tracking == null ? "" : tracking.entityId().map(UUID::toString).orElse(""));
        yaml.set("egg.tracking.updated-at-epoch-ms", tracking == null ? 0L : tracking.updatedAtEpochMillis());
        yaml.set("crystal-layout.world", state.crystalLayoutWorldName().orElse(""));
        yaml.set("crystal-layout.scanned-at-epoch-ms", state.crystalLayoutScannedAtEpochMillis());
        yaml.set("crystal-layout.positions", state.crystalLayoutPositions());
        yaml.set("first-ritual.status", state.ritualStatus().name());
        yaml.set("first-ritual.world", state.ritualWorldName().orElse(""));
        yaml.set("first-ritual.started-at-epoch-ms", state.ritualStartedAtEpochMillis());
        yaml.set("first-ritual.completed-at-epoch-ms", state.ritualCompletedAtEpochMillis());

        try {
            yaml.save(file);
        } catch (IOException exception) {
            logger.warning("Could not save ZyroxEnd state: " + exception.getMessage());
        }
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private DragonEggClaim claim(String eggIdValue, String ownerIdValue, String ownerName, long claimedAt) {
        UUID eggId = uuidOrNull(eggIdValue);
        UUID ownerId = uuidOrNull(ownerIdValue);
        return eggId == null || ownerId == null ? null : new DragonEggClaim(eggId, ownerId, ownerName, claimedAt);
    }

    private DragonEggTrackingState tracking(YamlConfiguration yaml) {
        String formValue = yaml.getString("egg.tracking.form", "");
        if (formValue.isBlank()) {
            return null;
        }
        try {
            return new DragonEggTrackingState(
                    DragonEggTrackingForm.valueOf(formValue),
                    uuidOrNull(yaml.getString("egg.tracking.world-uuid")),
                    yaml.getString("egg.tracking.world", ""),
                    yaml.getInt("egg.tracking.x"),
                    yaml.getInt("egg.tracking.y"),
                    yaml.getInt("egg.tracking.z"),
                    uuidOrNull(yaml.getString("egg.tracking.entity-uuid")),
                    yaml.getLong("egg.tracking.updated-at-epoch-ms")
            );
        } catch (IllegalArgumentException exception) {
            logger.warning("Ignoring unknown Dragon Egg tracking form '" + formValue + "'.");
            return null;
        }
    }

    private FirstEndRitualStatus ritualStatus(String value) {
        if (value == null || value.isBlank()) {
            return FirstEndRitualStatus.NOT_STARTED;
        }
        try {
            return FirstEndRitualStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            logger.warning("Ignoring unknown first End ritual status '" + value + "'.");
            return FirstEndRitualStatus.NOT_STARTED;
        }
    }
}
