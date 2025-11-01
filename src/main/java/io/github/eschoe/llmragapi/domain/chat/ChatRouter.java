package io.github.eschoe.llmragapi.domain.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ChatRouter {

    private static final Logger logger = LoggerFactory.getLogger(ChatRouter.class);
    private final ChatHandler handler;

    public ChatRouter(ChatHandler handler) {
        this.handler = handler;
        logger.info("ChatRouter initialized");
    }

    @Bean
    RouterFunction<ServerResponse> chatRouterFunction() {
        logger.info("=== REGISTERING CHAT ROUTER ===");
        logger.info("Route: POST /api/chat");
        RouterFunction<ServerResponse> router = RouterFunctions.route()
                .POST("/api/chat", handler::chat)
                .build();
        logger.info("Chat router function registered successfully");
        return router;
    }

}
