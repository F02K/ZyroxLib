package de.tecca.zyrox.api.config;

import java.nio.file.Path;
import java.util.Optional;

public record ConfigUpdateResult(
        ConfigUpdateStatus status,
        Path configPath,
        Optional<Path> backupPath,
        Optional<String> errorMessage
) {

    public static ConfigUpdateResult created(Path configPath) {
        return new ConfigUpdateResult(ConfigUpdateStatus.CREATED, configPath, Optional.empty(), Optional.empty());
    }

    public static ConfigUpdateResult unchanged(Path configPath) {
        return new ConfigUpdateResult(ConfigUpdateStatus.UNCHANGED, configPath, Optional.empty(), Optional.empty());
    }

    public static ConfigUpdateResult updated(Path configPath, Path backupPath) {
        return new ConfigUpdateResult(ConfigUpdateStatus.UPDATED, configPath, Optional.of(backupPath), Optional.empty());
    }

    public static ConfigUpdateResult failed(Path configPath, String errorMessage) {
        return new ConfigUpdateResult(ConfigUpdateStatus.FAILED, configPath, Optional.empty(), Optional.ofNullable(errorMessage));
    }
}
