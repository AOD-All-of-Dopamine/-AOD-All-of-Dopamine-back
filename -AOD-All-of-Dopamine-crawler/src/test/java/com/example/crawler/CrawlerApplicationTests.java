package com.example.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// H2(PostgreSQL 모드)로 실제 DB 없이 컨텍스트 검증. ddl-auto=none: jsonb 컬럼 DDL은 H2가 이해 못 함.
// 별도 application-test.yml을 쓰지 않는 이유: 해당 경로가 .gitignore(L66) 대상.
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:crawler-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=none"
})
class CrawlerApplicationTests {

	@Test
	void contextLoads() {
	}

}


