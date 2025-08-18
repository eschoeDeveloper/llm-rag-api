package io.github.eschoe.reactive_chatbot.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class LlmContextClient {

    @Value("${spring.ai.openai.api-key}")
    private String openaiKey;
    @Value("${spring.ai.anthropic.api-key}")
    private String anthropicKey;

    private final WebClient webClient;

    LlmContextClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    // Chat with provider-specific call
    public Mono<String> chat(String provider, String model, String system, String content) {
        if ("anthropic".equalsIgnoreCase(provider)) {
            return anthropicChat(model, system, content);
        } else {
            return openAiChat(model, system, content);
        }
    }

    // Embeddings (OpenAI)
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
                    return arr;
                });
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
