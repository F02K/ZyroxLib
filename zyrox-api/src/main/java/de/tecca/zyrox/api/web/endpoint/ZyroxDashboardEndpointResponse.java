package de.tecca.zyrox.api.web.endpoint;

import java.util.Objects;

public record ZyroxDashboardEndpointResponse(
        int status,
        String contentType,
        String body
) {

    public ZyroxDashboardEndpointResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid HTTP status");
        }
        contentType = Objects.requireNonNull(contentType, "contentType");
        body = body == null ? "" : body;
    }

    public static ZyroxDashboardEndpointResponse json(String body) {
        return json(200, body);
    }

    public static ZyroxDashboardEndpointResponse json(int status, String body) {
        return new ZyroxDashboardEndpointResponse(status, "application/json", body);
    }

    public static ZyroxDashboardEndpointResponse text(int status, String body) {
        return new ZyroxDashboardEndpointResponse(status, "text/plain; charset=utf-8", body);
    }
}
