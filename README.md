# LLM RAG API

고급 LLM + RAG (Retrieval Augmented Generation) API 서버입니다. 문서 업로드, 고급 검색, 대화 스레드 관리, 그리고 컨텍스트 유지 기능을 제공합니다.

## 🚀 주요 기능

### 📄 문서 관리
- **다양한 형식 지원**: PDF, DOCX, TXT, MD 파일 업로드
- **자동 파싱**: Apache Tika를 사용한 텍스트 추출
- **청크 분할**: 문서를 의미있는 단위로 분할
- **임베딩 생성**: 각 청크별 벡터 임베딩 자동 생성
- **문서 메타데이터**: 제목, 설명, 카테고리 관리

### 🔍 고급 검색
- **하이브리드 검색**: 의미 검색 + 키워드 검색 결합
- **고급 필터링**: 점수, 날짜, 제목별 필터
- **검색 히스토리**: 사용자별 검색 기록 관리
- **실시간 결과**: pgvector 기반 벡터 유사도 검색

### 💬 대화 스레드 관리
- **스레드 생성**: 주제별 대화 분리
- **메시지 관리**: 스레드별 메시지 추가/관리
- **상태 관리**: 활성/보관/삭제 상태 관리
- **제목 관리**: 대화 제목 수정 및 관리

### 🧠 컨텍스트 유지
- **세션 관리**: 자동 세션 ID 생성 및 관리
- **대화 히스토리**: Redis 기반 대화 기록 저장
- **컨텍스트 통합**: 이전 대화를 프롬프트에 자동 포함
- **Rate Limiting**: 사용자별 요청 제한 (60초당 10회)

## 🛠 기술 스택

- **Framework**: Spring Boot 3.5.4 + WebFlux (Reactive)
- **Database**: PostgreSQL + pgvector (벡터 검색)
- **Cache**: Redis (세션, 히스토리, Rate Limiting)
- **AI**: OpenAI GPT-4 + Embeddings
- **Document Parsing**: Apache Tika
- **Build**: Gradle
- **Java**: 23

## 📋 API 엔드포인트

### 채팅 API
```http
POST /api/chat
Content-Type: application/json

{
  "query": "사용자 질문",
  "config": {
    "topK": 5,
    "threshold": 0.7
  },
  "sessionId": "optional-session-id"
}
```

### 고급 검색 API
```http
POST /api/advanced-search
Content-Type: application/json

{
  "query": "검색어",
  "searchType": "HYBRID",
  "filters": [
    {
      "field": "score",
      "operator": "GREATER_THAN",
      "value": 0.8
    }
  ],
  "page": 0,
  "size": 10
}
```

### 대화 스레드 API
```http
# 스레드 생성
POST /api/threads
{
  "title": "새 대화",
  "description": "대화 설명"
}

# 스레드 조회
GET /api/threads/{threadId}

# 메시지 추가
POST /api/threads/{threadId}/messages
{
  "content": "메시지 내용",
  "role": "USER"
}
```

### 문서 관리 API
```http
# 문서 업로드
POST /api/documents/upload
Content-Type: multipart/form-data

# 문서 목록
GET /api/documents

# 문서 삭제
DELETE /api/documents/{documentId}
```

### 히스토리 API
```http
# 대화 히스토리
GET /api/history?sessionId={sessionId}

# 히스토리 삭제
DELETE /api/history?sessionId={sessionId}

# 히스토리 개수
GET /api/history/count?sessionId={sessionId}
```

## 🚀 배포

### Heroku 배포
```bash
# Heroku CLI 설치 후
heroku create your-app-name
heroku addons:create heroku-postgresql:mini
heroku addons:create heroku-redis:premium-0

# 환경 변수 설정
heroku config:set OPENAI_API_KEY=your-openai-key
heroku config:set SPRING_PROFILES_ACTIVE=prod

# 배포
git push heroku main
```

### 로컬 실행
```bash
# PostgreSQL + Redis 실행 필요
./gradlew bootRun
```

## ⚙️ 환경 변수

### 필수
- `OPENAI_API_KEY`: OpenAI API 키
- `DATABASE_URL`: PostgreSQL 연결 URL
- `REDIS_URL`: Redis 연결 URL

### 선택사항
- `SPRING_PROFILES_ACTIVE`: 프로파일 (기본: dev)
- `APP_RATE_LIMIT_WINDOW_SEC`: Rate Limit 윈도우 (기본: 60)
- `APP_RATE_LIMIT_LIMIT`: Rate Limit 제한 (기본: 10)
- `APP_DOCUMENT_CHUNK_SIZE`: 문서 청크 크기 (기본: 1000)
- `APP_DOCUMENT_OVERLAP_SIZE`: 청크 오버랩 크기 (기본: 100)

## 📊 모니터링

### Health Check
```http
GET /actuator/health
```

### Redis 상태
```http
GET /actuator/health/redis
```

### 메트릭
```http
GET /actuator/metrics
```

## 🔧 개발

### 프로젝트 구조
```
src/main/java/io/github/eschoe/llmragapi/
├── config/                 # 설정 클래스
├── domain/                 # 도메인 모델
│   ├── chat/              # 채팅 관련
│   ├── document/           # 문서 관리
│   ├── search/            # 검색 기능
│   └── conversation/       # 대화 스레드
├── service/               # 비즈니스 로직
├── dao/                   # 데이터 접근
└── global/                # 공통 유틸리티
```

### 빌드 및 테스트
```bash
# 컴파일
./gradlew compileJava

# 테스트
./gradlew test

# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

## 📈 성능 최적화

- **Reactive Programming**: WebFlux로 높은 동시성 처리
- **Connection Pooling**: Redis/PostgreSQL 연결 풀 최적화
- **Caching**: Redis 기반 캐싱으로 응답 속도 향상
- **Rate Limiting**: 과도한 요청 방지
- **Connection Timeout**: 30초 타임아웃으로 안정성 확보

## 🛡 보안

- **Rate Limiting**: 사용자별 요청 제한
- **Input Validation**: 입력값 검증
- **Error Handling**: 상세한 에러 응답
- **Session Management**: 안전한 세션 관리

## 📝 라이선스

MIT License

## 🤝 기여

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📞 지원

문제가 있으시면 이슈를 생성해주세요.