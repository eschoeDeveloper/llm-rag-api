package io.github.eschoe.llmragapi.llm;

import io.github.eschoe.llmragapi.rag.config.EmbeddingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class LlmClient {

    @Value("${spring.ai.openai.api-key}")
    private String openaiKey;
    @Value("${spring.ai.anthropic.api-key}")
    private String anthropicKey;

    private final WebClient webClient;
    private final EmbeddingProperties embeddingProps;

    LlmClient(WebClient.Builder builder, EmbeddingProperties embeddingProps) {
        this.webClient = builder.build();
        this.embeddingProps = embeddingProps;
    }

    // Chat with provider-specific call
    public Mono<String> chat(String provider, String model, String system, String content) {
        if ("anthropic".equalsIgnoreCase(provider)) {
            return anthropicChat(model, system, content);
        } else {
            return openAiChat(model, system, content);
        }
    }

    /**
     * OpenAI Embeddings API 호출.
     *
     * 차원 검증 강제:
     *   - 응답 임베딩 차원이 EmbeddingProperties.expectedDim 과 다르면 IllegalStateException.
     *   - 모델 변경(text-embedding-3-small 1536 → text-embedding-3-large 3072) 시
     *     스키마 VECTOR(1536) 와 mismatch 되어 INSERT 실패하는 것을 사전 차단.
     *   - 운영 시 모델 바꾸려면 (1) DB 컬럼 마이그레이션 (2) expected-dim 동시 변경 필요.
     */
    public Mono<float[]> embed(String model, String text) {
        Map<String, Object> body = Map.of("model", model, "input", text);
        return webClient.post()
                .uri("https://api.openai.com/v1/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openaiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> {
                    var data = (List<Map<String, Object>>) m.get("data");
                    var vec = (List<Number>) data.getFirst().get("embedding");
                    float[] arr = new float[vec.size()];
                    for (int i = 0; i < vec.size(); i++) arr[i] = vec.get(i).floatValue();
                    int expected = embeddingProps.getExpectedDim();
                    if (arr.length != expected) {
                        throw new IllegalStateException(
                                "Embedding dimension mismatch: model=" + model
                                        + " returned " + arr.length
                                        + " dims, schema expects " + expected
                                        + ". Update app.embedding.expected-dim or migrate the vector column.");
                    }
                    return arr;
                });
    }

    /**
     * GPT-4o vision으로 이미지에서 텍스트 추출.
     * image-only PDF의 페이지 PNG를 넣으면 한국어/표/세로 텍스트도 잘 추출.
     * 페이지당 비용: high detail 약 $0.005 (gpt-4o 기준).
     */
    public Mono<String> extractTextFromImage(byte[] imageBytes, String mimeType, String model) {
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:" + (mimeType != null ? mimeType : "image/png") + ";base64," + b64;
        String prompt = "이 페이지의 모든 텍스트를 정확히 추출해서 그대로 출력해주세요. " +
                "표는 행/열을 보존해 텍스트로 표현. 그래프/도형의 라벨도 포함. " +
                "한국어 그대로 유지하고, 추측·요약 하지 말고 보이는 그대로만 텍스트로 변환하세요.";

        Map<String, Object> body = Map.of(
                "model", model != null ? model : "gpt-4o",
                "max_tokens", 4096,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", dataUri, "detail", "high"))
                        )
                ))
        );
        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openaiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .exchangeToMono(this::extractOpenAi);
    }

    // -------- OpenAI Chat --------
    private Mono<String> openAiChat(String model, String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role","system","content", system),
                        Map.of("role","user","content", user)
                )
        );
        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openaiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .exchangeToMono(this::extractOpenAi);
    }

    private Mono<String> extractOpenAi(ClientResponse res) {
        return res.bodyToMono(Map.class).map(m -> {
            var choices = (List<Map<String, Object>>) m.getOrDefault("choices", List.of());
            if (choices.isEmpty()) return "(no choices)";
            var msg = (Map<String, Object>) choices.getFirst().get("message");
            return Objects.toString(msg.get("content"), "");
        });
    }

    // -------- Anthropic Chat --------
    private Mono<String> anthropicChat(String model, String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 1024,
                "system", system,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(Map.of("type","text","text", user))
                        )
                )
        );
        return webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", anthropicKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve().bodyToMono(Map.class)
                .map(m -> {
                    var content = (List<Map<String,Object>>) m.getOrDefault("content", List.of());
                    if (content.isEmpty()) return "(no content)";
                    var first = content.getFirst();
                    return Objects.toString(first.get("text"), "");
                });
    }

}
