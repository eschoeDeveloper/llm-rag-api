# Heroku Redis Writer/Reader 설정 가이드

## 🚀 빠른 설정

### 1. Redis Premium 플랜 추가
```bash
# Redis Premium 플랜 추가 (고가용성, 읽기 전용 복제본 포함)
heroku addons:create heroku-redis:premium-0 -a your-app-name
```

### 2. 자동 설정 스크립트 실행
```bash
# 스크립트 실행 권한 부여
chmod +x scripts/setup-heroku-redis.sh

# Redis 설정 자동화
./scripts/setup-heroku-redis.sh your-app-name
```

### 3. 애플리케이션 배포
```bash
# Heroku에 배포
git add .
git commit -m "Add Redis writer/reader configuration"
git push heroku main
```

## 📋 수동 설정 방법

### 1. Redis 애드온 추가
```bash
# Premium 플랜 (권장)
heroku addons:create heroku-redis:premium-0 -a your-app-name

# 또는 더 큰 용량
heroku addons:create heroku-redis:premium-1 -a your-app-name
```

### 2. 환경 변수 설정
```bash
# Redis URL에서 정보 추출
REDIS_URL=$(heroku config:get REDIS_URL -a your-app-name)

# Writer 설정 (마스터)
heroku config:set REDIS_WRITER_HOST=your-redis-host.herokuapps.com -a your-app-name
heroku config:set REDIS_WRITER_PORT=12345 -a your-app-name
heroku config:set REDIS_WRITER_PASSWORD=your-redis-password -a your-app-name
heroku config:set REDIS_WRITER_USERNAME=default -a your-app-name

# Reader 설정 (Premium 플랜에서는 자동 복제본)
heroku config:set REDIS_READER_HOST=your-redis-host.herokuapps.com -a your-app-name
heroku config:set REDIS_READER_PORT=12345 -a your-app-name
heroku config:set REDIS_READER_PASSWORD=your-redis-password -a your-app-name
heroku config:set REDIS_READER_USERNAME=default -a your-app-name
```

## 🔍 연결 테스트

### 1. 환경 변수 확인
```bash
heroku config -a your-app-name | grep REDIS
```

### 2. Redis 연결 테스트
```bash
# 연결 테스트 스크립트 실행
chmod +x scripts/test-redis-connection.sh
./scripts/test-redis-connection.sh your-app-name
```

### 3. 애플리케이션 로그 확인
```bash
heroku logs --tail -a your-app-name
```

## 🏗️ 아키텍처 설명

### Redis Writer/Reader 분리 구조
```
┌─────────────────┐    ┌─────────────────┐
│   Application   │    │   Application   │
│                 │    │                 │
│  Write Operations│    │  Read Operations│
│  (SET, DEL, etc)│    │  (GET, etc)     │
└─────────┬───────┘    └─────────┬───────┘
          │                      │
          ▼                      ▼
┌─────────────────┐    ┌─────────────────┐
│  Redis Writer   │    │  Redis Reader   │
│   (Master)      │◄───┤   (Replica)     │
│                 │    │                 │
│ • 캐시 저장      │    │ • 캐시 조회      │
│ • 히스토리 저장   │    │ • 히스토리 조회   │
│ • 분산락 관리     │    │ • 읽기 전용      │
└─────────────────┘    └─────────────────┘
```

### Heroku Redis Premium 플랜의 장점
- **자동 복제본**: 읽기 전용 복제본 자동 제공
- **고가용성**: 마스터 장애 시 자동 페일오버
- **성능 최적화**: 읽기/쓰기 분리로 성능 향상
- **모니터링**: Redis 메트릭 및 알림 제공

## ⚙️ 설정 최적화

### Heroku 환경에 맞는 설정
```yaml
# application-heroku.yaml
spring:
  data:
    redis:
      connect-timeout: 5000  # Heroku 네트워크 지연 고려
      command-timeout: 5000

app:
  llm:
    hist-max: 30  # Heroku 메모리 제한 고려
    hist-ttl-times: 24h  # TTL 단축
```

### 성능 모니터링
```bash
# Redis 메트릭 확인
heroku redis:info -a your-app-name

# Redis 로그 확인
heroku redis:logs -a your-app-name
```

## 🚨 문제 해결

### 1. 연결 실패
```bash
# Redis 상태 확인
heroku redis:info -a your-app-name

# 환경 변수 확인
heroku config -a your-app-name | grep REDIS
```

### 2. 성능 문제
```bash
# Redis 메트릭 확인
heroku redis:info -a your-app-name

# 애플리케이션 로그 확인
heroku logs --tail -a your-app-name
```

### 3. 메모리 부족
```bash
# Redis 메모리 사용량 확인
heroku redis:info -a your-app-name

# 히스토리 개수 줄이기
heroku config:set HIST_MAX=20 -a your-app-name
```

## 💰 비용 최적화

### Redis 플랜 선택 가이드
- **Hobby**: 개발/테스트용 (단일 인스턴스)
- **Premium-0**: 소규모 프로덕션 (복제본 포함)
- **Premium-1**: 중규모 프로덕션 (더 큰 용량)
- **Premium-2**: 대규모 프로덕션 (최고 성능)

### 비용 절약 팁
1. **TTL 최적화**: 불필요한 데이터 자동 삭제
2. **히스토리 제한**: `hist-max` 설정으로 메모리 사용량 제한
3. **캐시 전략**: 자주 사용되는 데이터만 캐시
4. **모니터링**: Redis 메트릭으로 사용량 추적

## 📊 모니터링 및 알림

### Redis 메트릭 모니터링
```bash
# Redis 정보 확인
heroku redis:info -a your-app-name

# Redis 로그 확인
heroku redis:logs -a your-app-name

# Redis 메트릭 확인
heroku redis:metrics -a your-app-name
```

### 알림 설정
```bash
# Redis 메모리 사용량 알림
heroku redis:alerts:add memory_usage -a your-app-name

# Redis 연결 수 알림
heroku redis:alerts:add connections -a your-app-name
```

