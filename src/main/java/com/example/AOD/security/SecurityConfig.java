package com.example.AOD.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS 설정 활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF 완전 비활성화 (개발용이라면 이 상태로도 동작하지만, H2 콘솔만 예외로 두는 경우라면 csrf().disable() 대신 #3 참고)
                .csrf(csrf -> csrf.disable())

                // 세션을 사용하지 않도록 설정 (Stateless JWT)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        // 1) H2 콘솔 URL 예외 처리
                        .requestMatchers("/h2-console/**").permitAll()

                        // 2) 인증 없이 열어둘 API 엔드포인트
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/**").permitAll()

                        // 3) 나머지 요청은 모두 허용(필요에 따라 authenticated() 등으로 변경)
                        .anyRequest().permitAll()
                )

                // 4) H2 콘솔이 iframe으로 동작하므로 sameOrigin으로 설정
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )

        // 5) JWT 필터가 있다면 추가
        ;

        if (jwtAuthenticationFilter != null) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
