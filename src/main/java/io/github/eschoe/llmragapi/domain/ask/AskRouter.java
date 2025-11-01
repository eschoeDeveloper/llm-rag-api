package io.github.eschoe.llmragapi.domain.ask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class AskRouter {

    private static final Logger logger = LoggerFactory.getLogger(AskRouter.class);
    private final AskHandler handler;

    AskRouter(AskHandler handler) {
        this.handler = handler;
        logger.info("AskRouter initialized");
    }

    @Bean
    RouterFunction<ServerResponse> askRouterFunction() {
        logger.info("=== REGISTERING ASK ROUTER ===");
        logger.info("Route: POST /api/ask");
        RouterFunction<ServerResponse> router = RouterFunctions.route()
                .POST("/api/ask", handler::ask)
                .build();
        logger.info("Ask router function registered successfully");
        return router;
    }

}
