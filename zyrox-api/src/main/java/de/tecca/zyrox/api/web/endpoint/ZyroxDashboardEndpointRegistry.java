package de.tecca.zyrox.api.web.endpoint;

import java.util.Collection;
import java.util.Optional;

public interface ZyroxDashboardEndpointRegistry {

    void register(String moduleId, ZyroxDashboardEndpoint endpoint);

    void unregister(String moduleId, String endpointId);

    Optional<ZyroxDashboardEndpoint> get(String moduleId, String endpointId);

    Collection<ZyroxDashboardEndpoint> allForModule(String moduleId);
}
