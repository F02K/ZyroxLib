package de.tecca.zyrox.api.web.endpoint;

import de.tecca.zyrox.api.ZyroxApi;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ZyroxDashboardEndpointRequest(
        ZyroxApi api,
        UUID actorUniqueId,
        String actorName,
        String method,
        Map<String, String> queryParameters,
        Map<String, String> formParameters,
        String body
) {

    public ZyroxDashboardEndpointRequest {
        api = Objects.requireNonNull(api, "api");
        actorUniqueId = Objects.requireNonNull(actorUniqueId, "actorUniqueId");
        actorName = Objects.requireNonNull(actorName, "actorName");
        method = Objects.requireNonNull(method, "method");
        queryParameters = Map.copyOf(Objects.requireNonNull(queryParameters, "queryParameters"));
        formParameters = Map.copyOf(Objects.requireNonNull(formParameters, "formParameters"));
        body = body == null ? "" : body;
    }

    public String parameter(String name) {
        String formValue = formParameters.get(name);
        if (formValue != null) {
            return formValue;
        }
        return queryParameters.getOrDefault(name, "");
    }
}
