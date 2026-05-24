# ADR-0002: Rate limit — Redis 장애 시 in-memory 로컬 한도로 degrade

- Status: Accepted
- Date: 2026-05-23
- 관련 commit: `41a2a34`

## Context

`RateLimitingService.isAllowed` 는 sessionId 기반으로 Redis fixed-window 카운터를 써서 요청 한도를 강제한다. 주 endpoint(`/api/chat`, `/api/embeddings/search`, `/api/documents`)는 **인증이 없다** (Google OAuth2 는 진행 중). 따라서 rate limit 의 주 목적은 DDoS 방어보다 **익명 사용자의 LLM API 비용 폭주 억제**다 — LLM 호출은 실제 돈.

기존 구현은 Redis 장애 시 `.onErrorReturn(true)` 로 **완전 fail-open** 이었다. 즉 Redis 가 죽으면 모든 요청이 통과 — 비용 방어가 통째로 해제된다. 이것은 버그가 아니라 명시된 정책(주석: "Redis 장애 시 fail-open")이었으나, 위 위협 모델에서는 위험한 선택이다. fail-open ↔ fail-closed 는 **가용성 ↔ 비용/보안** trade-off 이며 정답은 위협 모델에 달려 있다.

## Decision

Redis 장애 시 완전 fail-open 대신 **인스턴스 로컬 in-memory fixed-window 한도로 degrade** 한다:

- `onErrorReturn(true)` → `onErrorResume(e -> Mono.just(isAllowedLocal(sessionId)))`
- `ConcurrentHashMap<sessionId, LocalWindow(count, windowStartMs)>` 로 인스턴스 단위 카운터
- 한도 초과는 Redis 장애 중에도 차단
- `LOCAL_MAX_ENTRIES`(10k) 도달 시 만료 엔트리 sweep 으로 메모리 bound
- `getRemainingRequests`/`getResetTime` 은 정보성(429 헤더)이라 기존 fail-open 유지

## Consequences

**긍정**: Redis 장애 중에도 인스턴스 단위 비용 방어가 유지된다. 완전 무방비보다 크게 우수.

**trade-off**: 다중 dyno 환경에선 인스턴스별 카운터라 전체 한도가 인스턴스 수만큼 느슨해진다. 단 Redis 장애는 일시적이고, 느슨한 한도라도 무방비보다 낫다. 인스턴스 로컬 상태라 dyno 재시작 시 카운터 소실 (수용 — 장애 시 보수적 fallback 목적).

## Alternatives considered

- **fail-closed**: Redis 장애 시 모든 요청 차단 — 가용성 0. Redis 가 죽으면 전체 서비스 중단이라 과도
- **완전 fail-open (기존)**: 가용성 우선이나 비용 무방비. 위협 모델 불일치
- **외부 분산 rate limiter**: 별도 인프라 도입 복잡도. 현 규모 과잉
