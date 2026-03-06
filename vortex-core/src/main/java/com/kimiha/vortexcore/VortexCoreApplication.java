package com.kimiha.vortexcore;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(VortexCoreApplication.class);

	public static void main(String[] args) {
		log.info("VortexCoreApplication starting...");
		SpringApplication.run(VortexCoreApplication.class, args);
	}

	@PreDestroy
	public void onShutdown() {
		log.info("VortexCoreApplication stopping...");
	}

}
