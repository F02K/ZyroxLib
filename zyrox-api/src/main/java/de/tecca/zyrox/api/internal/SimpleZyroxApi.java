package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.ZyroxServiceRegistry;
import de.tecca.zyrox.api.action.ZyroxActionRegistry;
import de.tecca.zyrox.api.module.ZyroxModuleRegistry;
import de.tecca.zyrox.api.permission.ZyroxPermissionService;
import de.tecca.zyrox.api.setting.ZyroxSettingRegistry;
import de.tecca.zyrox.api.web.ZyroxDashboardViewRegistry;
import de.tecca.zyrox.api.web.ZyroxWebPanel;

import java.util.Objects;

public final class SimpleZyroxApi implements ZyroxApi {

    private final String version;
    private final ZyroxServiceRegistry services;
    private final ZyroxModuleRegistry modules;
    private final ZyroxActionRegistry actions;
    private final ZyroxSettingRegistry settings;
    private final ZyroxWebPanel webPanel;
    private final ZyroxDashboardViewRegistry dashboardViews;
    private final ZyroxPermissionService permissions;

    public SimpleZyroxApi(
            String version,
            ZyroxServiceRegistry services,
            ZyroxModuleRegistry modules,
            ZyroxActionRegistry actions,
            ZyroxSettingRegistry settings,
            ZyroxWebPanel webPanel,
            ZyroxDashboardViewRegistry dashboardViews,
            ZyroxPermissionService permissions
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.services = Objects.requireNonNull(services, "services");
        this.modules = Objects.requireNonNull(modules, "modules");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.webPanel = Objects.requireNonNull(webPanel, "webPanel");
        this.dashboardViews = Objects.requireNonNull(dashboardViews, "dashboardViews");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public ZyroxServiceRegistry services() {
        return services;
    }

    @Override
    public ZyroxModuleRegistry modules() {
        return modules;
    }

    @Override
    public ZyroxActionRegistry actions() {
        return actions;
    }

    @Override
    public ZyroxSettingRegistry settings() {
        return settings;
    }

    @Override
    public ZyroxWebPanel webPanel() {
        return webPanel;
    }

    @Override
    public ZyroxDashboardViewRegistry dashboardViews() {
        return dashboardViews;
    }

    @Override
    public ZyroxPermissionService permissions() {
        return permissions;
    }
}
