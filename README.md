# ⚡ LLM-RAG-API

---
## 📖 프로젝트 개요

**LLM-RAG-API** 는 spring 기반의 LLM+RAG API로서, 사용자 입력값을 받아 주요 로직에 해당하는 서비스 로직을 실행하고 테스트하기 위한 API입니다.

---

## 🛠 주요 로직

1. OpenAI 사용자 입력 텍스트 분석
2. PgVector + PostgreSQL을 활용한 벡터 분석
3. Top-K 유사도 분석


---

## 🏗 아키텍처 및 기술 스택

### 백엔드

* **언어 & 프레임워크:** Java 23, Spring Boot 3
* **API 문서화:** SpringDoc OpenAPI
* **인증/인가:** Spring Security

---

## 🚀 설치 및 실행

1. 저장소 클론

   ```bash
   git clone https://github.com/eschoeDeveloper/reactive_chatbot_api.git
   cd reactive_chatbot_api
   ```

2. 빌드 및 실행
   ```bash
   ./gradlew clean build
   java -jar ./build/libs/llm-rag-api-llm-rag-api-0.0.1-SNAPSHOT.jar
   ```

3. API 문서 확인

   ```text
   http://localhost:8090/swagger-ui.html
   ```

---

## 📂 프로젝트 구조

```
├── src/main/java/io/github/eschoe/llm-rag-api
│   ├── config       # Config 클래스
│   ├── client       # OpenAI LLM Client
│   ├── dao          # 임베딩 데이터 저장
│   ├── repository   # 임베딩 데이터 조회
│   ├── domain       # ASK, CHATBOT, SEARCH API
│   ├── entity       # Database Entity
│   ├── util         # Util 클래스
│   └── LlmRagApiApplication.java   # Boot 실행
├── src/main/resources
└── application.yaml # 애플리케이션 설정 파일
```

---

## 🤝 연락처

* **GitHub:** [github.com/eschoeDeveloper/llm-rag-api](https://github.com/eschoeDeveloper/llm-rag-api)
* **Email:** [develop.eschoe@gmail.com](mailto:develop.eschoe@gmail.com)

---

## 📜 라이선스

Apache License 2.0 © 2025 ChoeEuiSeung
