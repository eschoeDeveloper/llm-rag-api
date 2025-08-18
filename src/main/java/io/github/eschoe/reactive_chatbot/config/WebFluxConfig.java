package io.github.eschoe.reactive_chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties
public class WebFluxConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;
    @Value("${app.cors.allowed-methods}")
    private String allowedMethods;
    @Value("${app.cors.allowed-headers}")
    private String allowedHeaders;
    @Value("${app.cors.allowed-credentials}")
    private boolean allowedCredentials;

    @Bean
    public WebFluxConfigurer webFluxConfigurer() {

        return new WebFluxConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins.split(","))
                        .allowedMethods(allowedMethods.split(","))
                        .allowedHeaders(allowedHeaders.split(","))
                        .allowCredentials(allowedCredentials);

            }

        };

    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .responseTimeout(Duration.ofSeconds(30))
                        .followRedirect(true)
                ))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10*1024*1024))
                .build();
    }

}
