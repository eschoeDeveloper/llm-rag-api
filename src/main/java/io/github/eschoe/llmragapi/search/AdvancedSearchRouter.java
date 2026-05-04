package io.github.eschoe.llmragapi.search;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class AdvancedSearchRouter {

    private final AdvancedSearchHandler handler;

    public AdvancedSearchRouter(AdvancedSearchHandler handler) {
        this.handler = handler;
    }

    @Bean
    RouterFunction<ServerResponse> routeAdvancedSearch() {
        return RouterFunctions.route()
                .POST("/api/advanced-search", handler::search)
                .build();
    }

}
