package com.example.AOD;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 스케줄링 기능 활성화
@EntityScan(basePackages = {"com.example.AOD", "com.example.shared.entity"})
@EnableJpaRepositories(basePackages = {"com.example.AOD", "com.example.shared.repository"})
public class AodApplication {

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "false");
		SpringApplication.run(AodApplication.class, args);
	}

}


