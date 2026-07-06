package com.example.aidictionary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrammarResponse {

    private String originalText;

    private String correctedText;

    private String naturalText;

    private String naturalVersion;

    private String translation;

    private Boolean isCorrect;

    private List<GrammarErrorItem> errors;

    private List<GrammarPointItem> grammarPoints;

    private String note;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrammarErrorItem {
        private String error;
        private String wrong;
        private String correction;
        private String correct;
        private String explanation;
        private String reason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrammarPointItem {
        private String pattern;
        private String explanation;
        private String meaning;
        private String example;
    }
}
