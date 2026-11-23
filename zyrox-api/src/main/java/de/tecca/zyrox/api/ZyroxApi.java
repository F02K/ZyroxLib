package de.tecca.zyrox.api;

import de.tecca.zyrox.api.action.ZyroxActionRegistry;
import de.tecca.zyrox.api.module.ZyroxModuleRegistry;
import de.tecca.zyrox.api.permission.ZyroxPermissionService;
import de.tecca.zyrox.api.setting.ZyroxSettingRegistry;
import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpointRegistry;
import de.tecca.zyrox.api.web.ZyroxDashboardViewRegistry;
import de.tecca.zyrox.api.web.ZyroxWebPanel;

/**
 * Entry point for shared ZyroxMC server APIs.
 */
public interface ZyroxApi {

    /**
     * Returns the running ZyroxAPI plugin version.
     *
     * @return plugin version
     */
    String getVersion();

    /**
     * Returns the shared service registry for ZyroxMC plugins.
     *
     * @return service registry
     */
    ZyroxServiceRegistry services();

    /**
     * Returns the registry for ZyroxMC plugin modules.
     *
     * @return module registry
     */
    ZyroxModuleRegistry modules();

    /**
     * Returns the registry for permission-aware admin actions.
     *
     * @return action registry
     */
    ZyroxActionRegistry actions();

    /**
     * Returns the registry for plugin-owned editable settings.
     *
     * @return setting registry
     */
    ZyroxSettingRegistry settings();

    /**
     * Returns the browser dashboard integration.
     *
     * @return web panel
     */
    ZyroxWebPanel webPanel();

    /**
     * Returns the registry for plugin-owned dashboard views.
     *
     * @return dashboard view registry
     */
    ZyroxDashboardViewRegistry dashboardViews();

    /**
     * Returns the registry for plugin-owned dashboard HTTP endpoints.
     *
     * @return dashboard endpoint registry
     */
    ZyroxDashboardEndpointRegistry dashboardEndpoints();

    /**
     * Returns the permission service used by commands and web sessions.
     *
     * @return permission service
     */
    ZyroxPermissionService permissions();
}
