package io.github.eschoe.llmragapi.domain.conversation;

import io.github.eschoe.llmragapi.global.DetailedErrorResponse;
import io.github.eschoe.llmragapi.util.SessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class ConversationThreadHandler {

    private final ConversationThreadService threadService;
    private final ObjectMapper objectMapper;
    private final SessionUtil sessionUtil;

    public ConversationThreadHandler(ConversationThreadService threadService, 
                                   ObjectMapper objectMapper, 
                                   SessionUtil sessionUtil) {
        this.threadService = threadService;
        this.objectMapper = objectMapper;
        this.sessionUtil = sessionUtil;
    }

    public Mono<ServerResponse> createThread(ServerRequest request) {
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    final String sessionId = sessionUtil.extractSessionId(request);
                    
                    System.out.println("[ConversationThreadHandler] Received body: " + body);
                    System.out.println("[ConversationThreadHandler] Session ID: " + sessionId);
                    
                    try {
                        CreateThreadRequest createRequest = objectMapper.readValue(body, CreateThreadRequest.class);
                        System.out.println("[ConversationThreadHandler] Parsed request - title: " + createRequest.getTitle() + ", description: " + createRequest.getDescription());
                        
                        return threadService.createThread(sessionId, createRequest.getTitle())
                                .flatMap(thread -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Session-ID", sessionId)
                                        .bodyValue(thread));
                    } catch (Exception e) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "INVALID_THREAD_REQUEST",
                                        "스레드 생성 요청 형식이 올바르지 않습니다.",
                                        e.getMessage(),
                                        sessionId
                                ));
                    }
                });
    }

    public Mono<ServerResponse> getThread(ServerRequest request) {
        String threadId = request.pathVariable("threadId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return threadService.getThread(threadId)
                .flatMap(thread -> {
                    if (thread == null) {
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "THREAD_NOT_FOUND",
                                        "대화 스레드를 찾을 수 없습니다.",
                                        "Thread ID: " + threadId,
                                        sessionId
                                ));
                    }
                    
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-ID", sessionId)
                            .bodyValue(thread);
                });
    }

    public Mono<ServerResponse> getUserThreads(ServerRequest request) {
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return threadService.getUserThreads(sessionId)
                .collectList()
                .flatMap(threads -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(threads));
    }

    public Mono<ServerResponse> addMessage(ServerRequest request) {
        String threadId = request.pathVariable("threadId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        AddMessageRequest addRequest = objectMapper.readValue(body, AddMessageRequest.class);
                        
                        return threadService.addMessage(threadId, addRequest.getContent(), addRequest.getRole())
                                .flatMap(thread -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Session-ID", sessionId)
                                        .bodyValue(thread));
                    } catch (Exception e) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "INVALID_MESSAGE_REQUEST",
                                        "메시지 추가 요청 형식이 올바르지 않습니다.",
                                        e.getMessage(),
                                        sessionId
                                ));
                    }
                });
    }

    public Mono<ServerResponse> updateThreadTitle(ServerRequest request) {
        String threadId = request.pathVariable("threadId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        UpdateTitleRequest updateRequest = objectMapper.readValue(body, UpdateTitleRequest.class);
                        
                        return threadService.updateThreadTitle(threadId, updateRequest.getTitle())
                                .flatMap(thread -> ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Session-ID", sessionId)
                                        .bodyValue(thread));
                    } catch (Exception e) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "INVALID_UPDATE_REQUEST",
                                        "제목 업데이트 요청 형식이 올바르지 않습니다.",
                                        e.getMessage(),
                                        sessionId
                                ));
                    }
                });
    }

    public Mono<ServerResponse> archiveThread(ServerRequest request) {
        String threadId = request.pathVariable("threadId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return threadService.archiveThread(threadId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue("Thread archived successfully"));
    }

    public Mono<ServerResponse> deleteThread(ServerRequest request) {
        String threadId = request.pathVariable("threadId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return threadService.deleteThread(threadId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue("Thread deleted successfully"))
                .onErrorResume(IllegalArgumentException.class, error -> 
                    ServerResponse.status(404)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new DetailedErrorResponse(
                                    "THREAD_NOT_FOUND",
                                    "대화 스레드를 찾을 수 없습니다.",
                                    "Thread ID: " + threadId,
                                    sessionId
                            )))
                .onErrorResume(RuntimeException.class, error -> 
                    ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(new DetailedErrorResponse(
                                    "REDIS_CONNECTION_ERROR",
                                    "Redis 연결에 실패했습니다.",
                                    error.getMessage(),
                                    sessionId
                            )));
    }

    // Request DTOs
    public static class CreateThreadRequest {
        private String title;
        private String description;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class AddMessageRequest {
        private String content;
        private ConversationThread.Message.MessageRole role;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public ConversationThread.Message.MessageRole getRole() { return role; }
        public void setRole(ConversationThread.Message.MessageRole role) { this.role = role; }
    }

    public static class UpdateTitleRequest {
        private String title;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
