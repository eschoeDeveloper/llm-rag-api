package io.github.eschoe.llmragapi.domain.ask;

import reactor.core.publisher.Mono;

public interface AskService {

    Mono<String> ask(AskBody ask);

}
