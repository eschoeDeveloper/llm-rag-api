package io.github.eschoe.llmragapi.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SimpleDurationParser {

    private final Pattern P = Pattern.compile(
            "^(?<value>[-+]?\\d+(?:[\\.,]\\d+)?)\\s*(?<unit>ns|us|µs|ms|s|m|h|d)$",
            Pattern.CASE_INSENSITIVE
    );

    public Duration parse(String duration) {

        Matcher m = P.matcher(duration);
        if (!m.matches()) { throw new IllegalArgumentException("Invalid duration: " + duration); }

        String unit = m.group("unit").toLowerCase();
        BigDecimal totalNanos = getBigDecimal(m, unit);

        long seconds = totalNanos.divide(BigDecimal.valueOf(1_000_000_000L)).longValue();
        long nanos   = totalNanos.remainder(BigDecimal.valueOf(1_000_000_000L)).longValue();

        return Duration.ofSeconds(seconds, nanos);

    }

    private BigDecimal getBigDecimal(Matcher m, String unit) {
        BigDecimal value = new BigDecimal(m.group("value"));

        long nanosPerUnit = switch (unit) {
            case "ns" -> 1L;
            case "us", "µs" -> 1_000L;
            case "ms" -> 1_000_000L;
            case "s"  -> 1_000_000_000L;
            case "m"  -> 60L * 1_000_000_000L;
            case "h"  -> 3600L * 1_000_000_000L;
            case "d"  -> 86400L * 1_000_000_000L;
            default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
        };

        BigDecimal totalNanos = value.multiply(BigDecimal.valueOf(nanosPerUnit));
        return totalNanos;
    }

}
