package com.cryptoexchange.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Map<String, Policy> policies = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, Policy> policies) {
        this.policies = policies;
    }

    public static class Policy {
        private int limit;
        private String window;
        private String keyBy = "ip"; // "user" or "ip"

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public String getWindow() {
            return window;
        }

        public void setWindow(String window) {
            this.window = window;
        }

        public String getKeyBy() {
            return keyBy;
        }

        public void setKeyBy(String keyBy) {
            this.keyBy = keyBy;
        }

        public Duration getWindowDuration() {
            return parseDuration(window);
        }

        private Duration parseDuration(String durationStr) {
            if (durationStr == null || durationStr.isEmpty()) {
                return Duration.ofSeconds(60);
            }
            durationStr = durationStr.trim().toLowerCase();
            if (durationStr.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(durationStr.substring(0, durationStr.length() - 1)));
            } else if (durationStr.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(durationStr.substring(0, durationStr.length() - 1)));
            } else if (durationStr.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(durationStr.substring(0, durationStr.length() - 1)));
            } else {
                return Duration.ofSeconds(Long.parseLong(durationStr));
            }
        }
    }
}
