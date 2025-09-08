package io.github.eschoe.llmragapi.domain.chat;

import reactor.core.publisher.Mono;

public interface ChatService {
    Mono<String> chatLegacy(ChatBody chatBody);
    Mono<ChatResponse> chatEnhanced(ChatRequest request);
}
