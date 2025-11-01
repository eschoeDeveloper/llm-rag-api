package io.github.eschoe.llmragapi.domain.ask;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class AskRouter {

    private final AskHandler handler;

    AskRouter(AskHandler handler) {
        this.handler = handler;
    }

    @Bean
    RouterFunction<ServerResponse> askRouterFunction() {
        return RouterFunctions.route()
                .POST("/api/ask", handler::ask)
                .OPTIONS("/api/ask", handler::handleOptions)
                .build();
    }

}
