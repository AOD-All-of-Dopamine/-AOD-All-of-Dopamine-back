package com.example.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AOD 크롤러 서버
 * - 크롤링: Steam, TMDB, Naver 웹툰/웹소설 등
 * - Transform: RawItem -> Content 변환
 * - 스케줄링: 정기적 데이터 수집 및 변환
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.example.crawler", "com.example.shared.entity"})
@EnableJpaRepositories(basePackages = {"com.example.crawler", "com.example.shared.repository"})
public class CrawlerApplication {

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "false");  // Selenium용
		SpringApplication.run(CrawlerApplication.class, args);
	}
}


