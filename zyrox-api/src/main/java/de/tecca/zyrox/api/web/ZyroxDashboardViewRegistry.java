package de.tecca.zyrox.api.web;

import java.util.Collection;
import java.util.Optional;

public interface ZyroxDashboardViewRegistry {

    void register(ZyroxDashboardView view);

    void unregister(String viewId);

    Optional<ZyroxDashboardView> get(String viewId);

    Collection<ZyroxDashboardView> all();
}

