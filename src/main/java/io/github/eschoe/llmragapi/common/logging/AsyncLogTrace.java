package io.github.eschoe.llmragapi.common.logging;

import reactor.core.publisher.Mono;

public interface AsyncLogTrace {

    Mono<LogTraceStatus> begin(String message);
    Mono<Object> end(LogTraceStatus status);
    Mono<Object> exception(LogTraceStatus status, Throwable e);

}
