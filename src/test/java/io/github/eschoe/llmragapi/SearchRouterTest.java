package io.github.eschoe.llmragapi;

import io.github.eschoe.llmragapi.domain.search.SearchBody;
import io.github.eschoe.llmragapi.domain.search.SearchRequest;
import io.github.eschoe.llmragapi.domain.search.SearchResponse;
import io.github.eschoe.llmragapi.domain.search.TopKCosine;
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
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.main.web-application-type=reactive",
        "spring.config.import=classpath:application-test.yaml"
})
class SearchRouterTest {

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
    void testLegacySearchEndpoint() {
        // 테스트용 임베딩 벡터 (실제로는 더 긴 벡터여야 함)
        float[] testEmbedding = new float[1536]; // text-embedding-3-small 차원
        for (int i = 0; i < testEmbedding.length; i++) {
            testEmbedding[i] = (float) (Math.random() * 2 - 1); // -1 ~ 1 범위
        }

        SearchBody request = new SearchBody(testEmbedding, 5);

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(TopKCosine.class)
                .consumeWith(result -> {
                    List<TopKCosine> response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response).isNotEmpty();
                    response.forEach(item -> {
                        assertThat(item.id()).isNotNull();
                        assertThat(item.content()).isNotEmpty();
                        assertThat(item.score()).isNotNull();
                    });
                });
    }

    @Test
    void testEnhancedSearchEndpoint() {
        SearchRequest request = new SearchRequest("machine learning algorithms", 5, 0.7);

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(SearchResponse.class)
                .consumeWith(result -> {
                    SearchResponse response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response.getResults()).isNotNull();
                    assertThat(response.getTotalCount()).isGreaterThanOrEqualTo(0);
                    assertThat(response.getAverageScore()).isBetween(0.0, 1.0);
                    assertThat(response.getMetadata()).containsKey("processingTime");
                    assertThat(response.getMetadata()).containsKey("query");
                    assertThat(response.getMetadata()).containsKey("topK");
                    assertThat(response.getMetadata()).containsKey("threshold");
                });
    }

    @Test
    void testEnhancedSearchWithHighThreshold() {
        SearchRequest request = new SearchRequest("artificial intelligence", 10, 0.9);

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(SearchResponse.class)
                .consumeWith(result -> {
                    SearchResponse response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    // 높은 임계값으로 인해 결과가 적을 수 있음
                    assertThat(response.getTotalCount()).isGreaterThanOrEqualTo(0);
                    if (response.getTotalCount() > 0) {
                        assertThat(response.getAverageScore()).isGreaterThanOrEqualTo(0.9);
                    }
                });
    }

    @Test
    void testInvalidSearchRequest() {
        String invalidRequest = "invalid json";

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ErrorResponse.class);
    }

    @Test
    void testEmptyQuery() {
        SearchRequest request = new SearchRequest("", 5, 0.7);

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ErrorResponse.class);
    }

    @Test
    void testLegacySearchWithEmptyEmbedding() {
        SearchBody request = new SearchBody(null, 5);

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(ErrorResponse.class);
    }

    @Test
    void testSearchWithZeroTopK() {
        SearchRequest request = new SearchRequest("test query", 0, 0.7);

        webTestClient.post()
                .uri("/api/embeddings/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(SearchResponse.class)
                .consumeWith(result -> {
                    SearchResponse response = result.getResponseBody();
                    assertThat(response).isNotNull();
                    assertThat(response.getTotalCount()).isEqualTo(0);
                });
    }
}
