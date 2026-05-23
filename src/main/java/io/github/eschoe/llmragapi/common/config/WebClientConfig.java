package io.github.eschoe.llmragapi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 외부 HTTP 호출용 WebClient — 단일 책임.
 *
 * LLM/Cohere 등 외부 API 호출 시 모든 컴포넌트가 이 빈을 주입 받아 사용.
 *
 * 핵심 설정:
 *  - responseTimeout 60s — LLM long-tail (gpt-4o 30~50초 응답 가능) 커버.
 *    이전 값 8000초(=2시간) 는 오타였고 외부 API 응답 안 올 때 connection 영구 점유 위험.
 *  - maxInMemorySize 10MB — Tika 추출 텍스트, 큰 임베딩 응답 등 대비.
 *  - followRedirect — OpenAI/Anthropic API 가 reverse proxy 거쳐 redirect 가능.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .responseTimeout(Duration.ofSeconds(60))
                        .followRedirect(true)
                ))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
