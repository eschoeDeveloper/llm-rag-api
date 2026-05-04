package io.github.eschoe.llmragapi.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class AdminRouter {

    private final AdminHandler handler;

    public AdminRouter(AdminHandler handler) {
        this.handler = handler;
    }

    @Bean
    RouterFunction<ServerResponse> adminRoutes() {
        return RouterFunctions.route()
                .GET("/api/admin/vision-usage", handler::getVisionUsage)
                .POST("/api/admin/cache/invalidate", handler::invalidateCache)
                .build();
    }
}
