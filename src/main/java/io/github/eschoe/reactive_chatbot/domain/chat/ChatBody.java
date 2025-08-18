package io.github.eschoe.reactive_chatbot.domain.chat;

public record ChatBody(String query, Integer topK, float[] embedding, String provider, String model) { }
