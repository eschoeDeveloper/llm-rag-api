package io.github.eschoe.llmragapi.domain.search;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class SearchRouter {

    private final SearchHandler handler;

    public SearchRouter(SearchHandler handler) {
        this.handler = handler;
    }

    @Bean
    RouterFunction<ServerResponse> searchRouterFunction() {
        return RouterFunctions.route()
                .POST("/api/embeddings/search", handler::search)
                .build();
    }

}
