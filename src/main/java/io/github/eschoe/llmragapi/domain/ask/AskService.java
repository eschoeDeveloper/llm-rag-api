package io.github.eschoe.llmragapi.domain.ask;

import reactor.core.publisher.Mono;

public interface AskService {

    Mono<String> askLegacy(AskBody ask);

    Mono<AskResponse> askEnhanced(AskRequest ask);

}
