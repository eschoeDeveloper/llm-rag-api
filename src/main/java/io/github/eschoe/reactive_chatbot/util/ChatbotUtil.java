package io.github.eschoe.reactive_chatbot.util;

import org.springframework.stereotype.Component;

@Component
public class ChatbotUtil {

    public static String opt(String s) { return s == null ? "" : s.trim(); }

    public static String safeSnippet(String s) {
        if (s == null) return "";
        String t = s.replace('\n',' ').trim();
        return t.length() > 400 ? t.substring(0, 400) + " …" : t;
    }

    public static String chooseModel(String provider, String override) {
        if (override != null && !override.isBlank()) return override;
        if ("anthropic".equalsIgnoreCase(provider)) return System.getenv().getOrDefault("APP_ANTHROPIC_MODEL", "claude-3-5-sonnet-20240620");
        return System.getenv().getOrDefault("APP_OPENAI_MODEL", "gpt-4o-mini");
    }

}
