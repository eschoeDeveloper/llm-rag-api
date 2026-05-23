package io.github.eschoe.llmragapi.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * CORS — 단일 책임.
 *
 * CorsWebFilter 만 등록 (이전엔 WebFluxConfigurer.addCorsMappings 와 중복되어 preflight 헤더 충돌 위험 있어 제거).
 *
 * "*" + credentials 동시 사용 불가 (CORS spec) — 분기 처리해 둘 다 안전하게.
 * X-Session-ID 는 exposed-header 로 명시 — 클라이언트가 응답 헤더에서 읽을 수 있게.
 */
@Configuration
public class CorsConfig {

    private static final Logger logger = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;
    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;
    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;
    @Value("${app.cors.allowed-credentials:true}")
    private boolean allowedCredentials;

    @Bean
    public CorsWebFilter corsWebFilter() {
        logger.info("=== CORS WEBSITE FILTER CONFIGURATION ===");
        logger.info("Allowed Origins: {}", allowedOrigins);
        logger.info("Allowed Methods: {}", allowedMethods);
        logger.info("Allowed Headers: {}", allowedHeaders);
        logger.info("Allow Credentials: {}", allowedCredentials);

        CorsConfiguration corsConfig = new CorsConfiguration();

        if ("*".equals(allowedOrigins)) {
            // "*" + credentials 동시 사용 불가 → originPattern 으로 우회 + credentials false
            corsConfig.addAllowedOriginPattern("*");
            corsConfig.setAllowCredentials(false);
            logger.info("Using allowedOriginPattern: *");
        } else {
            corsConfig.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
            corsConfig.setAllowCredentials(allowedCredentials);
            logger.info("Using allowedOrigins: {}", Arrays.asList(allowedOrigins.split(",")));
        }

        corsConfig.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        corsConfig.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        corsConfig.addExposedHeader("X-Session-ID");
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        logger.info("CorsWebFilter bean created successfully");
        return new CorsWebFilter(source);
    }
}
