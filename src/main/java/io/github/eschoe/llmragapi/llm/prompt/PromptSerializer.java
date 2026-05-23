package io.github.eschoe.llmragapi.llm.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 프롬프트/대화 이력 메시지를 JSON 직렬화하는 공용 헬퍼.
 * 이전에 ChatService, AskService 양쪽에 똑같은 toPromptJson/toMessageJson 가 중복돼 있었다.
 * 한 곳으로 통합 — 변경 시 한 군데만 고치면 됨.
 */
@Component
public class PromptSerializer {

    private static final Logger log = LoggerFactory.getLogger(PromptSerializer.class);

    private final ObjectMapper objectMapper;

    public PromptSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 프롬프트(system + user) 캐시 키용 JSON 표현.
     * 캐시 hit/miss 식별에 사용되므로 출력 안정성이 중요 — LinkedHashMap 으로 키 순서 고정.
     */
    public String toPromptJson(String systemPrompt, String userPrompt) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("system", systemPrompt);
            payload.put("user", userPrompt);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("[prompt-serializer] failed to serialize prompt: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 대화 이력 한 메시지를 JSON 으로 직렬화. ChatHistoryStore 에 append 할 때 사용.
     */
    public String toMessageJson(String role, String content) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("role", role);
            payload.put("content", content);
            payload.put("timestamp", Instant.now().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("[prompt-serializer] failed to serialize message: {}", e.getMessage());
            return "{}";
        }
    }
}
