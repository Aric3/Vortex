package com.kimiha.vortexcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@ConfigurationProperties(prefix = "analytics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsProperties {
    private boolean enabled = true;
    private long refreshIntervalMs = 1000;
}
