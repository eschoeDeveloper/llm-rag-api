# 405 오류 로그 분석 가이드

## 1. 백엔드 로그 확인 방법

### 애플리케이션 실행 시 로그 확인
```bash
# 로컬 실행 시
./gradlew bootRun

# 또는 IDE에서 실행 시 콘솔 출력 확인
```

### 주요 확인 포인트

#### 1. 서버 시작 로그
다음 로그가 나타나는지 확인:
```
CorsWebFilter bean created
RouterFunction registered: /api/chat
RouterFunction registered: /api/ask
```

#### 2. 요청 수신 로그
요청이 들어올 때 다음과 같은 로그가 나타나야 함:
```
DEBUG org.springframework.web.reactive.function.server - Handling request: POST /api/chat
DEBUG org.springframework.web.cors - CORS request detected: Origin=...
DEBUG io.github.eschoe.llmragapi.domain.chat.ChatHandler - Processing chat request
```

#### 3. 405 오류 발생 시 로그
```
WARN org.springframework.web.reactive.function.server - No matching handler for: OPTIONS /api/chat
또는
ERROR org.springframework.web.reactive.function.server - Method not allowed: POST /api/chat
```

### 로그 레벨별 의미

- **DEBUG**: 상세한 요청/응답 정보
- **INFO**: 일반적인 동작 정보
- **WARN**: 경고 (예: 404, 405)
- **ERROR**: 에러 발생

## 2. 프론트엔드 로그 확인 방법

### 브라우저 개발자 도구 (F12)

#### Network 탭 확인
1. **F12** 눌러서 개발자 도구 열기
2. **Network** 탭 선택
3. 페이지 새로고침 (F5)
4. API 호출 시도
5. `/api/chat` 또는 `/api/ask` 요청 찾기

#### 확인할 내용:
- **Request Method**: POST인지 확인
- **Status Code**: 405인지 확인
- **Request Headers**: 
  - `Origin`: 프론트엔드 도메인
  - `Content-Type`: `application/json`
- **Response Headers**:
  - `Access-Control-Allow-Origin`: 있는지 확인
  - `Allow`: 어떤 메서드가 허용되는지 확인

#### Console 탭 확인
```javascript
// 에러 메시지 확인
fetch failed: 405 Method Not Allowed
또는
CORS error: ...
```

## 3. 문제 진단 체크리스트

### 백엔드 확인
- [ ] 서버가 정상적으로 시작되었는가?
- [ ] `CorsWebFilter` 빈이 생성되었는가? (로그에서 확인)
- [ ] `RouterFunction`이 등록되었는가?
- [ ] 요청이 서버에 도달하는가? (로그에서 확인)
- [ ] OPTIONS 요청이 들어오는가?
- [ ] POST 요청이 들어오는가?

### 프론트엔드 확인
- [ ] 네트워크 탭에서 실제 요청 URL 확인
- [ ] 요청 메서드가 POST인지 확인
- [ ] CORS 에러인지 405 에러인지 확인
- [ ] 브라우저 콘솔 에러 메시지 확인
- [ ] `base` prop이 제대로 전달되는지 확인

## 4. 자주 발생하는 문제와 해결

### 문제 1: OPTIONS 요청이 405 에러
**증상**: Network 탭에서 OPTIONS 요청이 405
**원인**: CorsWebFilter가 작동하지 않음
**해결**: 
- `CorsWebFilter` 빈이 제대로 등록되었는지 확인
- `SecurityConfig`에서 OPTIONS 허용 확인

### 문제 2: POST 요청이 405 에러
**증상**: Network 탭에서 POST 요청이 405
**원인**: RouterFunction이 등록되지 않았거나 경로가 잘못됨
**해결**:
- 라우터 설정 확인 (`ChatRouter`, `AskRouter`)
- 경로가 `/api/chat`, `/api/ask`인지 확인

### 문제 3: CORS 에러
**증상**: CORS policy 에러 메시지
**원인**: CORS 설정이 잘못됨
**해결**:
- `allowedOrigins` 설정 확인
- `CorsWebFilter` 설정 확인

## 5. 디버깅용 로그 추가

### ChatHandler에 로그 추가 예시:
```java
public Mono<ServerResponse> chat(ServerRequest req) {
    logger.debug("=== CHAT REQUEST ===");
    logger.debug("Method: {}", req.method());
    logger.debug("Path: {}", req.path());
    logger.debug("Headers: {}", req.headers().asHttpHeaders());
    // ... 나머지 코드
}
```

### AskHandler에 로그 추가 예시:
```java
public Mono<ServerResponse> ask(ServerRequest req) {
    logger.debug("=== ASK REQUEST ===");
    logger.debug("Method: {}", req.method());
    logger.debug("Path: {}", req.path());
    // ... 나머지 코드
}
```

## 6. 실제 테스트 방법

### curl로 테스트:
```bash
# OPTIONS 요청 테스트
curl -X OPTIONS http://localhost:8080/api/chat \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  -v

# POST 요청 테스트
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:5173" \
  -d '{"query":"test"}' \
  -v
```

### 기대되는 응답:
- OPTIONS: 200 OK with CORS headers
- POST: 200 OK with response body

## 7. 로그 파일 위치

- 콘솔: 애플리케이션 실행 터미널
- 파일: 설정에 따라 다름 (기본적으로 콘솔만)

