package io.github.eschoe.llmragapi.domain.conversation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ConversationThreadRouter {

    @Bean
    public RouterFunction<ServerResponse> conversationThreadRoutes(ConversationThreadHandler handler) {
        return route()
                // 스레드 생성
                .POST("/api/threads", handler::createThread)
                // 사용자 스레드 목록 조회
                .GET("/api/threads", handler::getUserThreads)
                // 특정 스레드 조회
                .GET("/api/threads/{threadId}", handler::getThread)
                // 스레드에 메시지 추가
                .POST("/api/threads/{threadId}/messages", handler::addMessage)
                // 스레드 제목 업데이트
                .PUT("/api/threads/{threadId}/title", handler::updateThreadTitle)
                // 스레드 아카이브
                .POST("/api/threads/{threadId}/archive", handler::archiveThread)
                // 스레드 삭제
                .DELETE("/api/threads/{threadId}", handler::deleteThread)
                .build();
    }
}
