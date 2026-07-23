package com.example.aidictionary.service;

import com.example.aidictionary.dto.DictionaryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelatedWordSanitizerTest {

    @Test
    void removesMainWordsAndDuplicatesFromRelatedWords() {
        DictionaryResponse dictionary = new DictionaryResponse();
        dictionary.setWord("保護");
        dictionary.setRelatedWords(List.of(related(" 保護 ", "bǎo hù", "bảo vệ"), related("守護", "shǒu hù", "bảo vệ, gìn giữ"), related("守護", "shǒu hù", "trùng"), related("防護", "fáng hù", "phòng hộ")));

        DictionaryResponse.Recommendation recommendation =
                new DictionaryResponse.Recommendation();
        recommendation.setDefaultWord("保護");
        dictionary.setRecommendation(recommendation);

        DictionaryResponse.TranslationOption option = option(
                "守護",
                List.of("bảo vệ", "保護", "守護", "防守", "防守")
        );

        DictionaryResponse.TranslationGroup group = group(List.of(option));
        dictionary.setTranslationGroups(List.of(group));

        RelatedWordSanitizer.sanitize(dictionary, "bảo vệ");

        assertEquals(List.of("守護", "防護"), dictionary.getRelatedWords().stream().map(DictionaryResponse.RelatedWordItem::getWord).toList());
        assertEquals("shǒu hù", dictionary.getRelatedWords().get(0).getReading());
        assertEquals("bảo vệ, gìn giữ", dictionary.getRelatedWords().get(0).getMeaning());
        assertEquals(List.of("防守"), option.getRelatedWords());
    }

    @Test
    void removesDefaultWordAndDuplicateItemsFromTranslationGroups() {
        DictionaryResponse dictionary = new DictionaryResponse();
        dictionary.setWord("飛機");

        DictionaryResponse.Recommendation recommendation =
                new DictionaryResponse.Recommendation();
        recommendation.setDefaultWord("飛機");
        dictionary.setRecommendation(recommendation);

        DictionaryResponse.TranslationOption duplicatedMainWord =
                option(" 飛機 ", List.of("機場"));
        DictionaryResponse.TranslationOption helicopter =
                option("直升機", List.of("飛機", "旋翼機"));
        DictionaryResponse.TranslationOption duplicatedHelicopter =
                option(" 直升機 ", List.of("飛行器"));
        DictionaryResponse.TranslationOption fighter =
                option("戰鬥機", List.of("飛機", "軍用飛機"));

        DictionaryResponse.TranslationGroup nounGroup = group(List.of(
                duplicatedMainWord,
                helicopter,
                duplicatedHelicopter
        ));
        nounGroup.setPartOfSpeech("danh từ");

        DictionaryResponse.TranslationGroup secondGroup = group(List.of(fighter));
        secondGroup.setPartOfSpeech("danh từ khác");

        dictionary.setTranslationGroups(List.of(nounGroup, secondGroup));

        RelatedWordSanitizer.sanitize(dictionary, "máy bay");

        assertEquals(2, dictionary.getTranslationGroups().size());
        assertEquals(List.of("直升機"), dictionary.getTranslationGroups().get(0)
                .getItems().stream().map(DictionaryResponse.TranslationOption::getWord).toList());
        assertEquals(List.of("戰鬥機"), dictionary.getTranslationGroups().get(1)
                .getItems().stream().map(DictionaryResponse.TranslationOption::getWord).toList());
        assertEquals(List.of("旋翼機"), helicopter.getRelatedWords());
        assertEquals(List.of("軍用飛機"), fighter.getRelatedWords());
    }

    @Test
    void removesTranslationGroupWhenAllItemsAreInvalid() {
        DictionaryResponse dictionary = new DictionaryResponse();
        dictionary.setWord("飛機");
        dictionary.setTranslationGroups(List.of(group(List.of(option("飛機", List.of())))));

        RelatedWordSanitizer.sanitize(dictionary, "máy bay");

        assertEquals(List.of(), dictionary.getTranslationGroups());
    }

    private static DictionaryResponse.RelatedWordItem related(
            String word,
            String reading,
            String meaning
    ) {
        DictionaryResponse.RelatedWordItem item =
                new DictionaryResponse.RelatedWordItem();
        item.setWord(word);
        item.setReading(reading);
        item.setMeaning(meaning);
        return item;
    }

    private static DictionaryResponse.TranslationOption option(
            String word,
            List<String> relatedWords
    ) {
        DictionaryResponse.TranslationOption option =
                new DictionaryResponse.TranslationOption();
        option.setWord(word);
        option.setRelatedWords(relatedWords);
        return option;
    }

    private static DictionaryResponse.TranslationGroup group(
            List<DictionaryResponse.TranslationOption> items
    ) {
        DictionaryResponse.TranslationGroup group =
                new DictionaryResponse.TranslationGroup();
        group.setItems(items);
        return group;
    }
}
