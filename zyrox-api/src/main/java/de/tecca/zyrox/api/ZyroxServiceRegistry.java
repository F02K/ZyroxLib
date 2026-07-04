package de.tecca.zyrox.api;

import java.util.Optional;

/**
 * Small typed registry for shared ZyroxMC services.
 */
public interface ZyroxServiceRegistry {

    /**
     * Registers or replaces a service implementation for the given type.
     *
     * @param type service type
     * @param service service implementation
     * @param <T> service type
     */
    <T> void register(Class<T> type, T service);

    /**
     * Gets a registered service by type.
     *
     * @param type service type
     * @param <T> service type
     * @return registered service, if present
     */
    <T> Optional<T> get(Class<T> type);

    /**
     * Unregisters a service by type.
     *
     * @param type service type
     * @param <T> service type
     */
    <T> void unregister(Class<T> type);
}

