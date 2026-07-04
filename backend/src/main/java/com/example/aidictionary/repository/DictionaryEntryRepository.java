package com.example.aidictionary.repository;

import com.example.aidictionary.entity.DictionaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryEntryRepository extends JpaRepository<DictionaryEntry, Long> {

    Optional<DictionaryEntry> findFirstByWordIgnoreCaseAndSourceLanguageAndTargetLanguage(
            String word,
            String sourceLanguage,
            String targetLanguage
    );

    Optional<DictionaryEntry> findFirstByNormalizedWordAndSourceLanguageAndTargetLanguage(
            String normalizedWord,
            String sourceLanguage,
            String targetLanguage
    );

    List<DictionaryEntry> findAllByOrderByIdDesc();
}