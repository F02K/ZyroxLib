package de.tecca.zyrox.api.internal.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxAction;
import de.tecca.zyrox.api.action.ZyroxActionParameter;
import de.tecca.zyrox.api.action.ZyroxActionResult;
import de.tecca.zyrox.api.action.ZyroxActionSource;
import de.tecca.zyrox.api.internal.SimpleZyroxActionContext;
import de.tecca.zyrox.api.internal.SimpleZyroxDashboardViewContext;
import de.tecca.zyrox.api.module.ZyroxModule;
import de.tecca.zyrox.api.setting.ZyroxSetting;
import de.tecca.zyrox.api.setting.ZyroxSettingCategory;
import de.tecca.zyrox.api.setting.ZyroxSettingResult;
import de.tecca.zyrox.api.setting.ZyroxSettingType;
import de.tecca.zyrox.api.web.ZyroxDashboardView;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DashboardHttpServer implements HttpHandler {

    private static final String SESSION_COOKIE = "zyrox_session";

    private final JavaPlugin plugin;
    private final ZyroxApi api;
    private final WebSessionStore sessionStore;
    private final HttpServer server;
    private final ExecutorService executorService;
    private boolean running;

    public DashboardHttpServer(JavaPlugin plugin, ZyroxApi api, WebSessionStore sessionStore, String bindAddress, int port) throws IOException {
        this.plugin = plugin;
        this.api = api;
        this.sessionStore = sessionStore;
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        this.server.createContext("/", this);
        this.executorService = Executors.newCachedThreadPool();
        this.server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        running = true;
    }

    public void stop() {
        server.stop(1);
        executorService.shutdownNow();
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/session/")) {
                handleSession(exchange, path.substring("/session/".length()));
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/dashboard".equals(path)) {
                handleDashboard(exchange);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/api/settings/")) {
                handleSettings(exchange, path.substring("/api/settings/".length()));
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && path.startsWith("/api/settings/")) {
                handleSettingSave(exchange, path.substring("/api/settings/".length()));
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/modules".equals(path)) {
                handleModules(exchange);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/views".equals(path)) {
                handleViews(exchange);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && path.startsWith("/api/actions/")) {
                handleAction(exchange, path.substring("/api/actions/".length()));
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && ("/".equals(path) || "/dashboard".equals(path))) {
                writeHtml(exchange, dashboardHtml());
                return;
            }

            writeText(exchange, 404, "Not found");
        } catch (Exception exception) {
            plugin.getLogger().warning("Dashboard request failed: " + exception.getMessage());
            writeText(exchange, 500, "Internal server error");
        } finally {
            exchange.close();
        }
    }

    private void handleSession(HttpExchange exchange, String token) throws IOException {
        Optional<SimpleZyroxWebSession> session = sessionStore.consumeToken(token);
        if (session.isEmpty()) {
            writeText(exchange, 401, "This ZyroxAPI session link is invalid or expired.");
            return;
        }

        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + session.get().id() + "; Path=/; HttpOnly; SameSite=Strict");
        writeHtml(exchange, dashboardHtml());
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        Optional<SimpleZyroxWebSession> session = readSession(exchange);
        if (session.isEmpty()) {
            writeText(exchange, 401, "{\"error\":\"unauthorized\"}", "application/json");
            return;
        }

        writeText(exchange, 200, dashboardJson(session.get()), "application/json");
    }

    private void handleModules(HttpExchange exchange) throws IOException {
        Optional<SimpleZyroxWebSession> session = readSession(exchange);
        if (session.isEmpty()) {
            writeText(exchange, 401, "{\"error\":\"unauthorized\"}", "application/json");
            return;
        }

        writeText(exchange, 200, modulesJson(session.get()), "application/json");
    }

    private void handleViews(HttpExchange exchange) throws Exception {
        Optional<SimpleZyroxWebSession> session = readSession(exchange);
        if (session.isEmpty()) {
            writeText(exchange, 401, "{\"error\":\"unauthorized\"}", "application/json");
            return;
        }

        writeText(exchange, 200, viewsJson(session.get()), "application/json");
    }

    private void handleSettings(HttpExchange exchange, String modulePath) throws IOException {
        Optional<SimpleZyroxWebSession> session = readSession(exchange);
        if (session.isEmpty()) {
            writeText(exchange, 401, "{\"error\":\"unauthorized\"}", "application/json");
            return;
        }

        String moduleId = decode(modulePath);
        Optional<ZyroxModule> module = api.modules().get(moduleId);
        if (module.isEmpty() || !canSeeModule(session.get(), module.get())) {
            writeText(exchange, 404, "{\"error\":\"module_not_found\"}", "application/json");
            return;
        }

        writeText(exchange, 200, settingsJson(session.get(), module.get()), "application/json");
    }

    private void handleAction(HttpExchange exchange, String actionPath) throws Exception {
        Optional<SimpleZyroxWebSession> session = readSession(exchange);
        if (session.isEmpty()) {
            writeText(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}", "application/json");
            return;
        }

        String[] parts = actionPath.split("/", 2);
        if (parts.length != 2) {
            writeText(exchange, 400, "{\"success\":false,\"message\":\"Invalid action path\"}", "application/json");
            return;
        }

        String moduleId = decode(parts[0]);
        String actionId = decode(parts[1]);
        Optional<ZyroxModule> module = api.modules().get(moduleId);
        Optional<ZyroxAction> action = api.actions().get(moduleId, actionId);
        if (module.isEmpty() || action.isEmpty()) {
            writeText(exchange, 404, "{\"success\":false,\"message\":\"Action not found\"}", "application/json");
            return;
        }

        SimpleZyroxWebSession webSession = session.get();
        if (!canSeeModule(webSession, module.get()) || !canRunAction(webSession, action.get())) {
            writeText(exchange, 403, "{\"success\":false,\"message\":\"Missing permission\"}", "application/json");
            return;
        }

        Map<String, String> parameters = parseForm(exchange);
        SimpleZyroxActionContext context = new SimpleZyroxActionContext(
                api,
                ZyroxActionSource.WEB,
                webSession.actorUniqueId(),
                webSession.actorName(),
                parameters
        );

        Future<ZyroxActionResult> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> action.get().handler().execute(context));
        ZyroxActionResult result = future.get(10, TimeUnit.SECONDS);
        writeText(exchange, 200, resultJson(result), "application/json");
    }

    private void handleSettingSave(HttpExchange exchange, String settingPath) throws Exception {
        Optional<SimpleZyroxWebSession> session = readSession(exchange);
        if (session.isEmpty()) {
            writeText(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}", "application/json");
            return;
        }

        String[] parts = settingPath.split("/", 2);
        if (parts.length != 2) {
            writeText(exchange, 400, "{\"success\":false,\"message\":\"Invalid setting path\"}", "application/json");
            return;
        }

        String moduleId = decode(parts[0]);
        String settingId = decode(parts[1]);
        Optional<ZyroxModule> module = api.modules().get(moduleId);
        Optional<ZyroxSetting> setting = api.settings().get(moduleId, settingId);
        if (module.isEmpty() || setting.isEmpty()) {
            writeText(exchange, 404, "{\"success\":false,\"message\":\"Setting not found\"}", "application/json");
            return;
        }

        SimpleZyroxWebSession webSession = session.get();
        if (!canSeeModule(webSession, module.get()) || !canSeeSetting(webSession, setting.get())) {
            writeText(exchange, 403, "{\"success\":false,\"message\":\"Missing permission\"}", "application/json");
            return;
        }

        Map<String, String> parameters = parseForm(exchange);
        String value = parameters.getOrDefault("value", "");
        ZyroxSetting currentSetting = setting.get();
        if (currentSetting.type() == ZyroxSettingType.SECRET && value.isBlank()) {
            writeText(exchange, 200, "{\"success\":true,\"message\":\"Secret value left unchanged.\"}", "application/json");
            return;
        }

        Optional<String> validationError = validateSettingValue(currentSetting, value);
        if (validationError.isPresent()) {
            writeText(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(validationError.get()) + "\"}", "application/json");
            return;
        }

        Future<ZyroxSettingResult> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> currentSetting.writer().write(value));
        ZyroxSettingResult result = future.get(10, TimeUnit.SECONDS);
        writeText(exchange, 200, resultJson(result), "application/json");
    }

    private Optional<SimpleZyroxWebSession> readSession(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) {
            return Optional.empty();
        }

        for (String part : cookie.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && SESSION_COOKIE.equals(pair[0])) {
                return sessionStore.findSession(pair[1]);
            }
        }
        return Optional.empty();
    }

    private boolean canSeeModule(SimpleZyroxWebSession session, ZyroxModule module) {
        return !module.hasPermission() || api.permissions().hasPermission(session.actorUniqueId(), module.permission());
    }

    private boolean canRunAction(SimpleZyroxWebSession session, ZyroxAction action) {
        return !action.hasPermission() || api.permissions().hasPermission(session.actorUniqueId(), action.permission());
    }

    private boolean canSeeView(SimpleZyroxWebSession session, ZyroxDashboardView view) {
        return !view.hasPermission() || api.permissions().hasPermission(session.actorUniqueId(), view.permission());
    }

    private boolean canSeeSetting(SimpleZyroxWebSession session, ZyroxSetting setting) {
        return !setting.hasPermission() || api.permissions().hasPermission(session.actorUniqueId(), setting.permission());
    }

    private String dashboardJson(SimpleZyroxWebSession session) {
        int visibleModules = 0;
        int visibleActions = 0;
        int visibleViews = 0;
        int visibleSettings = 0;

        StringBuilder modules = new StringBuilder();
        modules.append('[');
        boolean firstModule = true;
        for (ZyroxModule module : api.modules().all()) {
            if (!canSeeModule(session, module)) {
                continue;
            }

            visibleModules++;
            int moduleSettings = 0;
            for (ZyroxSetting setting : api.settings().allForModule(module.id())) {
                if (canSeeSetting(session, setting)) {
                    moduleSettings++;
                }
            }
            visibleSettings += moduleSettings;

            for (ZyroxAction action : api.actions().allForModule(module.id())) {
                if (canRunAction(session, action)) {
                    visibleActions++;
                }
            }

            if (!firstModule) {
                modules.append(',');
            }
            firstModule = false;
            modules.append("{\"id\":\"").append(escapeJson(module.id())).append("\",")
                    .append("\"name\":\"").append(escapeJson(module.name())).append("\",")
                    .append("\"description\":\"").append(escapeJson(module.description())).append("\",")
                    .append("\"settings\":").append(moduleSettings).append('}');
        }
        modules.append(']');

        for (ZyroxDashboardView view : api.dashboardViews().all()) {
            if (canSeeView(session, view)) {
                visibleViews++;
            }
        }

        return "{\"actor\":{\"name\":\"" + escapeJson(session.actorName()) + "\"},"
                + "\"stats\":{"
                + "\"apiVersion\":\"" + escapeJson(api.getVersion()) + "\","
                + "\"webStatus\":\"" + (api.webPanel().isRunning() ? "Running" : "Stopped") + "\","
                + "\"webPort\":" + api.webPanel().port() + ','
                + "\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size() + ','
                + "\"modules\":" + visibleModules + ','
                + "\"actions\":" + visibleActions + ','
                + "\"views\":" + visibleViews + ','
                + "\"settings\":" + visibleSettings
                + "},\"modules\":" + modules + "}";
    }

    private String modulesJson(SimpleZyroxWebSession session) {
        StringBuilder json = new StringBuilder();
        json.append("{\"actor\":{\"name\":\"").append(escapeJson(session.actorName())).append("\"},\"modules\":[");
        boolean firstModule = true;
        for (ZyroxModule module : api.modules().all()) {
            if (!canSeeModule(session, module)) {
                continue;
            }
            if (!firstModule) {
                json.append(',');
            }
            firstModule = false;
            json.append("{\"id\":\"").append(escapeJson(module.id())).append("\",")
                    .append("\"name\":\"").append(escapeJson(module.name())).append("\",")
                    .append("\"version\":\"").append(escapeJson(module.version())).append("\",")
                    .append("\"description\":\"").append(escapeJson(module.description())).append("\",")
                    .append("\"actions\":[");

            boolean firstAction = true;
            for (ZyroxAction action : api.actions().allForModule(module.id())) {
                if (!canRunAction(session, action)) {
                    continue;
                }
                if (!firstAction) {
                    json.append(',');
                }
                firstAction = false;
                appendActionJson(json, action);
            }
            json.append("]}");
        }
        json.append("]}");
        return json.toString();
    }

    private String settingsJson(SimpleZyroxWebSession session, ZyroxModule module) {
        StringBuilder json = new StringBuilder();
        json.append("{\"module\":{\"id\":\"").append(escapeJson(module.id())).append("\",")
                .append("\"name\":\"").append(escapeJson(module.name())).append("\",")
                .append("\"version\":\"").append(escapeJson(module.version())).append("\",")
                .append("\"description\":\"").append(escapeJson(module.description())).append("\"},")
                .append("\"categories\":[");

        boolean firstCategory = true;
        for (ZyroxSettingCategory category : api.settings().categoriesForModule(module.id())) {
            if (!firstCategory) {
                json.append(',');
            }
            firstCategory = false;
            json.append("{\"id\":\"").append(escapeJson(category.id())).append("\",")
                    .append("\"label\":\"").append(escapeJson(category.label())).append("\",")
                    .append("\"order\":").append(category.order()).append('}');
        }
        json.append("],\"settings\":[");

        boolean firstSetting = true;
        for (ZyroxSetting setting : api.settings().allForModule(module.id())) {
            if (!canSeeSetting(session, setting)) {
                continue;
            }
            if (!firstSetting) {
                json.append(',');
            }
            firstSetting = false;
            appendSettingJson(json, setting);
        }
        json.append("],\"actions\":[");

        boolean firstAction = true;
        for (ZyroxAction action : api.actions().allForModule(module.id())) {
            if (!canRunAction(session, action)) {
                continue;
            }
            if (!firstAction) {
                json.append(',');
            }
            firstAction = false;
            appendActionJson(json, action);
        }
        json.append("]}");
        return json.toString();
    }

    private void appendSettingJson(StringBuilder json, ZyroxSetting setting) {
        json.append("{\"id\":\"").append(escapeJson(setting.id())).append("\",")
                .append("\"label\":\"").append(escapeJson(setting.label())).append("\",")
                .append("\"description\":\"").append(escapeJson(setting.description())).append("\",")
                .append("\"categoryId\":\"").append(escapeJson(setting.categoryId())).append("\",")
                .append("\"type\":\"").append(setting.type().name().toLowerCase()).append("\",")
                .append("\"required\":").append(setting.required()).append(',')
                .append("\"order\":").append(setting.order()).append(',')
                .append("\"restartRequired\":").append(setting.restartRequired()).append(',');

        Object value;
        try {
            value = setting.reader().read();
        } catch (Exception exception) {
            plugin.getLogger().warning("Setting '" + setting.id() + "' could not be read: " + exception.getMessage());
            value = "";
        }

        if (setting.type() == ZyroxSettingType.SECRET) {
            json.append("\"value\":\"\",")
                    .append("\"hasValue\":").append(value != null && !String.valueOf(value).isBlank()).append('}');
            return;
        }

        if (setting.type() == ZyroxSettingType.BOOLEAN) {
            json.append("\"value\":").append(Boolean.parseBoolean(String.valueOf(value))).append('}');
            return;
        }

        json.append("\"value\":\"").append(escapeJson(String.valueOf(value == null ? "" : value))).append("\"}");
    }

    private void appendActionJson(StringBuilder json, ZyroxAction action) {
        json.append("{\"id\":\"").append(escapeJson(action.id())).append("\",")
                .append("\"label\":\"").append(escapeJson(action.label())).append("\",")
                .append("\"description\":\"").append(escapeJson(action.description())).append("\",")
                .append("\"parameters\":[");

        boolean firstParameter = true;
        for (ZyroxActionParameter parameter : action.parameters()) {
            if (!firstParameter) {
                json.append(',');
            }
            firstParameter = false;
            json.append("{\"id\":\"").append(escapeJson(parameter.id())).append("\",")
                    .append("\"label\":\"").append(escapeJson(parameter.label())).append("\",")
                    .append("\"type\":\"").append(parameter.type().name().toLowerCase()).append("\",")
                    .append("\"required\":").append(parameter.required()).append('}');
        }
        json.append("]}");
    }

    private String viewsJson(SimpleZyroxWebSession session) {
        StringBuilder json = new StringBuilder();
        SimpleZyroxDashboardViewContext context = new SimpleZyroxDashboardViewContext(
                api,
                session.actorUniqueId(),
                session.actorName()
        );

        json.append("{\"views\":[");
        boolean firstView = true;
        for (ZyroxDashboardView view : api.dashboardViews().all()) {
            if (!canSeeView(session, view)) {
                continue;
            }
            if (!firstView) {
                json.append(',');
            }
            firstView = false;

            String html;
            try {
                html = view.renderer().render(context);
            } catch (Exception exception) {
                plugin.getLogger().warning("Dashboard view '" + view.id() + "' failed: " + exception.getMessage());
                html = "<p class=\"muted\">This view could not be rendered.</p>";
            }

            json.append("{\"id\":\"").append(escapeJson(view.id())).append("\",")
                    .append("\"label\":\"").append(escapeJson(view.label())).append("\",")
                    .append("\"html\":\"").append(escapeJson(html)).append("\"}");
        }
        json.append("]}");
        return json.toString();
    }

    private Optional<String> validateSettingValue(ZyroxSetting setting, String value) {
        if (setting.required() && value.isBlank()) {
            return Optional.of(setting.label() + " is required.");
        }

        if (value.isBlank()) {
            return Optional.empty();
        }

        try {
            if (setting.type() == ZyroxSettingType.INTEGER) {
                Integer.parseInt(value);
            }
            if (setting.type() == ZyroxSettingType.DECIMAL) {
                Double.parseDouble(value);
            }
        } catch (NumberFormatException exception) {
            return Optional.of(setting.label() + " has an invalid number format.");
        }

        return Optional.empty();
    }

    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String text = new String(body, StandardCharsets.UTF_8);
        Map<String, String> parameters = new LinkedHashMap<>();
        if (text.isBlank()) {
            return parameters;
        }

        for (String part : text.split("&")) {
            String[] pair = part.split("=", 2);
            String key = decode(pair[0]);
            String value = pair.length == 2 ? decode(pair[1]) : "";
            parameters.put(key, value);
        }
        return parameters;
    }

    private String resultJson(ZyroxActionResult result) {
        return "{\"success\":" + result.success() + ",\"message\":\"" + escapeJson(result.message()) + "\"}";
    }

    private String resultJson(ZyroxSettingResult result) {
        return "{\"success\":" + result.success() + ",\"message\":\"" + escapeJson(result.message()) + "\"}";
    }

    private String dashboardHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>ZyroxMC Dashboard</title>
                    <style>
                        :root { color-scheme: dark; font-family: Inter, Segoe UI, Arial, sans-serif; }
                        * { box-sizing: border-box; }
                        body { margin: 0; background: #101418; color: #edf2f7; }
                        button, input, textarea { font: inherit; }
                        .shell { min-height: 100vh; display: grid; grid-template-columns: 260px 1fr; }
                        aside { border-right: 1px solid #2d3748; background: #151a20; padding: 22px 18px; position: sticky; top: 0; height: 100vh; overflow-y: auto; }
                        .brand { margin-bottom: 22px; }
                        .brand h1 { margin: 0; font-size: 22px; }
                        .brand p { margin: 6px 0 0; }
                        nav { display: grid; gap: 8px; }
                        .nav-label { margin: 18px 0 4px; color: #718096; font-size: 12px; font-weight: 800; letter-spacing: 0; text-transform: uppercase; }
                        .nav-button { width: 100%; border: 1px solid transparent; border-radius: 6px; padding: 10px 11px; color: #cbd5e0; background: transparent; text-align: left; cursor: pointer; }
                        .nav-button:hover { background: #1f2630; }
                        .nav-button.active { border-color: #63b3ed; background: #1b2a37; color: #edf2f7; }
                        main { padding: 30px; max-width: 1180px; width: 100%; }
                        h1, h2, h3 { margin: 0; }
                        h2 { font-size: 25px; }
                        h3 { font-size: 17px; }
                        .muted { color: #a0aec0; }
                        .page-head { display: flex; justify-content: space-between; gap: 18px; align-items: flex-start; margin-bottom: 22px; }
                        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
                        .stat, .panel, form { border: 1px solid #2d3748; border-radius: 8px; background: #181d24; }
                        .stat { padding: 16px; min-height: 96px; }
                        .stat strong { display: block; margin-top: 10px; font-size: 25px; }
                        .panel { padding: 18px; margin-top: 16px; }
                        .category { margin-top: 22px; }
                        .settings-grid, .actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; margin-top: 12px; }
                        form { padding: 14px; background: #12161c; }
                        .form-head { min-height: 76px; }
                        label { display: block; margin-top: 10px; color: #cbd5e0; font-size: 13px; }
                        input, textarea { width: 100%; margin-top: 5px; border: 1px solid #4a5568; border-radius: 6px; padding: 9px; background: #0f1318; color: #edf2f7; }
                        input[type="checkbox"] { width: auto; margin-right: 8px; transform: translateY(1px); }
                        textarea { min-height: 88px; resize: vertical; }
                        .row { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
                        .pill { display: inline-flex; align-items: center; border-radius: 999px; padding: 3px 8px; font-size: 12px; font-weight: 800; background: #2d3748; color: #e2e8f0; }
                        .warn { background: #744210; color: #fefcbf; }
                        .primary { margin-top: 12px; border: 0; border-radius: 6px; padding: 10px 12px; color: #081018; background: #63b3ed; font-weight: 800; cursor: pointer; }
                        pre { white-space: pre-wrap; color: #9ae6b4; min-height: 20px; }
                        .empty { border: 1px dashed #3c4655; border-radius: 8px; padding: 18px; color: #a0aec0; }
                        @media (max-width: 780px) {
                            .shell { grid-template-columns: 1fr; }
                            aside { position: static; height: auto; }
                            main { padding: 22px; }
                            .page-head { display: block; }
                        }
                    </style>
                </head>
                <body>
                    <div class="shell">
                        <aside>
                            <div class="brand">
                                <h1>ZyroxMC</h1>
                                <p id="actor" class="muted">Loading session...</p>
                            </div>
                            <nav id="nav"></nav>
                        </aside>
                        <main id="app"></main>
                    </div>
                    <script>
                        let dashboard = null;
                        let activeView = 'main';

                        async function loadDashboard() {
                            const response = await fetch('/api/dashboard');
                            if (!response.ok) {
                                document.getElementById('app').innerHTML = '<p class="empty">Your session is invalid or expired. Create a new link with /zyrox web.</p>';
                                return;
                            }
                            dashboard = await response.json();
                            document.getElementById('actor').textContent = 'Signed in as ' + dashboard.actor.name;
                            renderNav();
                            renderMain();
                        }

                        function renderNav() {
                            const nav = document.getElementById('nav');
                            nav.innerHTML = '';
                            nav.appendChild(navButton('main', 'Main', renderMain));
                            if (dashboard.stats.views > 0) {
                                nav.appendChild(navButton('views', 'Views', renderViews));
                            }
                            const label = document.createElement('div');
                            label.className = 'nav-label';
                            label.textContent = 'Plugins';
                            nav.appendChild(label);
                            for (const module of dashboard.modules) {
                                nav.appendChild(navButton(module.id, module.name, () => renderModule(module.id)));
                            }
                        }

                        function navButton(id, label, handler) {
                            const button = document.createElement('button');
                            button.className = 'nav-button' + (activeView === id ? ' active' : '');
                            button.type = 'button';
                            button.textContent = label;
                            button.addEventListener('click', () => {
                                activeView = id;
                                renderNav();
                                handler();
                            });
                            return button;
                        }

                        function renderMain() {
                            activeView = 'main';
                            const stats = dashboard.stats;
                            const app = document.getElementById('app');
                            app.innerHTML = '<div class="page-head"><div><h2>Main</h2><p class="muted">Central ZyroxAPI dashboard overview.</p></div></div>';
                            const grid = document.createElement('div');
                            grid.className = 'grid';
                            addStat(grid, 'API Version', stats.apiVersion);
                            addStat(grid, 'Dashboard', stats.webStatus + ' :' + stats.webPort);
                            addStat(grid, 'Online Players', stats.onlinePlayers);
                            addStat(grid, 'Modules', stats.modules);
                            addStat(grid, 'Actions', stats.actions);
                            addStat(grid, 'Views', stats.views);
                            addStat(grid, 'Settings', stats.settings);
                            app.appendChild(grid);

                            const modules = document.createElement('section');
                            modules.className = 'panel';
                            modules.innerHTML = '<h3>Plugins</h3>';
                            const list = document.createElement('div');
                            list.className = 'settings-grid';
                            for (const module of dashboard.modules) {
                                const item = document.createElement('div');
                                item.className = 'stat';
                                item.innerHTML = '<span class="muted">' + escapeHtml(module.id) + '</span><strong>' + escapeHtml(module.name) + '</strong><p class="muted">' + escapeHtml(module.description || 'No description.') + '</p><span class="pill">' + module.settings + ' settings</span>';
                                list.appendChild(item);
                            }
                            modules.appendChild(list);
                            app.appendChild(modules);
                        }

                        async function renderViews() {
                            activeView = 'views';
                            const app = document.getElementById('app');
                            app.innerHTML = '<p class="empty">Loading dashboard views...</p>';
                            const response = await fetch('/api/views');
                            if (!response.ok) {
                                app.innerHTML = '<p class="empty">Dashboard views could not be loaded.</p>';
                                return;
                            }
                            const data = await response.json();
                            app.innerHTML = '<div class="page-head"><div><h2>Views</h2><p class="muted">Plugin-owned dashboard views.</p></div></div>';
                            if (data.views.length === 0) {
                                app.innerHTML += '<p class="empty">No visible views are registered.</p>';
                                return;
                            }
                            for (const view of data.views) {
                                const section = document.createElement('section');
                                section.className = 'panel';
                                section.innerHTML = '<h3>' + escapeHtml(view.label) + '</h3>' + view.html;
                                app.appendChild(section);
                            }
                        }

                        function addStat(grid, label, value) {
                            const stat = document.createElement('div');
                            stat.className = 'stat';
                            stat.innerHTML = '<span class="muted">' + escapeHtml(label) + '</span><strong>' + escapeHtml(value) + '</strong>';
                            grid.appendChild(stat);
                        }

                        async function renderModule(moduleId) {
                            const app = document.getElementById('app');
                            app.innerHTML = '<p class="empty">Loading plugin settings...</p>';
                            const response = await fetch('/api/settings/' + encodeURIComponent(moduleId));
                            if (!response.ok) {
                                app.innerHTML = '<p class="empty">This plugin is not visible or your session expired.</p>';
                                return;
                            }
                            const data = await response.json();
                            app.innerHTML = '<div class="page-head"><div><h2>' + escapeHtml(data.module.name) + '</h2><p class="muted">' + escapeHtml(data.module.description || data.module.id) + ' - ' + escapeHtml(data.module.version) + '</p></div></div>';
                            renderSettings(data);
                            renderActions(data.module, data.actions);
                        }

                        function renderSettings(data) {
                            const app = document.getElementById('app');
                            if (data.settings.length === 0) {
                                const empty = document.createElement('p');
                                empty.className = 'empty';
                                empty.textContent = 'No visible settings are registered for this plugin.';
                                app.appendChild(empty);
                                return;
                            }

                            const categories = new Map(data.categories.map(category => [category.id, category]));
                            for (const category of data.categories) {
                                const settings = data.settings.filter(setting => setting.categoryId === category.id);
                                if (settings.length === 0) continue;
                                renderCategory(app, category, settings);
                            }
                            const uncategorized = data.settings.filter(setting => !categories.has(setting.categoryId));
                            if (uncategorized.length > 0) {
                                renderCategory(app, { id: 'uncategorized', label: 'Other' }, uncategorized);
                            }
                        }

                        function renderCategory(app, category, settings) {
                            const section = document.createElement('section');
                            section.className = 'category';
                            section.innerHTML = '<h3>' + escapeHtml(category.label) + '</h3>';
                            const grid = document.createElement('div');
                            grid.className = 'settings-grid';
                            for (const setting of settings) {
                                grid.appendChild(settingForm(setting));
                            }
                            section.appendChild(grid);
                            app.appendChild(section);
                        }

                        function settingForm(setting) {
                            const form = document.createElement('form');
                            form.innerHTML = '<div class="form-head"><h3>' + escapeHtml(setting.label) + '</h3><p class="muted">' + escapeHtml(setting.description || setting.id) + '</p>' + (setting.restartRequired ? '<span class="pill warn">Restart required</span>' : '') + '</div>';
                            const label = document.createElement('label');
                            label.textContent = setting.type === 'secret' && setting.hasValue ? 'New value (currently set)' : 'Value';

                            let input;
                            if (setting.type === 'boolean') {
                                input = document.createElement('input');
                                input.type = 'checkbox';
                                input.checked = Boolean(setting.value);
                                const row = document.createElement('div');
                                row.className = 'row';
                                row.appendChild(input);
                                row.appendChild(document.createTextNode('Enabled'));
                                form.appendChild(row);
                            } else if (setting.id.includes('description') || setting.id.includes('message')) {
                                input = document.createElement('textarea');
                                input.value = setting.value || '';
                                label.appendChild(input);
                                form.appendChild(label);
                            } else {
                                input = document.createElement('input');
                                input.type = setting.type === 'secret' ? 'password' : setting.type === 'integer' || setting.type === 'decimal' ? 'number' : 'text';
                                if (setting.type === 'decimal') input.step = 'any';
                                input.value = setting.type === 'secret' ? '' : setting.value || '';
                                input.required = setting.required && setting.type !== 'secret';
                                label.appendChild(input);
                                form.appendChild(label);
                            }

                            const button = document.createElement('button');
                            button.type = 'submit';
                            button.className = 'primary';
                            button.textContent = 'Save';
                            const output = document.createElement('pre');
                            form.appendChild(button);
                            form.appendChild(output);
                            form.addEventListener('submit', async event => {
                                event.preventDefault();
                                const body = new URLSearchParams();
                                body.set('value', input.type === 'checkbox' ? String(input.checked) : input.value);
                                const response = await fetch('/api/settings/' + encodeURIComponent(activeView) + '/' + encodeURIComponent(setting.id), {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                    body
                                });
                                const result = await response.json();
                                output.textContent = result.message;
                                output.style.color = result.success ? '#9ae6b4' : '#feb2b2';
                                if (result.success) {
                                    const current = activeView;
                                    const fresh = await fetch('/api/dashboard');
                                    if (fresh.ok) {
                                        dashboard = await fresh.json();
                                        activeView = current;
                                        renderNav();
                                    }
                                }
                            });
                            return form;
                        }

                        function renderActions(module, actions) {
                            if (!actions || actions.length === 0) return;
                            const app = document.getElementById('app');
                            const section = document.createElement('section');
                            section.className = 'category';
                            section.innerHTML = '<h3>Actions</h3>';
                            const grid = document.createElement('div');
                            grid.className = 'actions';
                            for (const action of actions) {
                                grid.appendChild(actionForm(module, action));
                            }
                            section.appendChild(grid);
                            app.appendChild(section);
                        }

                        function actionForm(module, action) {
                            const form = document.createElement('form');
                            form.innerHTML = '<div class="form-head"><h3>' + escapeHtml(action.label) + '</h3><p class="muted">' + escapeHtml(action.description || action.id) + '</p></div>';
                            for (const parameter of action.parameters) {
                                const label = document.createElement('label');
                                label.textContent = parameter.label;
                                const input = document.createElement('input');
                                input.name = parameter.id;
                                input.required = parameter.required;
                                input.type = parameter.type === 'boolean' ? 'checkbox' : 'text';
                                label.appendChild(input);
                                form.appendChild(label);
                            }
                            const button = document.createElement('button');
                            button.type = 'submit';
                            button.className = 'primary';
                            button.textContent = 'Run';
                            const output = document.createElement('pre');
                            form.appendChild(button);
                            form.appendChild(output);
                            form.addEventListener('submit', async event => {
                                event.preventDefault();
                                const body = new URLSearchParams();
                                for (const element of form.elements) {
                                    if (!element.name) continue;
                                    body.set(element.name, element.type === 'checkbox' ? String(element.checked) : element.value);
                                }
                                const response = await fetch('/api/actions/' + encodeURIComponent(module.id) + '/' + encodeURIComponent(action.id), {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                    body
                                });
                                const result = await response.json();
                                output.textContent = result.message;
                                output.style.color = result.success ? '#9ae6b4' : '#feb2b2';
                            });
                            return form;
                        }

                        function escapeHtml(value) {
                            return String(value).replace(/[&<>"']/g, character => ({
                                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
                            }[character]));
                        }

                        loadDashboard();
                    </script>
                </body>
                </html>
                """;
    }

    private void writeHtml(HttpExchange exchange, String body) throws IOException {
        writeText(exchange, 200, body, "text/html; charset=utf-8");
    }

    private void writeText(HttpExchange exchange, int status, String body) throws IOException {
        writeText(exchange, status, body, "text/plain; charset=utf-8");
    }

    private void writeText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }
}
