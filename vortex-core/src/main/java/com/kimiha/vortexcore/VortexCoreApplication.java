package com.kimiha.vortexcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VortexCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(VortexCoreApplication.class, args);
	}

}
