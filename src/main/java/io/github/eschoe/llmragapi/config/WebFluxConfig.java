package io.github.eschoe.llmragapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableConfigurationProperties
public class WebFluxConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxConfig.class);

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;
    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;
    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;
    @Value("${app.cors.allowed-credentials:true}")
    private boolean allowedCredentials;

    @Bean
    public WebFluxConfigurer webFluxConfigurer() {

        return new WebFluxConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = allowedOrigins.split(",");
                // "*"와 credentials를 함께 사용할 수 없으므로 처리
                if (allowedOrigins.equals("*")) {
                    registry.addMapping("/**")
                            .allowedOriginPatterns("*")
                            .allowedMethods(allowedMethods.split(","))
                            .allowedHeaders(allowedHeaders.split(","))
                            .maxAge(3600);
                } else {
                    registry.addMapping("/**")
                            .allowedOrigins(origins)
                            .allowedMethods(allowedMethods.split(","))
                            .allowedHeaders(allowedHeaders.split(","))
                            .allowCredentials(allowedCredentials)
                            .maxAge(3600);
                }
            }

        };

    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        logger.info("=== CORS WEBSITE FILTER CONFIGURATION ===");
        logger.info("Allowed Origins: {}", allowedOrigins);
        logger.info("Allowed Methods: {}", allowedMethods);
        logger.info("Allowed Headers: {}", allowedHeaders);
        logger.info("Allow Credentials: {}", allowedCredentials);
        
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // "*" 처리
        if (allowedOrigins.equals("*")) {
            corsConfig.addAllowedOriginPattern("*");
            corsConfig.setAllowCredentials(false); // "*" origin과 함께 사용할 수 없음
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

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .responseTimeout(Duration.ofSeconds(8000))
                        .followRedirect(true)
                ))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10*1024*1024))
                .build();
    }

}
