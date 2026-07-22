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

    /*
     * Buoc 1: tim exact/prefix. Cau query nay chi dung B-tree, khong phu thuoc pg_trgm.
     */
    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE lower(de.word) = lower(:keyword)
               OR de.normalized_word = :normalizedKeyword
               OR de.normalized_search_keyword = :normalizedKeyword
               OR de.normalized_word LIKE CONCAT(:normalizedKeyword, '%')
               OR de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%')
            ORDER BY
                CASE
                    WHEN lower(de.word) = lower(:keyword) THEN 100
                    WHEN de.normalized_word = :normalizedKeyword THEN 98
                    WHEN de.normalized_search_keyword = :normalizedKeyword THEN 96
                    WHEN de.normalized_word LIKE CONCAT(:normalizedKeyword, '%') THEN 90
                    WHEN de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%') THEN 88
                    ELSE 0
                END DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchExactAndPrefixIds(
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("limit") int limit
    );

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
              )
            ORDER BY
                CASE
                    WHEN lower(de.word) = lower(:keyword) THEN 100
                    WHEN de.normalized_word = :normalizedKeyword THEN 98
                    WHEN de.normalized_search_keyword = :normalizedKeyword THEN 96
                    WHEN de.normalized_word LIKE CONCAT(:normalizedKeyword, '%') THEN 90
                    WHEN de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%') THEN 88
                    ELSE 0
                END DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchExactAndPrefixIdsByLanguage(
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage,
            @Param("limit") int limit
    );

    /*
     * Buoc 2: contains fallback. Chi goi khi exact/prefix chua du ket qua va keyword >= 3 ky tu.
     * Khong dung similarity() hay toan tu %, nen khong can extension pg_trgm.
     */
    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE de.search_text LIKE CONCAT('%', :normalizedKeyword, '%')
               OR de.normalized_word LIKE CONCAT('%', :normalizedKeyword, '%')
               OR de.normalized_search_keyword LIKE CONCAT('%', :normalizedKeyword, '%')
            ORDER BY
                CASE
                    WHEN de.normalized_word LIKE CONCAT(:normalizedKeyword, '%') THEN 90
                    WHEN de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%') THEN 88
                    WHEN de.normalized_word LIKE CONCAT('%', :normalizedKeyword, '%') THEN 80
                    WHEN de.normalized_search_keyword LIKE CONCAT('%', :normalizedKeyword, '%') THEN 78
                    ELSE 70
                END DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchContainsIds(
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE de.source_language = :sourceLanguage
              AND de.target_language = :targetLanguage
              AND (
                    de.search_text LIKE CONCAT('%', :normalizedKeyword, '%')
                 OR de.normalized_word LIKE CONCAT('%', :normalizedKeyword, '%')
                 OR de.normalized_search_keyword LIKE CONCAT('%', :normalizedKeyword, '%')
              )
            ORDER BY
                CASE
                    WHEN de.normalized_word LIKE CONCAT(:normalizedKeyword, '%') THEN 90
                    WHEN de.normalized_search_keyword LIKE CONCAT(:normalizedKeyword, '%') THEN 88
                    WHEN de.normalized_word LIKE CONCAT('%', :normalizedKeyword, '%') THEN 80
                    WHEN de.normalized_search_keyword LIKE CONCAT('%', :normalizedKeyword, '%') THEN 78
                    ELSE 70
                END DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchContainsIdsByLanguage(
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage,
            @Param("limit") int limit
    );
    /*
     * Buoc 3: fuzzy fallback bang pg_trgm. Chi goi khi exact/prefix/contains chua du ket qua.
     * word_similarity phu hop hon similarity khi search_text la chuoi dai.
     */
    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE :normalizedKeyword <% de.search_text
               OR de.normalized_word % :normalizedKeyword
               OR de.normalized_search_keyword % :normalizedKeyword
            ORDER BY
                GREATEST(
                    similarity(COALESCE(de.normalized_word, ''), :normalizedKeyword),
                    similarity(COALESCE(de.normalized_search_keyword, ''), :normalizedKeyword),
                    word_similarity(:normalizedKeyword, COALESCE(de.search_text, ''))
                ) DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchFuzzyIds(
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT de.id
            FROM dictionary_entries de
            WHERE de.source_language = :sourceLanguage
              AND de.target_language = :targetLanguage
              AND (
                    :normalizedKeyword <% de.search_text
                 OR de.normalized_word % :normalizedKeyword
                 OR de.normalized_search_keyword % :normalizedKeyword
              )
            ORDER BY
                GREATEST(
                    similarity(COALESCE(de.normalized_word, ''), :normalizedKeyword),
                    similarity(COALESCE(de.normalized_search_keyword, ''), :normalizedKeyword),
                    word_similarity(:normalizedKeyword, COALESCE(de.search_text, ''))
                ) DESC,
                de.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> searchFuzzyIdsByLanguage(
            @Param("normalizedKeyword") String normalizedKeyword,
            @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage,
            @Param("limit") int limit
    );

}
