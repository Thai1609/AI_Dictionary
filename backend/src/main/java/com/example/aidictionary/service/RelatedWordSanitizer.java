package com.example.aidictionary.service;

import com.example.aidictionary.dto.DictionaryResponse;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chuẩn hóa dữ liệu từ liên quan và các phương án dịch do AI trả về.
 *
 * <p>Prompt chỉ giúp giảm dữ liệu sai. Lớp này là lớp bảo vệ cuối cùng trước
 * khi dữ liệu được trả về frontend hoặc lưu xuống database.</p>
 */
final class RelatedWordSanitizer {

    private RelatedWordSanitizer() {
    }

    static void sanitize(DictionaryResponse dictionary, String searchedWord) {
        if (dictionary == null) {
            return;
        }

        Set<String> commonExclusions = new HashSet<>();
        addNormalized(commonExclusions, searchedWord);
        addNormalized(commonExclusions, dictionary.getWord());

        if (dictionary.getRecommendation() != null) {
            addNormalized(
                    commonExclusions,
                    dictionary.getRecommendation().getDefaultWord()
            );
        }

        dictionary.setRelatedWords(filterRelatedWordItems(
                dictionary.getRelatedWords(),
                commonExclusions
        ));

        sanitizeTranslationGroups(dictionary, commonExclusions);
    }

    /**
     * translationGroups chỉ chứa các phương án khác với từ mặc định.
     * Đồng thời loại item rỗng, item trùng từ chính và item bị lặp giữa các nhóm.
     */
    private static void sanitizeTranslationGroups(
            DictionaryResponse dictionary,
            Set<String> commonExclusions
    ) {
        if (dictionary.getTranslationGroups() == null) {
            return;
        }

        List<DictionaryResponse.TranslationGroup> cleanGroups = new ArrayList<>();
        Set<String> seenOptionWords = new LinkedHashSet<>();

        for (DictionaryResponse.TranslationGroup group : dictionary.getTranslationGroups()) {
            if (group == null || group.getItems() == null) {
                continue;
            }

            List<DictionaryResponse.TranslationOption> cleanItems = new ArrayList<>();

            for (DictionaryResponse.TranslationOption item : group.getItems()) {
                if (item == null) {
                    continue;
                }

                String normalizedItemWord = normalize(item.getWord());
                if (normalizedItemWord.isEmpty()
                        || commonExclusions.contains(normalizedItemWord)
                        || !seenOptionWords.add(normalizedItemWord)) {
                    continue;
                }

                item.setWord(item.getWord().trim());

                Set<String> itemExclusions = new HashSet<>(commonExclusions);
                itemExclusions.add(normalizedItemWord);
                item.setRelatedWords(filterRelatedWordStrings(
                        item.getRelatedWords(),
                        itemExclusions
                ));

                cleanItems.add(item);
            }

            if (!cleanItems.isEmpty()) {
                group.setItems(cleanItems);
                cleanGroups.add(group);
            }
        }

        dictionary.setTranslationGroups(cleanGroups);
    }

    private static List<DictionaryResponse.RelatedWordItem> filterRelatedWordItems(
            List<DictionaryResponse.RelatedWordItem> values,
            Set<String> exclusions
    ) {
        if (values == null) {
            return null;
        }
        if (values.isEmpty()) {
            return List.of();
        }

        List<DictionaryResponse.RelatedWordItem> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (DictionaryResponse.RelatedWordItem value : values) {
            if (value == null || value.getWord() == null || value.getWord().isBlank()) {
                continue;
            }

            String cleanWord = value.getWord().trim();
            String normalizedWord = normalize(cleanWord);
            if (normalizedWord.isEmpty()
                    || exclusions.contains(normalizedWord)
                    || !seen.add(normalizedWord)) {
                continue;
            }

            value.setWord(cleanWord);
            value.setReading(trimToNull(value.getReading()));
            value.setMeaning(trimToNull(value.getMeaning()));
            result.add(value);
        }

        return result;
    }

    private static List<String> filterRelatedWordStrings(
            List<String> values,
            Set<String> exclusions
    ) {
        if (values == null) {
            return null;
        }
        if (values.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            String cleanValue = value.trim();
            String normalizedValue = normalize(cleanValue);
            if (normalizedValue.isEmpty()
                    || exclusions.contains(normalizedValue)
                    || !seen.add(normalizedValue)) {
                continue;
            }

            result.add(cleanValue);
        }

        return result;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void addNormalized(Set<String> values, String value) {
        String normalizedValue = normalize(value);
        if (!normalizedValue.isEmpty()) {
            values.add(normalizedValue);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }
}
