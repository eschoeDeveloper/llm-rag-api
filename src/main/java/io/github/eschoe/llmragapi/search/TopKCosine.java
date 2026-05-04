package io.github.eschoe.llmragapi.search;

import java.time.OffsetDateTime;

public record TopKCosine(Long id, String title, String content, Double score, OffsetDateTime createdAt) {}

