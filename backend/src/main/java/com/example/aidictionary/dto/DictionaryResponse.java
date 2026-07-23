package com.example.aidictionary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DictionaryResponse {

    @Size(max = 500, message = "Từ không được vượt quá 500 ký tự.")
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
    private List<RelatedWordItem> relatedWords;

    /** Các bản dịch được nhóm theo loại từ. */
    private List<TranslationGroup> translationGroups;

    /** Từ được đề xuất dùng mặc định và lý do chọn. */
    private Recommendation recommendation;

    private String note;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExampleItem {
        private String sentence;
        private String reading;
        private String translation;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelatedWordItem {
        private String word;
        /** Pinyin có dấu khi từ liên quan là tiếng Trung. */
        private String reading;
        /** Nghĩa ngắn gọn bằng tiếng Việt. */
        private String meaning;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TranslationGroup {
        private String partOfSpeech;
        private List<TranslationOption> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TranslationOption {
        private String word;
        private String pronunciation;
        private String reading;
        private String partOfSpeech;
        private List<String> meanings;
        private String usage;
        private List<ExampleItem> examples;
        private List<String> relatedWords;
        private String note;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Recommendation {
        private String defaultWord;
        private String partOfSpeech;
        private String reason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeyPhraseItem {
        private String phrase;
        private String reading;
        private String meaning;
        private String note;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrammarPointItem {
        private String pattern;
        private String explanation;
        private String meaning;
        private String example;
    }
}
