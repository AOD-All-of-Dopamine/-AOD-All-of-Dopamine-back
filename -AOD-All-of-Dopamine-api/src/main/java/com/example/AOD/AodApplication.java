package com.example.AOD;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EntityScan · @EnableJpaRepositories 는 com.example.AOD.config.JpaRepositoriesConfig 로 옮겼다
// (@WebMvcTest 슬라이스가 이 클래스에 직접 붙은 애너테이션은 걸러내지 못해 레포지토리를 즉시 초기화하려던
// 문제 — 상세 사유는 JpaRepositoriesConfig 주석 참고). 스캔 패키지·런타임 동작은 그대로다.
@SpringBootApplication
@EnableScheduling // 스케줄링 기능 활성화
@EnableCaching // 캐시 기능 활성화 (장르 집계 등 무거운 조회 캐싱)
public class AodApplication {

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "false");
		SpringApplication.run(AodApplication.class, args);
	}

}


