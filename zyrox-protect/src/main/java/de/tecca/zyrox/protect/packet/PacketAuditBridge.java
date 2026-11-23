package de.tecca.zyrox.protect.packet;

import de.tecca.zyrox.protect.audit.AuditRecord;
import de.tecca.zyrox.protect.audit.AuditStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketAuditBridge {

    private final JavaPlugin plugin;
    private final AuditStorage storage;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private int rateLimitPerMinute;

    public PacketAuditBridge(JavaPlugin plugin, AuditStorage storage, int rateLimitPerMinute) {
        this.plugin = plugin;
        this.storage = storage;
        this.rateLimitPerMinute = Math.max(1, rateLimitPerMinute);
    }

    public void reload(int rateLimitPerMinute) {
        this.rateLimitPerMinute = Math.max(1, rateLimitPerMinute);
    }

    public void register() {
        try {
            Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
            Object api = packetEventsClass.getMethod("getAPI").invoke(null);
            Object eventManager = api.getClass().getMethod("getEventManager").invoke(api);
            Class<?> listenerClass = Class.forName("com.github.retrooper.packetevents.event.PacketListener");
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onPacketReceive".equals(method.getName()) && args != null && args.length == 1) {
                            recordPacket(args[0]);
                        }
                        return null;
                    }
            );
            Class<?> priorityClass = Class.forName("com.github.retrooper.packetevents.event.PacketListenerPriority");
            Object normalPriority = Enum.valueOf((Class<Enum>) priorityClass.asSubclass(Enum.class), "NORMAL");
            Method registerListener = eventManager.getClass().getMethod("registerListener", listenerClass, priorityClass);
            registerListener.invoke(eventManager, listener, normalPriority);
            plugin.getLogger().info("PacketEvents audit bridge registered.");
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().info("PacketEvents was not detected. Packet audit is disabled.");
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not register PacketEvents audit bridge: " + exception.getMessage());
        }
    }

    private void recordPacket(Object event) {
        String packetType = readValue(event, "getPacketType");
        String user = readValue(event, "getUser");
        String key = user + "|" + packetType;
        if (!allow(key)) {
            return;
        }
        storage.record(new AuditRecord(
                "PACKET",
                "INFO",
                "PLAYER",
                Optional.empty(),
                user,
                "PACKET",
                "",
                packetType,
                "PACKET_RECEIVED",
                "Inbound player packet observed.",
                Map.of("packetType", packetType),
                ""
        ));
    }

    private boolean allow(String key) {
        Instant now = Instant.now();
        RateWindow current = rateWindows.get(key);
        if (current == null || current.expiresAt().isBefore(now)) {
            rateWindows.put(key, new RateWindow(1, now.plusSeconds(60)));
            return true;
        }
        if (current.count() >= rateLimitPerMinute) {
            return false;
        }
        rateWindows.put(key, new RateWindow(current.count() + 1, current.expiresAt()));
        return true;
    }

    private String readValue(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : value.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private record RateWindow(int count, Instant expiresAt) {
    }
}
