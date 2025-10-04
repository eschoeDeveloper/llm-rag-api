package io.github.eschoe.llmragapi.domain.history;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class HistoryRouter {

    private final HistoryHandler handler;

    public HistoryRouter(HistoryHandler handler) {
        this.handler = handler;
    }

    @Bean
    RouterFunction<ServerResponse> historyRouterFunction() {
        return RouterFunctions.route()
                .GET("/api/history", handler::getHistory)
                .DELETE("/api/history", handler::clearHistory)
                .GET("/api/history/count", handler::getHistoryCount)
                .build();
    }
}
