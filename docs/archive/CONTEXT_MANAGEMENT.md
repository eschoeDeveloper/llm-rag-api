# 컨텍스트 유지 기능 사용법

## 개요

LLM+RAG 앱에서 이전 대화 컨텍스트를 유지하여 연속적인 대화가 가능하도록 개선된 기능입니다.

## 주요 개선사항

### 1. 세션 기반 컨텍스트 관리
- 각 대화 세션별로 고유한 세션 ID를 사용
- Redis를 통한 대화 히스토리 저장 및 관리
- 자동 세션 ID 생성 및 관리

### 2. 대화 히스토리 통합
- 이전 대화 내용을 현재 프롬프트에 포함
- 최근 10개 대화를 컨텍스트로 활용
- JSON 형태로 구조화된 대화 저장

### 3. 세션 관리 API
- 히스토리 조회, 삭제, 개수 확인 기능
- 세션별 독립적인 대화 관리

## API 사용법

### 1. 채팅 요청 (컨텍스트 유지)

```bash
# 세션 ID를 헤더로 전달
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-Session-ID: your-session-id" \
  -d '{
    "query": "안녕하세요",
    "config": {
      "topK": 5,
      "threshold": 0.7
    }
  }'
```

```bash
# 세션 ID를 요청 본문에 포함
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "이전에 말한 내용을 기억하나요?",
    "sessionId": "your-session-id",
    "config": {
      "topK": 5,
      "threshold": 0.7
    }
  }'
```

### 2. 대화 히스토리 조회

```bash
curl -X GET http://localhost:8080/api/history \
  -H "X-Session-ID: your-session-id"
```

### 3. 대화 히스토리 삭제

```bash
curl -X DELETE http://localhost:8080/api/history \
  -H "X-Session-ID: your-session-id"
```

### 4. 대화 히스토리 개수 확인

```bash
curl -X GET http://localhost:8080/api/history/count \
  -H "X-Session-ID: your-session-id"
```

## 세션 ID 관리

### 자동 생성
- 세션 ID가 제공되지 않으면 자동으로 UUID 생성
- 응답 헤더에 `X-Session-ID` 포함하여 클라이언트에 전달

### 우선순위
1. 요청 헤더의 `X-Session-ID`
2. 요청 본문의 `sessionId` 필드
3. 자동 생성된 UUID

## 대화 컨텍스트 구조

### 저장 형태
```json
{
  "role": "user",
  "content": "사용자 질문",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

```json
{
  "role": "assistant", 
  "content": "AI 답변",
  "timestamp": "2024-01-01T00:00:01Z"
}
```

### 프롬프트에 포함되는 형태
```
QUESTION:
현재 질문

CONTEXT:
- RAG 검색 결과 1 (점수: 0.850)
- RAG 검색 결과 2 (점수: 0.720)

PREVIOUS CONVERSATION:
사용자: 이전 질문
AI: 이전 답변
사용자: 또 다른 질문
AI: 또 다른 답변
```

## 설정

### application.yaml
```yaml
app:
  llm:
    hist-ttl-times: 48h    # 히스토리 TTL
    hist-max: 50           # 최대 히스토리 개수
```

## 클라이언트 구현 예시

### JavaScript/TypeScript
```javascript
class ChatClient {
  constructor() {
    this.sessionId = null;
  }

  async sendMessage(query) {
    const headers = {
      'Content-Type': 'application/json'
    };
    
    if (this.sessionId) {
      headers['X-Session-ID'] = this.sessionId;
    }

    const response = await fetch('/api/chat', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        query,
        config: { topK: 5, threshold: 0.7 }
      })
    });

    // 세션 ID 저장
    this.sessionId = response.headers.get('X-Session-ID');
    
    return response.json();
  }

  async getHistory() {
    const response = await fetch('/api/history', {
      headers: { 'X-Session-ID': this.sessionId }
    });
    return response.json();
  }

  async clearHistory() {
    await fetch('/api/history', {
      method: 'DELETE',
      headers: { 'X-Session-ID': this.sessionId }
    });
  }
}
```

## 주의사항

1. **세션 ID 보안**: 세션 ID는 민감한 정보가 아니지만, 사용자별로 고유해야 합니다.
2. **메모리 사용량**: 히스토리가 많아질수록 프롬프트 길이가 증가하여 토큰 사용량이 늘어납니다.
3. **TTL 관리**: Redis TTL 설정에 따라 오래된 대화는 자동으로 삭제됩니다.
4. **성능**: 히스토리가 많을수록 응답 시간이 증가할 수 있습니다.

## 문제 해결

### 컨텍스트가 유지되지 않는 경우
1. 세션 ID가 올바르게 전달되고 있는지 확인
2. Redis 연결 상태 확인
3. 히스토리 TTL 설정 확인

### 성능 문제
1. `hist-max` 설정을 줄여서 히스토리 개수 제한
2. 히스토리 조회 개수 제한 (기본 10개)
3. Redis 성능 모니터링
