package com.kimiha.vortexcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.kimiha.vortexcore.config.AnalyticsProperties;
import com.kimiha.vortexcore.config.QuotationProperties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({ AnalyticsProperties.class, QuotationProperties.class })
public class VortexCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(VortexCoreApplication.class, args);
	}

}
