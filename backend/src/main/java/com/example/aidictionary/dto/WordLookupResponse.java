package com.example.aidictionary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WordLookupResponse {

    private String query;
    private String sourceLanguage;
    private String targetLanguage;
    private List<WordOption> options;
    private String message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WordOption {
        private String word;
        private String pronunciation;
        private String reading;
        private String partOfSpeech;
        private List<String> meanings;
        private String usage;
        private boolean recommended;
        private String reason;
    }
}
