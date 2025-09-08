package io.github.eschoe.llmragapi;


import io.github.eschoe.llmragapi.domain.chat.ChatBody;
import io.github.eschoe.llmragapi.domain.chat.ChatRequest;
import io.github.eschoe.llmragapi.domain.chat.ChatResponse;
import io.github.eschoe.llmragapi.domain.rag.RAGConfig;
import io.github.eschoe.llmragapi.domain.search.SearchResult;
import io.github.eschoe.llmragapi.global.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.main.web-application-type=reactive"
})
class ChatRouterTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(context)
                .configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    void testLegacyChatEndpoint() {
        ChatBody request = new ChatBody("What is machine learning?", 0, null, "", null);

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.TEXT_PLAIN)
                .expectBody(String.class)
                .consumeWith(result -> {
                    String response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response).isNotEmpty();
                });
    }

    @Test
    void testEnhancedChatEndpoint() {
        RAGConfig config = new RAGConfig();
        config.setTopK(5);
        config.setThreshold(0.7);
        config.setTemperature(0.7);

        ChatRequest request = new ChatRequest("What is machine learning?", null, config);

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ChatResponse.class)
                .consumeWith(result -> {
                    ChatResponse response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).isNotEmpty();
                    assertThat(response.getModel()).isNotEmpty();
                    assertThat(response.getMetadata()).containsKey("processingTime");
                    assertThat(response.getMetadata()).containsKey("searchResults");
                    assertThat(response.getMetadata()).containsKey("averageScore");
                });
    }

    @Test
    void testEnhancedChatWithSearchResults() {
        RAGConfig config = new RAGConfig();
        config.setTopK(3);
        config.setThreshold(0.8);
        config.setTemperature(0.5);

        // 미리 검색된 결과를 포함한 요청
        SearchResult searchResult1 = new SearchResult("1", "Machine learning is a subset of AI", 0.95, Map.of("title", "ML Basics"), "doc1");
        SearchResult searchResult2 = new SearchResult("2", "Deep learning uses neural networks", 0.88, Map.of("title", "DL Guide"), "doc2");

        List<SearchResult> searchResults = List.of(searchResult1, searchResult2);

        ChatRequest request = new ChatRequest("What is machine learning?", searchResults, config);

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ChatResponse.class)
                .consumeWith(result -> {
                    ChatResponse response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).isNotEmpty();
                    assertThat(response.getMetadata()).containsKey("searchResults");
                    assertThat(response.getMetadata().get("searchResults")).isEqualTo(2);
                });
    }

    @Test
    void testInvalidChatRequest() {
        String invalidRequest = "invalid json";

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ErrorResponse.class);
    }

    @Test
    void testEmptyQuery() {
        RAGConfig config = new RAGConfig();
        ChatRequest request = new ChatRequest("", null, config);

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ErrorResponse.class);
    }
}
