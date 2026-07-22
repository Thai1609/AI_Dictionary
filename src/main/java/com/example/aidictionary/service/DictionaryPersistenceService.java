package com.example.aidictionary.service;

import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.DictionaryCheckResponse;
import com.example.aidictionary.dto.DictionaryResponse;
import com.example.aidictionary.dto.GrammarHistoryResponse;
import com.example.aidictionary.dto.GrammarResponse;
import com.example.aidictionary.dto.SaveDictionaryRequest;
import com.example.aidictionary.dto.SaveDictionaryResponse;
import com.example.aidictionary.entity.DictionaryEntry;
import com.example.aidictionary.entity.DictionaryExample;
import com.example.aidictionary.entity.DictionaryMeaning;
import com.example.aidictionary.entity.DictionaryRelatedWord;
import com.example.aidictionary.entity.GrammarCheck;
import com.example.aidictionary.exception.BadRequestException;
import com.example.aidictionary.exception.ResourceNotFoundException;
import com.example.aidictionary.repository.DictionaryEntryRepository;
import com.example.aidictionary.repository.GrammarCheckRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DictionaryPersistenceService {

    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_HISTORY_RESULTS = 100;

    private final DictionaryEntryRepository dictionaryEntryRepository;
    private final GrammarCheckRepository grammarCheckRepository;

    /**
     * Tự khởi tạo ObjectMapper để service không phụ thuộc vào bean Jackson
     * do Spring quản lý. Lombok không đưa field đã khởi tạo này vào constructor.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Transactional(readOnly = true)
    public Optional<DictionaryResponse> findExistingWord(
            String word,
            String sourceLanguage,
            String targetLanguage
    ) {
        return findExistingEntity(word, sourceLanguage, targetLanguage)
                .map(this::convertToDictionaryResponse);
    }

    @Transactional
    public SaveDictionaryResponse saveDictionary(SaveDictionaryRequest request) {
        if (request == null || request.getDictionary() == null) {
            throw new BadRequestException("Dữ liệu lưu không hợp lệ.");
        }

        DictionaryResponse dictionary = request.getDictionary();
        if (isBlank(dictionary.getWord())) {
            throw new BadRequestException("Từ cần lưu không được để trống.");
        }

        String word = dictionary.getWord().trim();
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");
        String searchKeyword = isBlank(request.getSearchKeyword())
                ? word
                : request.getSearchKeyword().trim();

        Optional<DictionaryEntry> existingEntry = findExistingEntity(
                searchKeyword,
                sourceLanguage,
                targetLanguage
        );

        if (existingEntry.isEmpty()) {
            existingEntry = findExistingEntity(word, sourceLanguage, targetLanguage);
        }

        if (existingEntry.isPresent()) {
            DictionaryEntry existing = existingEntry.get();
            updateEntry(existing, dictionary, searchKeyword);
            DictionaryEntry saved = dictionaryEntryRepository.save(existing);
            return new SaveDictionaryResponse(
                    true,
                    false,
                    "Từ đã tồn tại, dữ liệu đã được đồng bộ.",
                    saved.getId()
            );
        }

        DictionaryEntry entry = createEntry(
                dictionary,
                word,
                searchKeyword,
                sourceLanguage,
                targetLanguage
        );
        DictionaryEntry saved = dictionaryEntryRepository.saveAndFlush(entry);

        return new SaveDictionaryResponse(
                true,
                true,
                "Lưu từ vào database thành công.",
                saved.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<DictionaryResponse> searchDictionary(
            String keyword,
            String sourceLanguage,
            String targetLanguage
    ) {
        if (isBlank(keyword)) {
            return List.of();
        }

        String cleanKeyword = keyword.trim();
        String normalizedKeyword = normalizeForSearch(cleanKeyword);
        if (normalizedKeyword.isEmpty()) {
            return List.of();
        }

        boolean filterByLanguage = !isBlank(sourceLanguage) || !isBlank(targetLanguage);
        String finalSourceLanguage = filterByLanguage
                ? defaultLanguage(sourceLanguage, "vi")
                : null;
        String finalTargetLanguage = filterByLanguage
                ? defaultLanguage(targetLanguage, "zh")
                : null;

        // Dung LinkedHashSet de loai trung nhung van giu dung thu tu xep hang tu PostgreSQL.
        java.util.LinkedHashSet<Long> rankedIds = new java.util.LinkedHashSet<>();

        List<Long> exactAndPrefixIds = filterByLanguage
                ? dictionaryEntryRepository.searchExactAndPrefixIdsByLanguage(
                        cleanKeyword,
                        normalizedKeyword,
                        finalSourceLanguage,
                        finalTargetLanguage,
                        MAX_SEARCH_RESULTS
                )
                : dictionaryEntryRepository.searchExactAndPrefixIds(
                        cleanKeyword,
                        normalizedKeyword,
                        MAX_SEARCH_RESULTS
                );

        rankedIds.addAll(exactAndPrefixIds);

        // Contains co the can scan nhieu dong, nen chi chay khi keyword du dai
        // va exact/prefix chua du 20 ket qua.
        if (rankedIds.size() < MAX_SEARCH_RESULTS && normalizedKeyword.length() >= 3) {
            List<Long> containsIds = filterByLanguage
                    ? dictionaryEntryRepository.searchContainsIdsByLanguage(
                            normalizedKeyword,
                            finalSourceLanguage,
                            finalTargetLanguage,
                            MAX_SEARCH_RESULTS
                    )
                    : dictionaryEntryRepository.searchContainsIds(
                            normalizedKeyword,
                            MAX_SEARCH_RESULTS
                    );

            for (Long id : containsIds) {
                if (rankedIds.size() >= MAX_SEARCH_RESULTS) {
                    break;
                }
                rankedIds.add(id);
            }
        }

        // Fuzzy fallback: bat loi go sai nhe, vi du "ninhao" / "nihap".
        // Chi chay voi keyword >= 3 ky tu de tranh ket qua nhieu va kem chinh xac.
        if (rankedIds.size() < MAX_SEARCH_RESULTS && normalizedKeyword.length() >= 3) {
            int remaining = MAX_SEARCH_RESULTS - rankedIds.size();
            List<Long> fuzzyIds = filterByLanguage
                    ? dictionaryEntryRepository.searchFuzzyIdsByLanguage(
                            normalizedKeyword,
                            finalSourceLanguage,
                            finalTargetLanguage,
                            remaining
                    )
                    : dictionaryEntryRepository.searchFuzzyIds(
                            normalizedKeyword,
                            remaining
                    );

            for (Long id : fuzzyIds) {
                if (rankedIds.size() >= MAX_SEARCH_RESULTS) {
                    break;
                }
                rankedIds.add(id);
            }
        }

        if (rankedIds.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>(rankedIds);
        Map<Long, Integer> positions = new HashMap<>();
        for (int index = 0; index < ids.size(); index++) {
            positions.put(ids.get(index), index);
        }

        List<DictionaryEntry> entries = dictionaryEntryRepository.findAllByIdIn(ids);
        entries.sort(Comparator.comparingInt(
                entry -> positions.getOrDefault(entry.getId(), Integer.MAX_VALUE)
        ));

        return entries.stream()
                .map(this::convertToDictionaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DictionaryResponse getDictionaryDetail(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("ID từ điển không hợp lệ.");
        }

        DictionaryEntry entry = dictionaryEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy từ trong database với id: " + id
                ));
        return convertToDictionaryResponse(entry);
    }

    @Transactional
    public void deleteDictionaryEntry(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("ID từ điển không hợp lệ.");
        }

        DictionaryEntry entry = dictionaryEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy từ trong database với id: " + id
                ));

        dictionaryEntryRepository.delete(entry);
        dictionaryEntryRepository.flush();
    }

    @Transactional(readOnly = true)
    public DictionaryCheckResponse checkWordExists(
            String word,
            String sourceLanguage,
            String targetLanguage
    ) {
        if (isBlank(word)) {
            throw new BadRequestException("Từ kiểm tra không được để trống.");
        }

        String finalSourceLanguage = defaultLanguage(sourceLanguage, "vi");
        String finalTargetLanguage = defaultLanguage(targetLanguage, "zh");

        Optional<DictionaryEntry> entry = findExistingEntity(
                word.trim(),
                finalSourceLanguage,
                finalTargetLanguage
        );

        return entry
                .map(value -> new DictionaryCheckResponse(
                        true,
                        "Từ đã tồn tại trong database.",
                        value.getId()
                ))
                .orElseGet(() -> new DictionaryCheckResponse(
                        false,
                        "Từ chưa tồn tại trong database.",
                        null
                ));
    }

    @Transactional
    public void saveGrammarCheck(
            String originalText,
            String sourceLanguage,
            String targetLanguage,
            AnalyzeResponse response
    ) {
        if (response == null || response.getGrammar() == null) {
            return;
        }

        GrammarResponse grammar = response.getGrammar();
        GrammarCheck check = new GrammarCheck();
        check.setOriginalText(defaultText(grammar.getOriginalText(), originalText));
        check.setCorrectedText(grammar.getCorrectedText());
        check.setNaturalText(defaultText(grammar.getNaturalText(), grammar.getNaturalVersion()));
        check.setTranslation(grammar.getTranslation());
        check.setCorrect(grammar.getIsCorrect());
        check.setSourceLanguage(defaultLanguage(sourceLanguage, "vi"));
        check.setTargetLanguage(defaultLanguage(targetLanguage, "zh"));
        check.setNote(grammar.getNote());
        check.setResultJson(toJson(grammar));
        grammarCheckRepository.save(check);
    }

    @Transactional(readOnly = true)
    public List<GrammarHistoryResponse> getGrammarHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_HISTORY_RESULTS));
        return grammarCheckRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toGrammarHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GrammarHistoryResponse getGrammarHistoryDetail(Long id) {
        GrammarCheck check = grammarCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch sử kiểm tra ngữ pháp với id: " + id
                ));
        return toGrammarHistoryResponse(check);
    }

    private Optional<DictionaryEntry> findExistingEntity(
            String word,
            String sourceLanguage,
            String targetLanguage
    ) {
        String normalized = normalizeForSearch(word);

        Optional<DictionaryEntry> bySearchKeyword =
                dictionaryEntryRepository.findFirstByNormalizedSearchKeywordAndSourceLanguageAndTargetLanguage(
                        normalized,
                        sourceLanguage,
                        targetLanguage
                );
        if (bySearchKeyword.isPresent()) {
            return bySearchKeyword;
        }

        Optional<DictionaryEntry> byNormalizedWord =
                dictionaryEntryRepository.findFirstByNormalizedWordAndSourceLanguageAndTargetLanguage(
                        normalized,
                        sourceLanguage,
                        targetLanguage
                );
        if (byNormalizedWord.isPresent()) {
            return byNormalizedWord;
        }

        return dictionaryEntryRepository.findFirstByWordIgnoreCaseAndSourceLanguageAndTargetLanguage(
                word.trim(),
                sourceLanguage,
                targetLanguage
        );
    }

    private DictionaryEntry createEntry(
            DictionaryResponse dictionary,
            String word,
            String searchKeyword,
            String sourceLanguage,
            String targetLanguage
    ) {
        DictionaryEntry entry = new DictionaryEntry();
        entry.setWord(word);
        entry.setNormalizedWord(normalizeForSearch(word));
        entry.setSourceLanguage(sourceLanguage);
        entry.setTargetLanguage(targetLanguage);
        entry.setPronunciation(dictionary.getPronunciation());
        entry.setReading(dictionary.getReading());
        entry.setPartOfSpeech(dictionary.getPartOfSpeech());
        entry.setNote(dictionary.getNote());
        entry.setTranslationsJson(toJson(dictionary.getTranslationGroups()));
        entry.setRecommendationJson(toJson(dictionary.getRecommendation()));
        entry.setSearchKeyword(searchKeyword);
        entry.setNormalizedSearchKeyword(normalizeForSearch(searchKeyword));

        replaceMeanings(entry, dictionary.getMeanings());
        replaceExamples(entry, dictionary.getExamples());
        replaceRelatedWords(entry, dictionary.getRelatedWords());
        entry.setSearchText(buildSearchText(entry));
        return entry;
    }

    private void updateEntry(
            DictionaryEntry entry,
            DictionaryResponse dictionary,
            String searchKeyword
    ) {
        if (!isBlank(dictionary.getWord())) {
            entry.setWord(dictionary.getWord().trim());
            entry.setNormalizedWord(normalizeForSearch(dictionary.getWord()));
        }

        entry.setSearchKeyword(searchKeyword);
        entry.setNormalizedSearchKeyword(normalizeForSearch(searchKeyword));
        entry.setPronunciation(dictionary.getPronunciation());
        entry.setReading(dictionary.getReading());
        entry.setPartOfSpeech(dictionary.getPartOfSpeech());
        entry.setNote(dictionary.getNote());

        if (dictionary.getTranslationGroups() != null) {
            entry.setTranslationsJson(toJson(dictionary.getTranslationGroups()));
        }
        if (dictionary.getRecommendation() != null) {
            entry.setRecommendationJson(toJson(dictionary.getRecommendation()));
        }

        if (dictionary.getMeanings() != null) {
            replaceMeanings(entry, dictionary.getMeanings());
        }
        if (dictionary.getExamples() != null) {
            replaceExamples(entry, dictionary.getExamples());
        }
        if (dictionary.getRelatedWords() != null) {
            replaceRelatedWords(entry, dictionary.getRelatedWords());
        }
        entry.setSearchText(buildSearchText(entry));
    }

    private void replaceMeanings(DictionaryEntry entry, List<String> values) {
        entry.getMeanings().clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            DictionaryMeaning meaning = new DictionaryMeaning();
            meaning.setMeaning(value.trim());
            meaning.setEntry(entry);
            entry.getMeanings().add(meaning);
        }
    }

    private void replaceExamples(
            DictionaryEntry entry,
            List<DictionaryResponse.ExampleItem> values
    ) {
        entry.getExamples().clear();
        if (values == null) {
            return;
        }
        for (DictionaryResponse.ExampleItem value : values) {
            if (value == null) {
                continue;
            }
            DictionaryExample example = new DictionaryExample();
            example.setExampleSentence(value.getSentence());
            example.setExampleReading(value.getReading());
            example.setExampleTranslation(value.getTranslation());
            example.setEntry(entry);
            entry.getExamples().add(example);
        }
    }

    private void replaceRelatedWords(DictionaryEntry entry, List<String> values) {
        entry.getRelatedWords().clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            DictionaryRelatedWord relatedWord = new DictionaryRelatedWord();
            relatedWord.setRelatedWord(value.trim());
            relatedWord.setEntry(entry);
            entry.getRelatedWords().add(relatedWord);
        }
    }

    private String buildSearchText(DictionaryEntry entry) {
        StringBuilder builder = new StringBuilder();
        append(builder, entry.getSearchKeyword());
        append(builder, entry.getNormalizedSearchKeyword());
        append(builder, entry.getWord());
        append(builder, entry.getNormalizedWord());
        append(builder, entry.getPronunciation());
        append(builder, entry.getReading());
        append(builder, entry.getPartOfSpeech());
        append(builder, entry.getNote());

        entry.getMeanings().forEach(value -> append(builder, value.getMeaning()));
        entry.getExamples().forEach(value -> {
            append(builder, value.getExampleSentence());
            append(builder, value.getExampleReading());
            append(builder, value.getExampleTranslation());
        });
        entry.getRelatedWords().forEach(value -> append(builder, value.getRelatedWord()));

        List<DictionaryResponse.TranslationGroup> groups =
                fromJsonList(entry.getTranslationsJson(), DictionaryResponse.TranslationGroup.class);
        groups.forEach(group -> {
            if (group == null) {
                return;
            }
            append(builder, group.getPartOfSpeech());
            if (group.getItems() == null) {
                return;
            }
            group.getItems().forEach(item -> {
                if (item == null) {
                    return;
                }
                append(builder, item.getWord());
                append(builder, item.getPronunciation());
                append(builder, item.getReading());
                append(builder, item.getPartOfSpeech());
                append(builder, item.getUsage());
                append(builder, item.getNote());
                if (item.getMeanings() != null) {
                    item.getMeanings().forEach(value -> append(builder, value));
                }
                if (item.getRelatedWords() != null) {
                    item.getRelatedWords().forEach(value -> append(builder, value));
                }
                if (item.getExamples() != null) {
                    item.getExamples().forEach(example -> {
                        if (example != null) {
                            append(builder, example.getSentence());
                            append(builder, example.getReading());
                            append(builder, example.getTranslation());
                        }
                    });
                }
            });
        });

        return normalizeForSearch(builder.toString());
    }

    private void append(StringBuilder builder, String value) {
        if (!isBlank(value)) {
            builder.append(' ').append(value);
        }
    }

    private DictionaryResponse convertToDictionaryResponse(DictionaryEntry entry) {
        DictionaryResponse response = new DictionaryResponse();
        response.setWord(entry.getWord());
        response.setPronunciation(entry.getPronunciation());
        response.setReading(entry.getReading());
        response.setPartOfSpeech(entry.getPartOfSpeech());
        response.setNote(entry.getNote());

        response.setMeanings(entry.getMeanings().stream()
                .filter(value -> value != null && !isBlank(value.getMeaning()))
                .map(DictionaryMeaning::getMeaning)
                .toList());

        List<DictionaryResponse.ExampleItem> examples = new ArrayList<>();
        for (DictionaryExample value : entry.getExamples()) {
            DictionaryResponse.ExampleItem item = new DictionaryResponse.ExampleItem();
            item.setSentence(value.getExampleSentence());
            item.setReading(value.getExampleReading());
            item.setTranslation(value.getExampleTranslation());
            examples.add(item);
        }
        response.setExamples(examples);

        response.setRelatedWords(entry.getRelatedWords().stream()
                .filter(value -> value != null && !isBlank(value.getRelatedWord()))
                .map(DictionaryRelatedWord::getRelatedWord)
                .toList());

        response.setTranslationGroups(
                fromJsonList(entry.getTranslationsJson(), DictionaryResponse.TranslationGroup.class)
        );
        response.setRecommendation(
                fromJson(entry.getRecommendationJson(), DictionaryResponse.Recommendation.class)
        );
        return response;
    }

    private GrammarHistoryResponse toGrammarHistoryResponse(GrammarCheck check) {
        return new GrammarHistoryResponse(
                check.getId(),
                check.getOriginalText(),
                check.getCorrectedText(),
                check.getNaturalText(),
                check.getTranslation(),
                check.getCorrect(),
                check.getSourceLanguage(),
                check.getTargetLanguage(),
                check.getNote(),
                check.getResultJson(),
                check.getCreatedAt()
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> valueType) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private <T> List<T> fromJsonList(String json, Class<T> elementType) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)
            );
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String toJson(GrammarResponse grammar) {
        try {
            return objectMapper.writeValueAsString(grammar);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String defaultLanguage(String language, String defaultValue) {
        return isBlank(language) ? defaultValue : language.trim().toLowerCase();
    }

    public String normalizeForSearch(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .trim()
                .toLowerCase()
                .replace("đ", "d")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
