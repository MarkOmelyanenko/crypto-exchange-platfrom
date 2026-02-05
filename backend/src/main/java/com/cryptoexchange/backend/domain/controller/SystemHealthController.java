package com.cryptoexchange.backend.domain.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System health endpoints")
public class SystemHealthController {

    @Autowired
    private ApplicationContext applicationContext; // Use Object to avoid compile-time dependency

    @GetMapping("/health")
    @Operation(summary = "Get system health", description = "Returns simplified system health status")
    public ResponseEntity<SystemHealth> getHealth() {
        Map<String, String> status = new HashMap<>();

        try {
            // Try to get healthEndpoint bean by name
            Object healthEndpoint = applicationContext.getBean("healthEndpoint");
            
            if (healthEndpoint != null) {
                // Use reflection to call health endpoint methods
                java.lang.reflect.Method healthMethod = healthEndpoint.getClass().getMethod("health");
                Object health = healthMethod.invoke(healthEndpoint);
                
                java.lang.reflect.Method getDetailsMethod = health.getClass().getMethod("getDetails");
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) getDetailsMethod.invoke(health);
                
                // Extract component statuses
                status.put("api", "OK");
                
                if (details.containsKey("db")) {
                    Object dbHealthObj = details.get("db");
                    if (dbHealthObj != null) {
                        try {
                            java.lang.reflect.Method getStatusMethod = dbHealthObj.getClass().getMethod("getStatus");
                            Object dbStatus = getStatusMethod.invoke(dbHealthObj);
                            java.lang.reflect.Method getCodeMethod = dbStatus.getClass().getMethod("getCode");
                            String code = (String) getCodeMethod.invoke(dbStatus);
                            status.put("db", "UP".equals(code) ? "OK" : "Down");
                        } catch (Exception e) {
                            status.put("db", "Unknown");
                        }
                    } else {
                        status.put("db", "Unknown");
                    }
                } else {
                    status.put("db", "Unknown");
                }
                
                if (details.containsKey("kafka")) {
                    Object kafkaHealthObj = details.get("kafka");
                    if (kafkaHealthObj != null) {
                        try {
                            java.lang.reflect.Method getStatusMethod = kafkaHealthObj.getClass().getMethod("getStatus");
                            Object kafkaStatus = getStatusMethod.invoke(kafkaHealthObj);
                            java.lang.reflect.Method getCodeMethod = kafkaStatus.getClass().getMethod("getCode");
                            String code = (String) getCodeMethod.invoke(kafkaStatus);
                            status.put("kafka", "UP".equals(code) ? "OK" : "Down");
                        } catch (Exception e) {
                            status.put("kafka", "N/A");
                        }
                    } else {
                        status.put("kafka", "N/A");
                    }
                } else {
                    status.put("kafka", "N/A");
                }
            } else {
                // Fallback if actuator is not available
                status.put("api", "OK");
                status.put("db", "Unknown");
                status.put("kafka", "N/A");
            }
        } catch (Exception e) {
            // If health check fails, return basic status
            status.put("api", "OK");
            status.put("db", "Unknown");
            status.put("kafka", "N/A");
        }

        return ResponseEntity.ok(new SystemHealth(status));
    }

    public static class SystemHealth {
        public final Map<String, String> status;

        public SystemHealth(Map<String, String> status) {
            this.status = status;
        }
    }
}
