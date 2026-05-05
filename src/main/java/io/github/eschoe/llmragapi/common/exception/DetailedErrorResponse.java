package io.github.eschoe.llmragapi.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * 클라이언트로 전송되는 에러 응답.
 *
 * 보안: prod 프로필에서는 details 자동 마스킹.
 * 원본 details (e.getMessage(), stack trace 단서)는 로그에만 남기고 클라이언트엔 generic 메시지.
 * → DB 호스트, 의존성 버전, 내부 경로 등 정보 누출 차단.
 *
 * 디버깅이 필요하면 sessionId + timestamp 로 로그·Sentry 에서 검색.
 */
public class DetailedErrorResponse {

    private static final Logger log = LoggerFactory.getLogger(DetailedErrorResponse.class);
    private static final String MASKED_PLACEHOLDER =
            "(상세 정보는 운영 환경에서 가려집니다. 지원이 필요하면 sessionId 와 timestamp 를 알려주세요.)";

    private String error;
    private String message;
    private String details;
    private String timestamp;
    private String sessionId;
    private Map<String, Object> metadata;

    public DetailedErrorResponse() {}

    public DetailedErrorResponse(String error, String message, String details, String sessionId) {
        this.error = error;
        this.message = message;
        this.details = sanitize(error, details, sessionId);
        this.sessionId = sessionId;
        this.timestamp = Instant.now().toString();
    }

    public DetailedErrorResponse(String error, String message, String details, String sessionId, Map<String, Object> metadata) {
        this.error = error;
        this.message = message;
        this.details = sanitize(error, details, sessionId);
        this.sessionId = sessionId;
        this.timestamp = Instant.now().toString();
        this.metadata = metadata;
    }

    /**
     * prod 프로필이면 details 마스킹 + 원본은 로그에 남김.
     * 그 외(local/test)는 그대로 노출 — 개발 편의성 유지.
     */
    private static String sanitize(String error, String details, String sessionId) {
        if (details == null || details.isBlank()) return details;
        if (isProduction()) {
            log.warn("[error-mask] code={} sessionId={} masked-details={}", error, sessionId, details);
            return MASKED_PLACEHOLDER;
        }
        return details;
    }

    private static boolean isProduction() {
        String profile = System.getProperty("spring.profiles.active");
        if (profile == null) profile = System.getenv("SPRING_PROFILES_ACTIVE");
        return profile != null && profile.contains("prod");
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
