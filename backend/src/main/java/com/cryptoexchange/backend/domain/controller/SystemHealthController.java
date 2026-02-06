package com.cryptoexchange.backend.domain.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System health endpoints")
public class SystemHealthController {

    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping("/health")
    @Operation(summary = "Get system health", description = "Returns simplified system health status")
    public ResponseEntity<SystemHealth> getHealth() {
        // Use LinkedHashMap to preserve insertion order (API → DB → Redis → Kafka)
        Map<String, String> status = new LinkedHashMap<>();

        try {
            Object healthEndpoint = applicationContext.getBean("healthEndpoint");

            if (healthEndpoint != null) {
                java.lang.reflect.Method healthMethod = healthEndpoint.getClass().getMethod("health");
                Object health = healthMethod.invoke(healthEndpoint);

                // Spring Boot 4.x uses getComponents(), earlier versions use getDetails()
                Map<String, Object> components = extractComponents(health);

                status.put("api", "OK");
                status.put("db", extractComponentStatus(components, "db"));
                status.put("redis", extractComponentStatus(components, "redis"));
                status.put("kafka", extractComponentStatus(components, "kafka"));
            } else {
                fillFallbackStatus(status);
            }
        } catch (Exception e) {
            fillFallbackStatus(status);
        }

        return ResponseEntity.ok(new SystemHealth(status));
    }

    /**
     * Extract components map from the health result.
     * Tries getComponents() first (Spring Boot 4.x), then getDetails() (older versions).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractComponents(Object health) {
        // Try getComponents() first (Spring Boot 4.x CompositeHealth)
        try {
            java.lang.reflect.Method getComponentsMethod = health.getClass().getMethod("getComponents");
            Object result = getComponentsMethod.invoke(health);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to getDetails()
        } catch (Exception ignored) {
            // Fall through to getDetails()
        }

        // Try getDetails() (Spring Boot 3.x and earlier)
        try {
            java.lang.reflect.Method getDetailsMethod = health.getClass().getMethod("getDetails");
            Object result = getDetailsMethod.invoke(health);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        } catch (Exception ignored) {
            // Return empty map
        }

        return Map.of();
    }

    /**
     * Extract the status of a single component from the actuator health components via reflection.
     */
    private String extractComponentStatus(Map<String, Object> components, String component) {
        if (!components.containsKey(component)) {
            return "N/A";
        }
        Object componentHealth = components.get(component);
        if (componentHealth == null) {
            return "N/A";
        }
        try {
            java.lang.reflect.Method getStatusMethod = componentHealth.getClass().getMethod("getStatus");
            Object statusObj = getStatusMethod.invoke(componentHealth);
            // Try getCode() first, then getValue() (Spring Boot 4.x)
            String code = null;
            try {
                java.lang.reflect.Method getCodeMethod = statusObj.getClass().getMethod("getCode");
                code = (String) getCodeMethod.invoke(statusObj);
            } catch (NoSuchMethodException e) {
                java.lang.reflect.Method getValueMethod = statusObj.getClass().getMethod("getValue");
                code = (String) getValueMethod.invoke(statusObj);
            }
            return "UP".equals(code) ? "OK" : "Down";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void fillFallbackStatus(Map<String, String> status) {
        status.put("api", "OK");
        status.put("db", "Unknown");
        status.put("redis", "N/A");
        status.put("kafka", "N/A");
    }

    public static class SystemHealth {
        public final Map<String, String> status;

        public SystemHealth(Map<String, String> status) {
            this.status = status;
        }
    }
}
