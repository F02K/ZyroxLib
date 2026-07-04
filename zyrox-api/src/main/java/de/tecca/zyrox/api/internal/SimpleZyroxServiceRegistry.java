package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.ZyroxServiceRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleZyroxServiceRegistry implements ZyroxServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, T service) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(service, "service");

        services.put(type, type.cast(service));
    }

    @Override
    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");

        return Optional.ofNullable(services.get(type)).map(type::cast);
    }

    @Override
    public <T> void unregister(Class<T> type) {
        Objects.requireNonNull(type, "type");

        services.remove(type);
    }
}

