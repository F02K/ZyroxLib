package de.tecca.zyrox.api.action;

import java.util.Collection;
import java.util.Optional;

public interface ZyroxActionRegistry {

    void register(String moduleId, ZyroxAction action);

    void unregister(String moduleId, String actionId);

    Optional<ZyroxAction> get(String moduleId, String actionId);

    Collection<ZyroxAction> allForModule(String moduleId);

    Collection<ZyroxRegisteredAction> all();
}

