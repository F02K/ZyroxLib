package de.tecca.zyrox.api.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class ConfigUpdater {

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigUpdater() {
    }

    public static ConfigUpdateResult update(JavaPlugin plugin, String resourcePath) {
        Objects.requireNonNull(plugin, "plugin");
        return update(
                plugin.getDataFolder().toPath(),
                resourcePath,
                () -> plugin.getResource(resourcePath),
                plugin.getLogger(),
                Clock.systemDefaultZone()
        );
    }

    static ConfigUpdateResult update(
            Path dataFolder,
            String resourcePath,
            Supplier<InputStream> resourceSupplier,
            Logger logger,
            Clock clock
    ) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(resourceSupplier, "resourceSupplier");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(clock, "clock");

        Path configPath = dataFolder.resolve(resourcePath).normalize();
        byte[] defaultBytes;
        try (InputStream inputStream = resourceSupplier.get()) {
            if (inputStream == null) {
                return fail(logger, configPath, "Bundled config resource not found: " + resourcePath);
            }
            defaultBytes = inputStream.readAllBytes();
        } catch (IOException exception) {
            return fail(logger, configPath, "Failed to read bundled config resource: " + exception.getMessage());
        }

        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                Files.copy(new ByteArrayInputStream(defaultBytes), configPath);
                logger.info("Created default config " + resourcePath + ".");
                return ConfigUpdateResult.created(configPath);
            }

            String currentText = Files.readString(configPath, StandardCharsets.UTF_8);
            YamlConfiguration current = load(currentText);
            YamlConfiguration merged = load(defaultBytes);
            mergeCurrentValues(current, merged);
            String mergedText = merged.saveToString();
            if (currentText.equals(mergedText)) {
                logger.info("Config " + resourcePath + " is already up to date.");
                return ConfigUpdateResult.unchanged(configPath);
            }

            Path backupPath = backupPath(dataFolder, configPath, clock);
            Files.createDirectories(backupPath.getParent());
            Files.copy(configPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            Files.writeString(configPath, mergedText, StandardCharsets.UTF_8);
            logger.info("Updated config " + resourcePath + " and wrote backup to " + backupPath + ".");
            return ConfigUpdateResult.updated(configPath, backupPath);
        } catch (Exception exception) {
            return fail(logger, configPath, "Failed to update config " + resourcePath + ": " + exception.getMessage());
        }
    }

    private static void mergeCurrentValues(YamlConfiguration current, YamlConfiguration merged) {
        Set<String> paths = new TreeSet<>(Comparator.comparingInt(ConfigUpdater::depth).thenComparing(path -> path));
        paths.addAll(current.getKeys(true));
        for (String path : paths) {
            copyComments(current, merged, path);
            if (current.isConfigurationSection(path)) {
                if (!merged.contains(path)) {
                    merged.createSection(path);
                }
                continue;
            }
            if (current.isSet(path)) {
                merged.set(path, current.get(path));
            }
        }
    }

    private static void copyComments(YamlConfiguration current, YamlConfiguration merged, String path) {
        List<String> comments = current.getComments(path);
        if (!comments.isEmpty()) {
            merged.setComments(path, comments);
        }
        List<String> inlineComments = current.getInlineComments(path);
        if (!inlineComments.isEmpty()) {
            merged.setInlineComments(path, inlineComments);
        }
    }

    private static int depth(String path) {
        int depth = 0;
        for (int index = 0; index < path.length(); index++) {
            if (path.charAt(index) == '.') {
                depth++;
            }
        }
        return depth;
    }

    private static YamlConfiguration load(byte[] bytes) throws InvalidConfigurationException {
        return load(new String(bytes, StandardCharsets.UTF_8));
    }

    private static YamlConfiguration load(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(text);
        return yaml;
    }

    private static Path backupPath(Path dataFolder, Path configPath, Clock clock) {
        String fileName = configPath.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex <= 0 ? fileName : fileName.substring(0, extensionIndex);
        String extension = extensionIndex <= 0 ? ".yml" : fileName.substring(extensionIndex);
        String timestamp = LocalDateTime.now(clock).format(BACKUP_TIMESTAMP);
        return dataFolder.resolve("config-backups").resolve(baseName + "-" + timestamp + extension).normalize();
    }

    private static ConfigUpdateResult fail(Logger logger, Path configPath, String message) {
        logger.severe(message);
        return ConfigUpdateResult.failed(configPath, message);
    }
}
