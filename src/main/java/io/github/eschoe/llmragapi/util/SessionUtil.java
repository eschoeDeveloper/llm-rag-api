package io.github.eschoe.llmragapi.util;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.UUID;

/**
 * 세션 관리 유틸리티 클래스
 * 세션 ID 생성, 추출, 검증 등의 기능을 제공합니다.
 */
@Component
public class SessionUtil {

    /**
     * 새로운 세션 ID를 생성합니다.
     * @return UUID 기반 세션 ID
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 요청에서 세션 ID를 추출합니다.
     * 우선순위: 헤더 > 쿠키 > 새로 생성
     * 
     * @param request 서버 요청
     * @return 세션 ID
     */
    public String extractSessionId(ServerRequest request) {
        // 1. 헤더에서 세션 ID 추출
        String sessionId = request.headers().firstHeader("X-Session-ID");
        if (isValidSessionId(sessionId)) {
            return sessionId;
        }

        // 2. 쿠키에서 세션 ID 추출
        sessionId = request.cookies().getFirst("SESSION_ID");
        if (isValidSessionId(sessionId)) {
            return sessionId;
        }

        // 3. 새 세션 ID 생성
        return generateSessionId();
    }

    /**
     * 세션 ID가 유효한지 검증합니다.
     * 
     * @param sessionId 검증할 세션 ID
     * @return 유효하면 true, 아니면 false
     */
    public boolean isValidSessionId(String sessionId) {
        return sessionId != null && !sessionId.trim().isEmpty() && sessionId.length() > 10;
    }

    /**
     * 세션 ID를 정규화합니다.
     * 
     * @param sessionId 정규화할 세션 ID
     * @return 정규화된 세션 ID
     */
    public String normalizeSessionId(String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return generateSessionId();
        }
        return sessionId.trim();
    }
}
