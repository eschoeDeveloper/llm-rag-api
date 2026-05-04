package io.github.eschoe.llmragapi.search;

public record SearchBody(float[] embedding, Integer topK) { }
