package io.github.eschoe.reactive_chatbot.domain.search;

public record SearchBody(float[] embedding, Integer topK) { }
