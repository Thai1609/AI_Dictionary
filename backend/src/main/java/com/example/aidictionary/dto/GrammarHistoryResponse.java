package com.example.aidictionary.dto;

import java.time.LocalDateTime;

public record GrammarHistoryResponse(
        Long id,
        String originalText,
        String correctedText,
        String naturalText,
        String translation,
        Boolean correct,
        String sourceLanguage,
        String targetLanguage,
        String note,
        String resultJson,
        LocalDateTime createdAt
) {
}
