package io.github.eschoe.reactive_chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())  // ★ CorsWebFilter와 연동
                .authorizeExchange(ex -> ex
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll() // ★ preflight 허용
                        .anyExchange().permitAll()
                )
                .build();
    }

}
