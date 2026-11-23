package de.tecca.zyrox.api.web.endpoint;

@FunctionalInterface
public interface ZyroxDashboardEndpointHandler {

    ZyroxDashboardEndpointResponse handle(ZyroxDashboardEndpointRequest request) throws Exception;
}
