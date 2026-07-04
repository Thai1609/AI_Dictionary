package com.example.aidictionary.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "dictionary_entries")
public class DictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String word;

    @Column(name = "normalized_word")
    private String normalizedWord;

    @Column(name = "source_language")
    private String sourceLanguage;

    @Column(name = "target_language")
    private String targetLanguage;

    private String pronunciation;

    private String reading;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "search_keyword")
    private String searchKeyword;

    @Column(name = "normalized_search_keyword")
    private String normalizedSearchKeyword;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryMeaning> meanings = new ArrayList<>();

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryExample> examples = new ArrayList<>();

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DictionaryRelatedWord> relatedWords = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}