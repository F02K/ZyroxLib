package de.tecca.zyrox.protect.luckperms;

import de.tecca.zyrox.protect.audit.AuditRecord;
import de.tecca.zyrox.protect.audit.AuditStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class LuckPermsAuditBridge {

    private final JavaPlugin plugin;
    private final AuditStorage storage;
    private final Object luckPerms;

    public LuckPermsAuditBridge(JavaPlugin plugin, AuditStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.luckPerms = loadLuckPerms();
    }

    public void register() {
        if (luckPerms == null) {
            plugin.getLogger().info("LuckPerms was not detected. Permission mutation audit will use command capture only.");
            return;
        }
        subscribe("net.luckperms.api.event.node.NodeAddEvent", "PERMISSION_ADDED");
        subscribe("net.luckperms.api.event.node.NodeRemoveEvent", "PERMISSION_REMOVED");
        subscribe("net.luckperms.api.event.user.UserPromoteEvent", "USER_PROMOTED");
        subscribe("net.luckperms.api.event.user.UserDemoteEvent", "USER_DEMOTED");
        plugin.getLogger().info("LuckPerms audit bridge registered.");
    }

    private Object loadLuckPerms() {
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(luckPermsClass);
            return provider == null ? null : provider.getProvider();
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void subscribe(String eventClassName, String action) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            Method getEventBus = luckPerms.getClass().getMethod("getEventBus");
            Object eventBus = getEventBus.invoke(luckPerms);
            Method subscribe = eventBus.getClass().getMethod("subscribe", Object.class, Class.class, Consumer.class);
            subscribe.invoke(eventBus, plugin, eventClass, (Consumer) event -> record(event, action));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not subscribe to LuckPerms event " + eventClassName + ": " + exception.getMessage());
        }
    }

    private void record(Object event, String action) {
        String target = readValue(event, "getTarget");
        String node = readNestedValue(event, "getNode", "getKey");
        String userUuid = readNestedValue(event, "getUser", "getUniqueId");
        storage.record(new AuditRecord(
                "LUCKPERMS",
                "CRITICAL",
                "LUCKPERMS",
                parseUuid(userUuid),
                userUuid.isBlank() ? "LuckPerms" : userUuid,
                "PERMISSION",
                userUuid,
                target,
                action,
                "LuckPerms permission data changed.",
                Map.of("target", target, "node", node, "event", event.getClass().getSimpleName()),
                ""
        ));
    }

    private String readValue(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : value.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String readNestedValue(Object target, String firstMethod, String secondMethod) {
        try {
            Object first = target.getClass().getMethod(firstMethod).invoke(target);
            if (first == null) {
                return "";
            }
            Object second = first.getClass().getMethod(secondMethod).invoke(first);
            return second == null ? "" : second.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
