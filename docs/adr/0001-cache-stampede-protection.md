# ADR-0001: LLM 응답 캐시 stampede 보호 — 폴링 + fallback

- Status: Accepted
- Date: 2026-05-23
- 관련 commit: `4dc87a6`

## Context

`LlmCacheService.getOrInvoke` 는 동일 입력(model + inputHash)에 대한 LLM 응답을 캐싱한다. 같은 입력으로 동시 요청이 몰리면 (cache miss 상태) 모두 LLM 을 호출해 비용이 N배가 된다 — cache stampede. 이를 막기 위해 Redis SETNX 분산락을 둔다: 락 획득자만 실제 LLM 을 호출하고, 나머지는 그 결과를 캐시에서 읽는다.

문제는 **락을 못 잡은 측의 처리**였다. 기존 구현은 `Mono.delay(120ms).then(redisR.get(redisKey))` — 120ms 한 번 기다린 뒤 캐시를 단발 재조회하고, 그래도 없으면 **empty Mono** 를 반환했다. 코드 주석은 "호출자가 다시 시도하거나 empty 반환"이라는 trade-off 를 명시했으나, 실제 호출자(`ChatService.invokeLlmCached`, `AskService.ask`)는 `.then(answerMono)` 로 empty 를 그대로 전파했다 — retry 가 없었다. WebFlux endpoint 에서 빈 Mono 는 HTTP 404 또는 SSE 갑작스러운 종료로 사용자에게 노출됐다. 락 TTL 60s 대비 재조회가 120ms 단발이라, gpt-4o long-tail(30~50s) 상황에선 거의 항상 깨졌다.

## Decision

락 fail 측을 **짧은 간격 폴링 + timeout fallback** 으로 변경한다:

- `Flux.interval(100ms).take(300)` — 응답이 캐시에 set 될 때까지 최대 30s 폴링 (`STAMPEDE_POLL_INTERVAL`, `STAMPEDE_MAX_POLLS`)
- 폴링 timeout 도달 시(`switchIfEmpty`) — 본인이 invoker 를 호출하는 **안전망**. 락 보유자가 폭사했거나 매우 느린 경우 보호. 운영 모니터링용 metric `cache_stampede_fallback` 증가
- 폴링 캡(30s)은 락 TTL(60s)보다 짧게 둔다

## Consequences

**긍정**: 락 fail 측도 정상 케이스에서 응답을 보장받는다 (empty Mono 제거). 정상 케이스에서 LLM 호출은 여전히 1회 — stampede 방지 본 목적 유지.

**trade-off**: 폴링이 Redis 에 부하를 준다 (100ms 간격). 폴링 timeout fallback 은 드물게 추가 LLM 호출을 유발할 수 있으나 metric 으로 가시화.

## Alternatives considered

- **호출자 retry**: 모든 호출자가 retry 를 잊지 말아야 — 응집도 저하. 근본 fix 아님
- **Redis Pub/Sub 락 해제 신호**: 가장 우아하나 `ReactiveRedisMessageListenerContainer` 등 인프라 복잡도 증가. 현 규모 과잉
- **락 fail 측 자체 invoke**: stampede 방지 목적 정면 위반 (비용 폭주)
