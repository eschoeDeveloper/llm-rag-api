package io.github.eschoe.llmragapi.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@Component
public class ReactiveLogTrace implements AsyncLogTrace{

    private static final Logger log = LoggerFactory.getLogger(ReactiveLogTrace.class);

    private static final String TRACE_ID_KEY = "traceId"; // 로깅 식별을 위한 ID
    private static final String START_PREFIX = "-->"; // 시작점 표시
    private static final String COMPLETE_PREFIX = "<--"; // 완료점 표시
    private static final String EXCEPTION_PREFIX = "<X-"; // 에러 표시

    private LogTraceId syncLogTraceId(ContextView ctx) {

        if( ctx.hasKey(TRACE_ID_KEY) ) {
            LogTraceId logTraceId = ctx.get(TRACE_ID_KEY);
            return logTraceId.createNextTraceId();
        } else {
            return new LogTraceId("", 0);
        }

    }

    private LogTraceId releaseLogTraceId(LogTraceId logTraceId) {

        if( logTraceId.isFirstLevel() ) {
            return null;
        } else {
            return logTraceId.createPrevTraceId();
        }

    }

    private static String addSpace(String prefix, int level) {

        StringBuilder sb = new StringBuilder();

        for( int i = 0; i < level; i++ ) {
            sb.append((i == level - 1) ? "||" + prefix : "|| ");
        }

        return sb.toString();

    }

    private void printLog(String level, String traceId, String message) {
        // SLF4J 가 시간/스레드를 자동으로 붙여주므로 직접 포맷 안 함
        switch (level) {
            case "ERROR" -> log.error("[{}] {}", traceId, message);
            case "WARN" -> log.warn("[{}] {}", traceId, message);
            default -> log.info("[{}] {}", traceId, message);
        }
    }

    /** ----------------------------------------------------------------
     * Logging Trace 시작 전
     * -----------------------------------------------------------------
     * */
    
    public Mono<LogTraceStatus> begin(String message) {

        return Mono.deferContextual(ctx -> {

            LogTraceId logTraceId = this.syncLogTraceId(ctx);
            Long startTimeMillis = System.currentTimeMillis();

            this.printLog("INFO", logTraceId.traceId(), addSpace(START_PREFIX, logTraceId.level()) + message);

            LogTraceStatus status = new LogTraceStatus(logTraceId, startTimeMillis, message);

            return Mono.just(status)
                    .contextWrite(context -> context.put(TRACE_ID_KEY, logTraceId.traceId()));

        });

    }

    /** ----------------------------------------------------------------
     * Logging Trace 종료
     * -----------------------------------------------------------------
     * */
    
    public Mono<Object> end(LogTraceStatus status) {
        return complete(status, null);
    }

    /** ----------------------------------------------------------------
     * Logging Trace 오류 단계
     * -----------------------------------------------------------------
     * */
    
    public Mono<Object> exception(LogTraceStatus status, Throwable e) {
        return complete(status, e);
    }

    /** ----------------------------------------------------------------
     * Logging Trace 완료
     * -----------------------------------------------------------------
     * */
    private Mono<Object> complete(LogTraceStatus status, Throwable e) {

        return Mono.deferContextual(ctx -> {

            Long stopTimeMillis = System.currentTimeMillis();
            Long resultTimeMillis = stopTimeMillis - status.startTimeMillis();

            String levelPrefix = "INFO";
            String logPrefix = COMPLETE_PREFIX;
            String message = " time=" + resultTimeMillis + "ms";

            LogTraceId logTraceId = status.logTraceId();

            if( e != null ) {
                levelPrefix = "ERROR";
                logPrefix = EXCEPTION_PREFIX;
                message += " exception=" + e.getMessage();
            }

            this.printLog(levelPrefix,logTraceId.traceId(), addSpace(logPrefix, logTraceId.level()) + status.message() + message);

            LogTraceId nextTraceId = releaseLogTraceId(logTraceId);

            if( nextTraceId != null ) {
                return Mono.empty().contextWrite(context -> context.put(TRACE_ID_KEY, nextTraceId.traceId()));
            } else {
                return Mono.empty();
            }

        });

    }
    
}
