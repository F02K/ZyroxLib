package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpoint;
import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpointRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleZyroxDashboardEndpointRegistry implements ZyroxDashboardEndpointRegistry {

    private final Map<String, Map<String, ZyroxDashboardEndpoint>> endpointsByModule = new ConcurrentHashMap<>();

    @Override
    public void register(String moduleId, ZyroxDashboardEndpoint endpoint) {
        String normalizedModuleId = normalizeIdentifier(moduleId, "moduleId");
        Objects.requireNonNull(endpoint, "endpoint");
        endpointsByModule
                .computeIfAbsent(normalizedModuleId, ignored -> new ConcurrentHashMap<>())
                .put(endpoint.id(), endpoint);
    }

    @Override
    public void unregister(String moduleId, String endpointId) {
        Map<String, ZyroxDashboardEndpoint> endpoints = endpointsByModule.get(normalizeIdentifier(moduleId, "moduleId"));
        if (endpoints != null) {
            endpoints.remove(normalizeIdentifier(endpointId, "endpointId"));
        }
    }

    @Override
    public Optional<ZyroxDashboardEndpoint> get(String moduleId, String endpointId) {
        Map<String, ZyroxDashboardEndpoint> endpoints = endpointsByModule.get(normalizeIdentifier(moduleId, "moduleId"));
        if (endpoints == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(endpoints.get(normalizeIdentifier(endpointId, "endpointId")));
    }

    @Override
    public Collection<ZyroxDashboardEndpoint> allForModule(String moduleId) {
        Map<String, ZyroxDashboardEndpoint> endpoints = endpointsByModule.get(normalizeIdentifier(moduleId, "moduleId"));
        if (endpoints == null) {
            return List.of();
        }
        return endpoints.values().stream()
                .sorted(Comparator.comparing(ZyroxDashboardEndpoint::id))
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
