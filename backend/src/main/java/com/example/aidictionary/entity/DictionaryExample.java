package com.example.aidictionary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "dictionary_examples")
public class DictionaryExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "example_sentence", columnDefinition = "TEXT")
    private String exampleSentence;

    /**
     * Pinyin hoặc cách đọc của câu ví dụ.
     * Ví dụ tiếng Trung: nǐ hǎo, wǒ shì yuè nán rén
     */
    @Column(name = "example_reading", columnDefinition = "TEXT")
    private String exampleReading;

    @Column(name = "example_translation", columnDefinition = "TEXT")
    private String exampleTranslation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    private DictionaryEntry entry;
}