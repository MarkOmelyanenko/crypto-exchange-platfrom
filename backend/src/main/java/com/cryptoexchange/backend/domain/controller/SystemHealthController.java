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

                java.lang.reflect.Method getDetailsMethod = health.getClass().getMethod("getDetails");
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) getDetailsMethod.invoke(health);

                status.put("api", "OK");
                status.put("db", extractComponentStatus(details, "db"));
                status.put("redis", extractComponentStatus(details, "redis"));
                status.put("kafka", extractComponentStatus(details, "kafka"));
            } else {
                fillFallbackStatus(status);
            }
        } catch (Exception e) {
            fillFallbackStatus(status);
        }

        return ResponseEntity.ok(new SystemHealth(status));
    }

    /**
     * Extract the status of a single component from the actuator health details via reflection.
     */
    private String extractComponentStatus(Map<String, Object> details, String component) {
        if (!details.containsKey(component)) {
            return "N/A";
        }
        Object componentHealth = details.get(component);
        if (componentHealth == null) {
            return "N/A";
        }
        try {
            java.lang.reflect.Method getStatusMethod = componentHealth.getClass().getMethod("getStatus");
            Object statusObj = getStatusMethod.invoke(componentHealth);
            java.lang.reflect.Method getCodeMethod = statusObj.getClass().getMethod("getCode");
            String code = (String) getCodeMethod.invoke(statusObj);
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
