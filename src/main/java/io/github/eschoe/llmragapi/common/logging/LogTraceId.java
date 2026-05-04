package io.github.eschoe.llmragapi.common.logging;

import java.util.UUID;

public record LogTraceId(String traceId, int level) implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String createTraceId() {
        return UUID.randomUUID().toString();
    }

    public LogTraceId createNextTraceId() {
        return new LogTraceId(traceId, level + 1);
    }

    public LogTraceId createPrevTraceId() {
        return new LogTraceId(traceId, level - 1);
    }

    public boolean isFirstLevel() {
        return level == 0;
    }

}
