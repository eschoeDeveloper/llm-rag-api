package io.github.eschoe.llmragapi.domain.history;

import io.github.eschoe.llmragapi.global.ErrorResponse;
import io.github.eschoe.llmragapi.util.SessionUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * 대화 히스토리 관리를 위한 핸들러
 */
@Component
public class HistoryHandler {

    private final ChatHistoryStore chatHistoryStore;
    private final SessionUtil sessionUtil;

    public HistoryHandler(ChatHistoryStore chatHistoryStore, SessionUtil sessionUtil) {
        this.chatHistoryStore = chatHistoryStore;
        this.sessionUtil = sessionUtil;
    }

    /**
     * 대화 히스토리를 조회합니다.
     */
    public Mono<ServerResponse> getHistory(ServerRequest request) {
        String sessionId = sessionUtil.extractSessionId(request);
        
        return chatHistoryStore.getFormattedHistory(sessionId, 20)
                .flatMap(history -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(Map.of(
                                "sessionId", sessionId,
                                "history", history,
                                "timestamp", Instant.now()
                        )))
                .onErrorResume(e -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ErrorResponse("히스토리 조회 실패: " + e.getMessage(), Instant.now())));
    }

    /**
     * 대화 히스토리를 삭제합니다.
     */
    public Mono<ServerResponse> clearHistory(ServerRequest request) {
        String sessionId = sessionUtil.extractSessionId(request);
        
        return chatHistoryStore.clearHistory(sessionId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(Map.of(
                                "sessionId", sessionId,
                                "message", "대화 히스토리가 삭제되었습니다.",
                                "timestamp", Instant.now()
                        )))
                .onErrorResume(e -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ErrorResponse("히스토리 삭제 실패: " + e.getMessage(), Instant.now())));
    }

    /**
     * 대화 히스토리 개수를 조회합니다.
     */
    public Mono<ServerResponse> getHistoryCount(ServerRequest request) {
        String sessionId = sessionUtil.extractSessionId(request);
        
        return chatHistoryStore.getHistoryCount(sessionId)
                .flatMap(count -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(Map.of(
                                "sessionId", sessionId,
                                "count", count,
                                "timestamp", Instant.now()
                        )))
                .onErrorResume(e -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ErrorResponse("히스토리 개수 조회 실패: " + e.getMessage(), Instant.now())));
    }
}
