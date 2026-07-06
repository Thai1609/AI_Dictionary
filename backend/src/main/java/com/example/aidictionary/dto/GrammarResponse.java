package com.example.aidictionary.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarResponse {

    private String originalText;

    private String correctedText;

    private String naturalText;

    private String translation;

    private List<GrammarErrorItem> errors;

    private List<GrammarPointItem> grammarPoints;

    private String note;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrammarErrorItem {
        private String wrong;
        private String correct;
        private String reason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrammarPointItem {
        private String pattern;
        private String meaning;
        private String example;
    }
}
