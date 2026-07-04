package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionRegistry;
import de.tecca.zyrox.api.action.ZyroxRegisteredAction;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleZyroxActionRegistry implements ZyroxActionRegistry {

    private final Map<String, Map<String, ZyroxAction>> actionsByModule = new ConcurrentHashMap<>();

    @Override
    public void register(String moduleId, ZyroxAction action) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Objects.requireNonNull(action, "action");

        actionsByModule
                .computeIfAbsent(normalizedModuleId, ignored -> new ConcurrentHashMap<>())
                .put(action.id(), action);
    }

    @Override
    public void unregister(String moduleId, String actionId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        String normalizedActionId = normalizeIdentifier(actionId, "actionId");

        Map<String, ZyroxAction> moduleActions = actionsByModule.get(normalizedModuleId);
        if (moduleActions != null) {
            moduleActions.remove(normalizedActionId);
        }
    }

    @Override
    public Optional<ZyroxAction> get(String moduleId, String actionId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        String normalizedActionId = normalizeIdentifier(actionId, "actionId");

        Map<String, ZyroxAction> moduleActions = actionsByModule.get(normalizedModuleId);
        if (moduleActions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(moduleActions.get(normalizedActionId));
    }

    @Override
    public Collection<ZyroxAction> allForModule(String moduleId) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Map<String, ZyroxAction> moduleActions = actionsByModule.get(normalizedModuleId);
        if (moduleActions == null) {
            return List.of();
        }

        return moduleActions.values().stream()
                .sorted(Comparator.comparing(ZyroxAction::id))
                .toList();
    }

    @Override
    public Collection<ZyroxRegisteredAction> all() {
        return actionsByModule.entrySet().stream()
                .flatMap(entry -> entry.getValue().values().stream()
                        .map(action -> new ZyroxRegisteredAction(entry.getKey(), action)))
                .sorted(Comparator.comparing(ZyroxRegisteredAction::moduleId)
                        .thenComparing(registeredAction -> registeredAction.action().id()))
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

