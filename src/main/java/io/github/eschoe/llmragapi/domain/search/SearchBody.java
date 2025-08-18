package io.github.eschoe.llmragapi.domain.search;

public record SearchBody(float[] embedding, Integer topK) { }
