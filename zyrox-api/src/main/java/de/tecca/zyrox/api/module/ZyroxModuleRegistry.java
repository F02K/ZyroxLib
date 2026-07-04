package de.tecca.zyrox.api.module;

import java.util.Collection;
import java.util.Optional;

public interface ZyroxModuleRegistry {

    void register(ZyroxModule module);

    void unregister(String moduleId);

    Optional<ZyroxModule> get(String moduleId);

    Collection<ZyroxModule> all();
}

