package com.example.aidictionary.dto;

import lombok.Data;

import java.util.List;

@Data
public class DictionaryResponse {

    private String word;
    private String originalSentence;
    private String pronunciation;
    private String reading;
    private String partOfSpeech;
    private String translation;
    private String naturalVersion;

    private List<String> meanings;
    private List<ExampleItem> examples;
    private List<KeyPhraseItem> keyPhrases;
    private List<GrammarPointItem> grammarPoints;
    private List<String> relatedWords;

    private String note;

    @Data
    public static class ExampleItem {
        private String sentence;
        private String reading;
        private String translation;
    }

    @Data
    public static class KeyPhraseItem {
        private String phrase;
        private String reading;
        private String meaning;
        private String note;
    }
 
    @Data
    public static class GrammarPointItem {
        private String pattern;
        private String meaning;
        private String explanation;
        private String example;
    }
}
