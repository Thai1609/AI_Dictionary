package com.example.aidictionary.repository;

import com.example.aidictionary.entity.DictionaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    Optional<DictionaryEntry> findFirstByNormalizedSearchKeywordAndSourceLanguageAndTargetLanguage(
            String normalizedSearchKeyword,
            String sourceLanguage,
            String targetLanguage
    );

    List<DictionaryEntry> findAllByIdIn(Collection<Long> ids);

    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE de.source_language = :sourceLanguage
              AND de.target_language = :targetLanguage
              AND (
                    lower(de.word) = lower(:keyword)
                 OR de.normalized_word = :normalizedKeyword
                 OR de.normalized_search_keyword = :normalizedKeyword
                 OR de.normalized_word LIKE CONCAT(:normalizedKeyword, '%')
                 OR de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%')
                 OR (
                      char_length(:normalizedKeyword) >= 3
                      AND (
                          de.search_text ILIKE CONCAT('%', :normalizedKeyword, '%')
                          OR de.search_text % :normalizedKeyword
                      )
                 )
              )
            ORDER BY
                CASE
                    WHEN lower(de.word) = lower(:keyword) THEN 100
                    WHEN de.normalized_word = :normalizedKeyword THEN 98
                    WHEN de.normalized_search_keyword = :normalizedKeyword THEN 96
                    WHEN de.normalized_word LIKE CONCAT(:normalizedKeyword, '%') THEN 90
                    WHEN de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%') THEN 88
                    WHEN de.search_text ILIKE CONCAT('%', :normalizedKeyword, '%') THEN 75
                    ELSE 50
                END DESC,
                GREATEST(
                    similarity(COALESCE(de.normalized_word, ''), :normalizedKeyword),
                    similarity(COALESCE(de.normalized_search_keyword, ''), :normalizedKeyword),
                    similarity(COALESCE(de.search_text, ''), :normalizedKeyword)
                ) DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchEntryIdsByLanguage(
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage,
            @Param("limit") int limit
    );

    /**
     * PostgreSQL thực hiện việc lọc, chấm điểm và LIMIT trước khi Java tải entity.
     * - B-tree phục vụ exact/prefix.
     * - pg_trgm GIN phục vụ contains/fuzzy khi keyword dài từ 3 ký tự.
     */
    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE lower(de.word) = lower(:keyword)
               OR de.normalized_word = :normalizedKeyword
               OR de.normalized_search_keyword = :normalizedKeyword
               OR de.normalized_word LIKE CONCAT(:normalizedKeyword, '%')
               OR de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%')
               OR (
                    char_length(:normalizedKeyword) >= 3
                    AND (
                        de.search_text ILIKE CONCAT('%', :normalizedKeyword, '%')
                        OR de.search_text % :normalizedKeyword
                    )
               )
            ORDER BY
                CASE
                    WHEN lower(de.word) = lower(:keyword) THEN 100
                    WHEN de.normalized_word = :normalizedKeyword THEN 98
                    WHEN de.normalized_search_keyword = :normalizedKeyword THEN 96
                    WHEN de.normalized_word LIKE CONCAT(:normalizedKeyword, '%') THEN 90
                    WHEN de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%') THEN 88
                    WHEN de.search_text ILIKE CONCAT('%', :normalizedKeyword, '%') THEN 75
                    ELSE 50
                END DESC,
                GREATEST(
                    similarity(COALESCE(de.normalized_word, ''), :normalizedKeyword),
                    similarity(COALESCE(de.normalized_search_keyword, ''), :normalizedKeyword),
                    similarity(COALESCE(de.search_text, ''), :normalizedKeyword)
                ) DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchEntryIds(
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("limit") int limit
    );
}
