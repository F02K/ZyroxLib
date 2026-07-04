package de.tecca.zyrox.api.setting;

import java.util.Collection;
import java.util.Optional;

public interface ZyroxSettingRegistry {

    void registerCategory(String moduleId, ZyroxSettingCategory category);

    void unregisterCategory(String moduleId, String categoryId);

    Collection<ZyroxSettingCategory> categoriesForModule(String moduleId);

    void register(String moduleId, ZyroxSetting setting);

    void unregister(String moduleId, String settingId);

    Optional<ZyroxSetting> get(String moduleId, String settingId);

    Collection<ZyroxSetting> allForModule(String moduleId);
}
