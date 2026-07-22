package com.example.aidictionary.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "dictionary_entries",
        indexes = {
                @Index(
                        name = "ix_dictionary_lookup",
                        columnList = "normalized_search_keyword,source_language,target_language"
                ),
                @Index(
                        name = "ix_dictionary_normalized_word",
                        columnList = "normalized_word,source_language,target_language"
                )
        }
)
public class DictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String word;

    @Column(name = "normalized_word", length = 500)
    private String normalizedWord;

    @Column(name = "source_language", length = 20)
    private String sourceLanguage;

    @Column(name = "target_language", length = 20)
    private String targetLanguage;

    @Column(length = 500)
    private String pronunciation;

    @Column(length = 500)
    private String reading;

    @Column(name = "part_of_speech", length = 100)
    private String partOfSpeech;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "translations_json", columnDefinition = "TEXT")
    private String translationsJson;

    @Column(name = "recommendation_json", columnDefinition = "TEXT")
    private String recommendationJson;

    @Column(name = "search_keyword", length = 500)
    private String searchKeyword;

    @Column(name = "normalized_search_keyword", length = 500)
    private String normalizedSearchKeyword;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @BatchSize(size = 50)
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryMeaning> meanings = new ArrayList<>();

    @BatchSize(size = 50)
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryExample> examples = new ArrayList<>();

    @BatchSize(size = 50)
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryRelatedWord> relatedWords = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
