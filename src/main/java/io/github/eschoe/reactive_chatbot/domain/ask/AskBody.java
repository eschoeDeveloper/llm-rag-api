package io.github.eschoe.reactive_chatbot.domain.ask;

public record AskBody(String query, String provider, String model) { }
