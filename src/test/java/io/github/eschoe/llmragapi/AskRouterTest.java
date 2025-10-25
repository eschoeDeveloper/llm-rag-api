package io.github.eschoe.llmragapi;

import io.github.eschoe.llmragapi.domain.ask.AskBody;
import io.github.eschoe.llmragapi.domain.ask.AskRequest;
import io.github.eschoe.llmragapi.domain.ask.AskResponse;
import io.github.eschoe.llmragapi.domain.rag.RAGConfig;
import io.github.eschoe.llmragapi.global.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.main.web-application-type=reactive",
        "spring.config.import=classpath:application-test.yaml"
})
class AskRouterTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(context).configureClient().responseTimeout(Duration.ofSeconds(30)).build();
    }

    @Test
    void testLegacyAskEndpoint() {
        AskBody request = new AskBody("What is artificial intelligence?", "", "");

        webTestClient.post()
                .uri("/api/ask")
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
    void testEnhancedAskEndpoint() {
        RAGConfig config = new RAGConfig();
        config.setTopK(10);
        config.setThreshold(0.7);
        config.setTemperature(0.7);

        AskRequest request = new AskRequest("What is artificial intelligence?", config, "test-session");

        webTestClient.post()
                .uri("/api/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(AskResponse.class)
                .consumeWith(result -> {
                    AskResponse response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response.getContent()).isNotEmpty();
                    assertThat(response.getModel()).isNotEmpty();
                    assertThat(response.getMetadata()).containsKey("processingTime");
                });
    }

    @Test
    void testInvalidRequest() {
        String invalidRequest = "invalid json";

        webTestClient.post()
                .uri("/api/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ErrorResponse.class);
    }
}