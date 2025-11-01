package io.github.eschoe.llmragapi.domain.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ChatRouter {

    private final ChatHandler handler;

    public ChatRouter(ChatHandler handler) {
        this.handler = handler;
    }

    @Bean
    RouterFunction<ServerResponse> chatRouterFunction() {
        return RouterFunctions.route()
                .POST("/api/chat", handler::chat)
                .OPTIONS("/api/chat", handler::handleOptions)
                .build();
    }

}
