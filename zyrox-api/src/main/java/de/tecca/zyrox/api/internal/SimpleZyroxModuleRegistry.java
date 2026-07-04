package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.module.ZyroxModuleRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleZyroxModuleRegistry implements ZyroxModuleRegistry {

    private final Map<String, ZyroxModule> modules = new ConcurrentHashMap<>();

    @Override
    public void register(ZyroxModule module) {
        Objects.requireNonNull(module, "module");
        modules.put(module.id(), module);
    }

    @Override
    public void unregister(String moduleId) {
        modules.remove(normalizeIdentifier(moduleId, "moduleId"));
    }

    @Override
    public Optional<ZyroxModule> get(String moduleId) {
        return Optional.ofNullable(modules.get(normalizeIdentifier(moduleId, "moduleId")));
    }

    @Override
    public Collection<ZyroxModule> all() {
        return modules.values().stream()
                .sorted(Comparator.comparing(ZyroxModule::id))
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

