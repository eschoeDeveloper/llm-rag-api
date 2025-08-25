package io.github.eschoe.llmragapi.domain.chat;

import reactor.core.publisher.Mono;

public interface ChatService {
    Mono<String> chat(ChatBody chatBody);
}
