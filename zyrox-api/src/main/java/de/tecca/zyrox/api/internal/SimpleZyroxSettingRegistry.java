package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleZyroxSettingRegistry implements ZyroxSettingRegistry {

    private final Map<String, Map<String, ZyroxSettingCategory>> categoriesByModule = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ZyroxSetting>> settingsByModule = new ConcurrentHashMap<>();

    @Override
    public void registerCategory(String moduleId, ZyroxSettingCategory category) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Objects.requireNonNull(category, "category");

        categoriesByModule
                .computeIfAbsent(normalizedModuleId, ignored -> new ConcurrentHashMap<>())
                .put(category.id(), category);
    }

    @Override
    public void unregisterCategory(String moduleId, String categoryId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        String normalizedCategoryId = normalizeIdentifier(categoryId, "categoryId");

        Map<String, ZyroxSettingCategory> moduleCategories = categoriesByModule.get(normalizedModuleId);
        if (moduleCategories != null) {
            moduleCategories.remove(normalizedCategoryId);
        }
    }

    @Override
    public Collection<ZyroxSettingCategory> categoriesForModule(String moduleId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Map<String, ZyroxSettingCategory> moduleCategories = categoriesByModule.get(normalizedModuleId);
        if (moduleCategories == null) {
            return List.of();
        }

        return moduleCategories.values().stream()
                .sorted(Comparator.comparingInt(ZyroxSettingCategory::order)
                        .thenComparing(ZyroxSettingCategory::id))
                .toList();
    }

    @Override
    public void register(String moduleId, ZyroxSetting setting) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Objects.requireNonNull(setting, "setting");

        settingsByModule
                .computeIfAbsent(normalizedModuleId, ignored -> new ConcurrentHashMap<>())
                .put(setting.id(), setting);
    }

    @Override
    public void unregister(String moduleId, String settingId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        String normalizedSettingId = normalizeIdentifier(settingId, "settingId");

        Map<String, ZyroxSetting> moduleSettings = settingsByModule.get(normalizedModuleId);
        if (moduleSettings != null) {
            moduleSettings.remove(normalizedSettingId);
        }
    }

    @Override
    public Optional<ZyroxSetting> get(String moduleId, String settingId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        String normalizedSettingId = normalizeIdentifier(settingId, "settingId");

        Map<String, ZyroxSetting> moduleSettings = settingsByModule.get(normalizedModuleId);
        if (moduleSettings == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(moduleSettings.get(normalizedSettingId));
    }

    @Override
    public Collection<ZyroxSetting> allForModule(String moduleId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Map<String, ZyroxSetting> moduleSettings = settingsByModule.get(normalizedModuleId);
        if (moduleSettings == null) {
            return List.of();
        }

        return moduleSettings.values().stream()
                .sorted(Comparator.comparingInt(ZyroxSetting::order)
                        .thenComparing(ZyroxSetting::id))
                .toList();
    }

    private static String normalizeIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (!text.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(name + " must contain only lowercase letters, numbers, dots, underscores, or dashes");
        }
        return text;
    }
}
