# ⚡ LLM-RAG-API

---
## 📖 프로젝트 개요

**LLM-RAG-API** 는 spring 기반의 LLM + RAG를 이해하기 위한 예제 프로젝트로서 PgVector를 활용하며 LLM과 RAG의 기초 개념을 학습하는데 초점을 맞춰 진행하였습니다.

---

## 🛠 주요 로직

1. Top-K 유사도 검색
2. OpenAI LLM 활용

---

## 🏗 아키텍처 및 기술 스택

### 백엔드

* **언어 & 프레임워크:** Java 23, Spring Boot 3
* **API 문서화:** SpringDoc OpenAPI
* **데이터베이스**: PostgreSQL 17
* **LLM+RAG**: pgVector, OpenAI Client
* **인증/인가:** Spring Security

---

## 🚀 설치 및 실행

1. 저장소 클론

   ```bash
   git clone https://github.com/eschoeDeveloper/llm_rag_api.git
   cd llm_rag_api
   ```

2. API 문서 확인

   ```text
   http://localhost:8090/swagger-ui.html
   ```

---

## 📂 프로젝트 구조

```
├── src/main/java/io/github/eschoe/llm_rag_api
│   ├── config       # Config 클래스
│   └── 
├── src/main/resources
│   ├── application.yaml # 애플리케이션 설정 파일
```

---

## 🤝 연락처

* **GitHub:** [github.com/eschoeDeveloper/llm_rag_api](https://github.com/eschoeDeveloper/llm_rag_api)
* **Email:** [develop.eschoe@gmail.com](mailto:develop.eschoe@gmail.com)

---

## 📜 라이선스

Apache License 2.0 © 2025 ChoeEuiSeung
