package io.github.eschoe.llmragapi.domain.document;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class DocumentUploadRouter {
    
    @Bean
    public RouterFunction<ServerResponse> documentRoutes(DocumentUploadHandler handler) {
        return route()
                .POST("/api/documents/upload", 
                      contentType(MediaType.MULTIPART_FORM_DATA), 
                      handler::uploadDocument)
                .GET("/api/documents", handler::getUserDocuments)
                .GET("/api/documents/{documentId}", handler::getDocument)
                .DELETE("/api/documents/{documentId}", handler::deleteDocument)
                .build();
    }
}

