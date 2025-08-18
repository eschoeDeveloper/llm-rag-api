# ⚡ reactive-chatbot-api

---
## 📖 프로젝트 개요

**reactive-chatbot-api** 는 spring 기반의 reactive api ( WebFlux )를 이해하기 위한 예제 프로젝트로서 h2 데이터베이스 기반 실 데이터를 활용하여 Service 로직에 대한 행위 테스트를 진행합니다.

---

## 🛠 주요 로직



---

## 🏗 아키텍처 및 기술 스택

### 백엔드

* **언어 & 프레임워크:** Java 23, Spring Boot 3
* **API 문서화:** SpringDoc OpenAPI
* **인증/인가:** Spring Security, JWT

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
