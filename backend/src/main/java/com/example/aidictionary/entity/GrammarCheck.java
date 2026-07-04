package com.example.aidictionary.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "grammar_checks")
public class GrammarCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String correctedText;

    @Column(columnDefinition = "TEXT")
    private String naturalText;

    @Column(columnDefinition = "TEXT")
    private String translation;

    private String sourceLanguage;

    private String targetLanguage;

    private LocalDateTime createdAt = LocalDateTime.now();
}