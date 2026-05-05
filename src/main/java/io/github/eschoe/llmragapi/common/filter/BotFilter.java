package io.github.eschoe.llmragapi.common.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 알려진 AI 학습/스크랩 크롤러 User-Agent 차단.
 *
 * robots.txt 를 무시하는 봇 대응 — Spring WebFlux WebFilter 로 진입 즉시 403.
 * Filter 우선순위 최상위 (Ordered.HIGHEST_PRECEDENCE) — CORS·인증보다 먼저 컷.
 *
 * 매칭 방식: User-Agent 문자열에 패턴이 부분 포함되면 차단 (case-insensitive).
 * 정확 매칭이 아닌 부분 매칭 — 봇이 UA 끝에 "...GPTBot/1.2" 같은 형태로 박는 경우 다 잡힘.
 *
 * 명단 출처: 각 회사 공식 발표 (OpenAI, Anthropic, Common Crawl, Meta 등).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BotFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(BotFilter.class);

    private static final Set<String> BLOCKED_UA_PATTERNS = Set.of(
            // OpenAI
            "GPTBot", "ChatGPT-User", "OAI-SearchBot",
            // Anthropic
            "ClaudeBot", "Claude-Web", "anthropic-ai",
            // 기타 LLM 회사
            "cohere-ai", "PerplexityBot", "Perplexity-User", "MistralAI-User",
            // Big Tech 학습 봇
            "Google-Extended", "GoogleOther",
            "FacebookBot", "Meta-ExternalAgent", "Meta-ExternalFetcher",
            "Applebot-Extended", "Amazonbot", "Bytespider",
            // Common Crawl 계열
            "CCBot",
            // 기타 학습/스크래퍼
            "AI2Bot", "Diffbot", "Omgilibot", "ImagesiftBot",
            "YouBot", "DuckAssistBot", "Timpibot", "Webzio-Extended"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ua = exchange.getRequest().getHeaders().getFirst("User-Agent");
        if (ua != null && !ua.isEmpty()) {
            String lower = ua.toLowerCase();
            for (String pattern : BLOCKED_UA_PATTERNS) {
                if (lower.contains(pattern.toLowerCase())) {
                    log.info("[bot-filter] blocked UA={} path={}", ua, exchange.getRequest().getPath());
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }
        }
        return chain.filter(exchange);
    }
}
