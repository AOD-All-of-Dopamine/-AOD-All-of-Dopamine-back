package com.example.AOD.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * {@code @EnableJpaRepositories}·{@code @EntityScan} 을 {@link com.example.AOD.AodApplication}(유일한
 * {@code @SpringBootConfiguration}) 에서 분리한 설정.
 *
 * 이유(Task 9 편차, ReactionControllerTest): {@code @WebMvcTest} 는 컨텍스트를 만들 때 이 모듈의 유일한
 * {@code @SpringBootConfiguration} 클래스({@code AodApplication})를 그대로 쓴다. 그 클래스에 직접 붙은
 * 애너테이션(이 둘)은 슬라이스의 {@code TypeExcludeFilter} 를 타지 않고 그대로 살아남는다 — 그래서
 * {@code ContentRepositoryImpl}(도메인 {@code @PersistenceContext EntityManager} 필드를 쓰는 커스텀
 * 레포지토리 구현체) 을 포함해 16개 레포지토리 전부가 슬라이스에서도 즉시(eager) 초기화를 시도하고,
 * JPA 인프라(entityManagerFactory 등)가 없는 슬라이스라 컨텍스트 로딩이 깨진다.
 * 이 애너테이션들을 평범한 {@code @Configuration} 빈으로 옮기면(패키지는 그대로 스캔되므로 런타임 동작은
 * 동일) {@code @WebMvcTest} 의 기본 필터가 이 빈을 제외해준다 — {@link com.example.AOD.security.SecurityConfig}
 * 를 슬라이스에 넣으려면 {@code @Import} 로 명시해야 하는 것과 같은 이유(반대 방향)다.
 */
@Configuration
@EntityScan(basePackages = {"com.example.AOD", "com.example.shared.entity"})
@EnableJpaRepositories(basePackages = {"com.example.AOD", "com.example.shared.repository"})
public class JpaRepositoriesConfig {
}
