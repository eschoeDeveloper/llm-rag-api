# 405 에러 완전 진단 가이드

## 현재 상황
- 프론트엔드: `https://www.llmragapp.com`에서 실행 중
- 요청 URL: `https://www.llmragapp.com/api/ask` (올바르게 변환됨)
- 문제: 405 Method Not Allowed 또는 로그가 서버에 안 올라옴

## 핵심 문제
**백엔드 API 서버가 실제로 어디에 있는지 확인해야 합니다!**

## 확인 방법

### 1. 백엔드 API 서버 URL 확인

#### Heroku에 배포되어 있다면:
```bash
# Heroku 앱 이름 확인
heroku apps

# 또는
git remote -v | grep heroku
```

백엔드가 `https://your-api-app.herokuapp.com`에 있다면:
- 프론트엔드에서 base URL을 `https://your-api-app.herokuapp.com/api`로 설정

#### 다른 서버에 있다면:
- 백엔드가 실행 중인 실제 서버 URL 확인
- 예: `https://api.llmragapp.com` 또는 `https://backend.llmragapp.com`

### 2. 브라우저 네트워크 탭 확인 (F12)

1. **Network 탭 열기**
2. **요청 시도**
3. **`/api/ask` 요청 찾기**
4. **확인할 내용:**
   - **Request URL**: 실제로 어디로 요청이 가는지
   - **Status Code**: 405인지, 다른 에러인지
   - **Response Headers**: `Allow` 헤더가 있으면 어떤 메서드가 허용되는지 보여줌
   - **Remote Address**: 실제 연결된 서버 IP/도메인

### 3. 백엔드 로그 확인

#### 로컬에서 실행 중이라면:
```bash
./gradlew bootRun
```
콘솔에서 다음 로그 확인:
```
INFO ChatRouter - === REGISTERING CHAT ROUTER ===
INFO AskRouter - === REGISTERING ASK ROUTER ===
DEBUG AskHandler - === ASK REQUEST RECEIVED ===
```

#### Heroku에 배포되어 있다면:
```bash
heroku logs --tail -a your-api-app-name
```

### 4. 실제 문제 진단

#### 시나리오 1: 백엔드가 다른 서버에 있음
**증상**: 요청이 `www.llmragapp.com/api/ask`로 가는데 백엔드는 `api.llmragapp.com`에 있음
**해결**: 프론트엔드에서 base URL을 올바른 서버 URL로 설정

#### 시나리오 2: 백엔드가 같은 도메인에 있지만 라우팅 안됨
**증상**: 요청은 도달하지만 405 에러
**원인**: 
- RouterFunction이 등록되지 않음
- 다른 필터/라우터가 먼저 매칭됨
- 서버 재시작 필요

#### 시나리오 3: CORS 프리플라이트 실패
**증상**: OPTIONS 요청이 405
**원인**: CorsWebFilter가 작동하지 않음
**해결**: CorsWebFilter 빈 확인, SecurityConfig 확인

### 5. 즉시 테스트 방법

#### curl로 직접 테스트:
```bash
# 로컬 서버 테스트
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:3000" \
  -d '{"query":"test"}' \
  -v

# 프로덕션 서버 테스트 (실제 API URL 사용)
curl -X POST https://your-api-server.com/api/ask \
  -H "Content-Type: application/json" \
  -H "Origin: https://www.llmragapp.com" \
  -d '{"query":"test"}' \
  -v
```

### 6. 백엔드 서버 상태 확인

#### Health Check:
```bash
# 로컬
curl http://localhost:8080/actuator/health

# 프로덕션
curl https://your-api-server.com/actuator/health
```

#### 라우터 등록 확인:
서버 시작 로그에서 다음 확인:
```
INFO ChatRouter - Chat router function registered successfully
INFO AskRouter - Ask router function registered successfully
```

## 빠른 해결 체크리스트

- [ ] 백엔드 API 서버의 실제 URL 확인
- [ ] 프론트엔드 base URL이 올바른 서버를 가리키는지 확인
- [ ] 백엔드 서버가 실행 중인지 확인
- [ ] 백엔드 로그에서 라우터 등록 확인
- [ ] 네트워크 탭에서 실제 요청 URL 확인
- [ ] OPTIONS 프리플라이트 요청이 성공하는지 확인
- [ ] POST 요청이 실제로 서버에 도달하는지 확인 (로그 확인)

