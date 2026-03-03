package com.kimiha.vortexcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.kimiha.vortexcore.config.AnalyticsProperties;
import com.kimiha.vortexcore.alltick.AllTickProperties;
import com.kimiha.vortexcore.alltick.SimulationProperties;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({ AnalyticsProperties.class, AllTickProperties.class, SimulationProperties.class })
public class VortexCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(VortexCoreApplication.class, args);
	}

}
