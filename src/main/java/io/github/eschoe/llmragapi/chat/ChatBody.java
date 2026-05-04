package io.github.eschoe.llmragapi.chat;

public record ChatBody(String query, Integer topK, float[] embedding, String provider, String model) { }
