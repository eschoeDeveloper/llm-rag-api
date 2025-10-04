package io.github.eschoe.llmragapi.domain.llm;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

public interface LlmCacheService {

    Mono<String> getOrBuildPrompt(String userId, String ctxHash, Supplier<Mono<String>> builder);
    Mono<String> getOrInvoke(String model, String inputHash, Supplier<Mono<String>> invoker);

}
