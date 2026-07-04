package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.web.ZyroxDashboardView;
import de.tecca.zyrox.api.web.ZyroxDashboardViewRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleZyroxDashboardViewRegistry implements ZyroxDashboardViewRegistry {

    private final Map<String, ZyroxDashboardView> views = new ConcurrentHashMap<>();

    @Override
    public void register(ZyroxDashboardView view) {
        Objects.requireNonNull(view, "view");
        views.put(view.id(), view);
    }

    @Override
    public void unregister(String viewId) {
        views.remove(normalizeIdentifier(viewId, "viewId"));
    }

    @Override
    public Optional<ZyroxDashboardView> get(String viewId) {
        return Optional.ofNullable(views.get(normalizeIdentifier(viewId, "viewId")));
    }

    @Override
    public Collection<ZyroxDashboardView> all() {
        return views.values().stream()
                .sorted(Comparator.comparing(ZyroxDashboardView::id))
                .toList();
    }

    private static String normalizeIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (!text.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(name + " must contain only lowercase letters, numbers, dots, underscores, or dashes");
        }
        return text;
    }
}
