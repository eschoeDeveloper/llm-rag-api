package io.github.eschoe.llmragapi.domain.search;

import java.time.OffsetDateTime;

public record TopKCosine(Long id, String title, String content, OffsetDateTime createdAt) {}

