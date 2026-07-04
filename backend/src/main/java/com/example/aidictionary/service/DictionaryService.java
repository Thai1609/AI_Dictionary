package com.example.aidictionary.service;

import com.example.aidictionary.dto.AnalyzeRequest;
import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.DictionaryCheckResponse;
import com.example.aidictionary.dto.DictionaryResponse;
import com.example.aidictionary.dto.SaveDictionaryRequest;
import com.example.aidictionary.dto.SaveDictionaryResponse;
import com.example.aidictionary.entity.DictionaryEntry;
import com.example.aidictionary.entity.DictionaryExample;
import com.example.aidictionary.entity.DictionaryMeaning;
import com.example.aidictionary.entity.DictionaryRelatedWord;
import com.example.aidictionary.repository.DictionaryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final AiService aiService;
    private final DictionaryEntryRepository dictionaryEntryRepository;

    /**
     * Analyze:
     * - Nếu là word: kiểm tra DB trước.
     * - Nếu DB có: trả dữ liệu DB.
     * - Nếu DB chưa có: gọi AI.
     */
    @Transactional(readOnly = true)
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        if (request == null) {
            return AnalyzeResponse.builder()
                    .type("word")
                    .message("Request không hợp lệ.")
                    .build();
        }

        String text = request.getText();

        if (isBlank(text)) {
            return AnalyzeResponse.builder()
                    .type("word")
                    .message("Nội dung tra cứu không được để trống.")
                    .build();
        }

        text = text.trim();

        String mode = detectMode(request.getMode(), text);
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");

        if ("word".equalsIgnoreCase(mode)) {
            Optional<DictionaryEntry> existingEntry = findExistingWord(
                    text,
                    sourceLanguage,
                    targetLanguage
            );

            if (existingEntry.isPresent()) {
                return AnalyzeResponse.builder()
                        .type("word")
                        .dictionary(convertToDictionaryResponse(existingEntry.get()))
                        .message("Lấy dữ liệu từ database.")
                        .build();
            }

            return aiService.analyzeWord(text, sourceLanguage, targetLanguage);
        }

        if ("sentence".equalsIgnoreCase(mode)) {
            return aiService.analyzeSentence(text, sourceLanguage, targetLanguage);
        }

        if ("grammar".equalsIgnoreCase(mode)) {
            return aiService.checkGrammar(text, sourceLanguage, targetLanguage);
        }

        return AnalyzeResponse.builder()
                .type(mode)
                .message("Mode không hợp lệ: " + mode)
                .build();
    }

    /**
     * Lưu từ vào database.
     *
     * Lưu ý:
     * - word là từ AI trả về, ví dụ: 羊肉
     * - searchKeyword là keyword user nhập ban đầu, ví dụ: thịt cừu
     * - note lưu vào dictionary_entries.note
     * - relatedWords lưu vào dictionary_related_words
     */
    @Transactional
    public SaveDictionaryResponse saveDictionary(SaveDictionaryRequest request) {
        if (request == null || request.getDictionary() == null) {
            return new SaveDictionaryResponse(false, "Dữ liệu lưu không hợp lệ.", null);
        }

        DictionaryResponse dictionary = request.getDictionary();

        if (isBlank(dictionary.getWord())) {
            return new SaveDictionaryResponse(false, "Từ cần lưu không được để trống.", null);
        }

        String word = dictionary.getWord().trim();
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");

        String searchKeyword = request.getSearchKeyword();

        if (isBlank(searchKeyword)) {
            searchKeyword = word;
        }

        searchKeyword = searchKeyword.trim();

        Optional<DictionaryEntry> existingEntry = findExistingWord(
                word,
                sourceLanguage,
                targetLanguage
        );

        if (existingEntry.isPresent()) {
            DictionaryEntry existing = existingEntry.get();

            boolean updated = false;

            /*
             * Cập nhật searchKeyword nếu:
             * - trước đó đang trống
             * - hoặc trước đó lưu sai, ví dụ đang là 羊肉 nhưng keyword gốc phải là thịt cừu
             */
            if (!isBlank(searchKeyword)
                    && !searchKeyword.equalsIgnoreCase(existing.getSearchKeyword())) {
                existing.setSearchKeyword(searchKeyword);
                existing.setNormalizedSearchKeyword(normalizeForSearch(searchKeyword));
                updated = true;
            }

            if (isBlank(existing.getPronunciation()) && !isBlank(dictionary.getPronunciation())) {
                existing.setPronunciation(dictionary.getPronunciation());
                updated = true;
            }

            if (isBlank(existing.getReading()) && !isBlank(dictionary.getReading())) {
                existing.setReading(dictionary.getReading());
                updated = true;
            }

            if (isBlank(existing.getPartOfSpeech()) && !isBlank(dictionary.getPartOfSpeech())) {
                existing.setPartOfSpeech(dictionary.getPartOfSpeech());
                updated = true;
            }

            /*
             * Cập nhật note nếu backend nhận được note mới.
             */
            if (!isBlank(dictionary.getNote())
                    && !dictionary.getNote().equals(existing.getNote())) {
                existing.setNote(dictionary.getNote());
                updated = true;
            }

            /*
             * Cập nhật relatedWords nếu request có gửi relatedWords.
             * Dùng clear + add lại để đồng bộ hoàn toàn với response mới từ AI/frontend.
             */
            if (dictionary.getRelatedWords() != null && !dictionary.getRelatedWords().isEmpty()) {
                if (existing.getRelatedWords() == null) {
                    existing.setRelatedWords(new ArrayList<>());
                }

                existing.getRelatedWords().clear();

                for (String relatedText : dictionary.getRelatedWords()) {
                    if (isBlank(relatedText)) {
                        continue;
                    }

                    DictionaryRelatedWord relatedWord = new DictionaryRelatedWord();
                    relatedWord.setRelatedWord(relatedText.trim());
                    relatedWord.setEntry(existing);

                    existing.getRelatedWords().add(relatedWord);
                }

                updated = true;
            }

            /*
             * Dù updated = false vẫn build lại searchText,
             * để đảm bảo dữ liệu tìm kiếm luôn đúng format mới.
             */
            existing.setSearchText(buildSearchText(existing));

            dictionaryEntryRepository.save(existing);

            if (updated) {
                return new SaveDictionaryResponse(
                        true,
                        "Từ đã tồn tại, đã cập nhật thêm dữ liệu.",
                        existing.getId()
                );
            }

            return new SaveDictionaryResponse(
                    false,
                    "Từ này đã tồn tại trong database.",
                    existing.getId()
            );
        }

        DictionaryEntry entry = new DictionaryEntry();
        entry.setWord(word);
        entry.setNormalizedWord(normalize(word));
        entry.setSourceLanguage(sourceLanguage);
        entry.setTargetLanguage(targetLanguage);
        entry.setPronunciation(dictionary.getPronunciation());
        entry.setReading(dictionary.getReading());
        entry.setPartOfSpeech(dictionary.getPartOfSpeech());
        entry.setNote(dictionary.getNote());

        entry.setSearchKeyword(searchKeyword);
        entry.setNormalizedSearchKeyword(normalizeForSearch(searchKeyword));

        List<DictionaryMeaning> meanings = new ArrayList<>();

        if (dictionary.getMeanings() != null) {
            for (String meaningText : dictionary.getMeanings()) {
                if (isBlank(meaningText)) {
                    continue;
                }

                DictionaryMeaning meaning = new DictionaryMeaning();
                meaning.setMeaning(meaningText.trim());
                meaning.setEntry(entry);

                meanings.add(meaning);
            }
        }

        entry.setMeanings(meanings);

        List<DictionaryExample> examples = new ArrayList<>();

        if (dictionary.getExamples() != null) {
            for (DictionaryResponse.ExampleItem exampleItem : dictionary.getExamples()) {
                if (exampleItem == null) {
                    continue;
                }

                DictionaryExample example = new DictionaryExample();
                example.setExampleSentence(exampleItem.getSentence());
                example.setExampleReading(exampleItem.getReading());
                example.setExampleTranslation(exampleItem.getTranslation());
                example.setEntry(entry);

                examples.add(example);
            }
        }

        entry.setExamples(examples);

        List<DictionaryRelatedWord> relatedWords = new ArrayList<>();

        if (dictionary.getRelatedWords() != null) {
            for (String relatedText : dictionary.getRelatedWords()) {
                if (isBlank(relatedText)) {
                    continue;
                }

                DictionaryRelatedWord relatedWord = new DictionaryRelatedWord();
                relatedWord.setRelatedWord(relatedText.trim());
                relatedWord.setEntry(entry);

                relatedWords.add(relatedWord);
            }
        }

        entry.setRelatedWords(relatedWords);

        /*
         * searchText chỉ lưu:
         * - searchKeyword
         * - normalizedSearchKeyword
         * - word
         * - normalizedWord
         * - pronunciation
         * - reading
         *
         * Không lưu meanings.
         * Không lưu examples.
         * Không lưu note.
         * Không lưu relatedWords.
         */
        entry.setSearchText(buildSearchText(entry));

        DictionaryEntry savedEntry = dictionaryEntryRepository.save(entry);

        return new SaveDictionaryResponse(
                true,
                "Lưu từ vào database thành công.",
                savedEntry.getId()
        );
    }

    /**
     * Search DB theo keyword.
     *
     * Chỉ nhập keyword, không cần chọn sourceLanguage / targetLanguage.
     *
     * Hỗ trợ:
     * - 学校       -> 学校
     * - xue xiao  -> 学校
     * - xué xiào  -> 学校
     * - truong    -> 学校
     * - trường    -> 学校
     * - bay       -> chỉ match máy bay, không match máy tính/tay
     */
    @Transactional(readOnly = true)
    public List<DictionaryResponse> searchDictionary(String keyword) {
        if (isBlank(keyword)) {
            return new ArrayList<>();
        }

        String cleanKeyword = keyword.trim();
        String normalizedKeyword = normalizeForSearch(cleanKeyword);

        List<DictionaryEntry> entries = dictionaryEntryRepository.findAllByOrderByIdDesc();

        return entries.stream()
                .map(entry -> new SearchResult(
                        entry,
                        calculateSearchScore(entry, cleanKeyword, normalizedKeyword)
                ))
                .filter(result -> result.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(20)
                .map(result -> convertToDictionaryResponse(result.entry()))
                .toList();
    }

    /**
     * Nếu controller cũ vẫn gọi hàm có sourceLanguage / targetLanguage,
     * thì vẫn cho chạy nhưng bỏ qua filter ngôn ngữ.
     */
    @Transactional(readOnly = true)
    public List<DictionaryResponse> searchDictionary(
            String keyword,
            String sourceLanguage,
            String targetLanguage
    ) {
        return searchDictionary(keyword);
    }

    /**
     * Lấy chi tiết từ DB theo id.
     */
    @Transactional(readOnly = true)
    public DictionaryResponse getDictionaryDetail(Long id) {
        DictionaryEntry entry = dictionaryEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy từ trong database với id: " + id));

        return convertToDictionaryResponse(entry);
    }

    /**
     * Check word tồn tại.
     */
    @Transactional(readOnly = true)
    public DictionaryCheckResponse checkWordExists(
            String word,
            String sourceLanguage,
            String targetLanguage
    ) {
        if (isBlank(word)) {
            return new DictionaryCheckResponse(false, "Từ kiểm tra không được để trống.", null);
        }

        String finalSourceLanguage = defaultLanguage(sourceLanguage, "vi");
        String finalTargetLanguage = defaultLanguage(targetLanguage, "zh");

        Optional<DictionaryEntry> existingEntry = findExistingWord(
                word.trim(),
                finalSourceLanguage,
                finalTargetLanguage
        );

        if (existingEntry.isPresent()) {
            return new DictionaryCheckResponse(
                    true,
                    "Từ đã tồn tại trong database.",
                    existingEntry.get().getId()
            );
        }

        return new DictionaryCheckResponse(
                false,
                "Từ chưa tồn tại trong database.",
                null
        );
    }

    /**
     * Tìm từ đã tồn tại theo:
     * 1. normalizedWord + sourceLanguage + targetLanguage
     * 2. word ignore case + sourceLanguage + targetLanguage
     * 3. searchKeyword / normalizedSearchKeyword cùng sourceLanguage + targetLanguage
     */
    private Optional<DictionaryEntry> findExistingWord(
            String word,
            String sourceLanguage,
            String targetLanguage
    ) {
        String normalizedWord = normalize(word);

        Optional<DictionaryEntry> byNormalized =
                dictionaryEntryRepository.findFirstByNormalizedWordAndSourceLanguageAndTargetLanguage(
                        normalizedWord,
                        sourceLanguage,
                        targetLanguage
                );

        if (byNormalized.isPresent()) {
            return byNormalized;
        }

        Optional<DictionaryEntry> byWord =
                dictionaryEntryRepository.findFirstByWordIgnoreCaseAndSourceLanguageAndTargetLanguage(
                        word,
                        sourceLanguage,
                        targetLanguage
                );

        if (byWord.isPresent()) {
            return byWord;
        }

        /*
         * Tìm thêm bằng searchKeyword để trường hợp:
         * user nhập "du lịch", AI trả về "旅游"
         * lần sau analyze "du lịch" vẫn lấy được DB, không gọi AI nữa.
         */
        List<DictionaryEntry> entries = dictionaryEntryRepository.findAllByOrderByIdDesc();

        for (DictionaryEntry entry : entries) {
            if (!sameLanguagePair(entry, sourceLanguage, targetLanguage)) {
                continue;
            }

            String normalizedSearchKeyword = normalizeForSearch(entry.getSearchKeyword());
            String savedNormalizedSearchKeyword = normalizeForSearch(entry.getNormalizedSearchKeyword());

            if (!isBlank(normalizedSearchKeyword) && normalizedSearchKeyword.equals(normalizedWord)) {
                return Optional.of(entry);
            }

            if (!isBlank(savedNormalizedSearchKeyword) && savedNormalizedSearchKeyword.equals(normalizedWord)) {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
    }

    private boolean sameLanguagePair(
            DictionaryEntry entry,
            String sourceLanguage,
            String targetLanguage
    ) {
        String entrySource = defaultLanguage(entry.getSourceLanguage(), "");
        String entryTarget = defaultLanguage(entry.getTargetLanguage(), "");

        return entrySource.equals(defaultLanguage(sourceLanguage, ""))
                && entryTarget.equals(defaultLanguage(targetLanguage, ""));
    }

    /**
     * Tính điểm match.
     *
     * Ưu tiên:
     * 1. word khớp tuyệt đối
     * 2. normalizedWord khớp
     * 3. searchKeyword khớp
     * 4. pronunciation / reading khớp
     * 5. searchText chứa keyword
     * 6. fuzzy có kiểm soát cho keyword dài
     */
    private int calculateSearchScore(
            DictionaryEntry entry,
            String keyword,
            String normalizedKeyword
    ) {
        String cleanKeyword = keyword == null ? "" : keyword.trim();

        String word = safe(entry.getWord());
        String normalizedWord = safe(entry.getNormalizedWord());

        String searchKeyword = safe(entry.getSearchKeyword());
        String normalizedSearchKeyword = safe(entry.getNormalizedSearchKeyword());

        String pronunciation = safe(entry.getPronunciation());
        String reading = safe(entry.getReading());

        String searchText = entry.getSearchText();

        if (isBlank(searchText)) {
            searchText = buildSearchText(entry);
        }

        String normalizedWordText = normalizeForSearch(word);
        String normalizedSavedWord = normalizeForSearch(normalizedWord);

        String normalizedSearchKeywordText = normalizeForSearch(searchKeyword);
        String normalizedSavedSearchKeyword = normalizeForSearch(normalizedSearchKeyword);

        String normalizedPronunciation = normalizeForSearch(pronunciation);
        String normalizedReading = normalizeForSearch(reading);

        String normalizedSearchText = normalizeForSearch(searchText);

        if (!cleanKeyword.isEmpty() && word.equalsIgnoreCase(cleanKeyword)) {
            return 100;
        }

        if (!normalizedKeyword.isEmpty() && normalizedWordText.equals(normalizedKeyword)) {
            return 98;
        }

        if (!normalizedKeyword.isEmpty() && normalizedSavedWord.equals(normalizedKeyword)) {
            return 97;
        }

        if (!normalizedKeyword.isEmpty() && normalizedSearchKeywordText.equals(normalizedKeyword)) {
            return 96;
        }

        if (!normalizedKeyword.isEmpty() && normalizedSavedSearchKeyword.equals(normalizedKeyword)) {
            return 95;
        }

        if (!cleanKeyword.isEmpty() && word.contains(cleanKeyword)) {
            return 92;
        }

        if (!normalizedKeyword.isEmpty() && normalizedWordText.contains(normalizedKeyword)) {
            return 90;
        }

        if (!normalizedKeyword.isEmpty() && normalizedSearchKeywordText.contains(normalizedKeyword)) {
            return 88;
        }

        if (!normalizedKeyword.isEmpty() && normalizedSavedSearchKeyword.contains(normalizedKeyword)) {
            return 87;
        }

        if (!normalizedKeyword.isEmpty() && normalizedPronunciation.contains(normalizedKeyword)) {
            return 85;
        }

        if (!normalizedKeyword.isEmpty() && normalizedReading.contains(normalizedKeyword)) {
            return 85;
        }

        if (!normalizedKeyword.isEmpty() && normalizedSearchText.contains(normalizedKeyword)) {
            return 75;
        }

        int fuzzyScore = calculateFuzzyScore(normalizedSearchText, normalizedKeyword);

        if (fuzzyScore > 0) {
            return fuzzyScore;
        }

        return 0;
    }

    /**
     * Fuzzy search có kiểm soát.
     *
     * Không fuzzy với keyword ngắn dưới 5 ký tự.
     *
     * Lý do:
     * - bay gần may
     * - bay gần tay
     * - tai gần tay
     *
     * Nếu fuzzy keyword ngắn sẽ trả kết quả sai.
     */
    private int calculateFuzzyScore(String searchText, String keyword) {
        if (isBlank(searchText) || isBlank(keyword)) {
            return 0;
        }

        /*
         * Keyword dưới 5 ký tự chỉ search bằng contains chính xác.
         * Không dùng Levenshtein.
         */
        if (keyword.length() < 5) {
            return 0;
        }

        String[] tokens = searchText.split("\\s+");

        int bestScore = 0;

        for (String token : tokens) {
            if (isBlank(token)) {
                continue;
            }

            /*
             * Chỉ match prefix khi keyword đủ dài.
             */
            if (token.startsWith(keyword) || keyword.startsWith(token)) {
                bestScore = Math.max(bestScore, 60);
            }

            int distance = levenshteinDistance(token, keyword);

            /*
             * Keyword dài từ 5 ký tự trở lên mới cho sai 1 ký tự.
             */
            if (keyword.length() >= 5 && distance <= 1) {
                bestScore = Math.max(bestScore, 50);
            }

            /*
             * Keyword dài từ 8 ký tự trở lên mới cho sai 2 ký tự.
             */
            if (keyword.length() >= 8 && distance <= 2) {
                bestScore = Math.max(bestScore, 45);
            }
        }

        return bestScore;
    }

    /**
     * Build searchText thu gọn.
     *
     * Chỉ gồm:
     * - searchKeyword
     * - normalizedSearchKeyword
     * - word
     * - normalizedWord
     * - pronunciation
     * - reading
     *
     * Không gồm meanings.
     * Không gồm examples.
     * Không gồm note.
     * Không gồm relatedWords.
     */
    private String buildSearchText(DictionaryEntry entry) {
        StringBuilder builder = new StringBuilder();

        append(builder, entry.getSearchKeyword());
        append(builder, entry.getNormalizedSearchKeyword());

        append(builder, entry.getWord());
        append(builder, entry.getNormalizedWord());

        append(builder, entry.getPronunciation());
        append(builder, entry.getReading());

        return normalizeForSearch(builder.toString());
    }

    private void append(StringBuilder builder, String value) {
        if (!isBlank(value)) {
            builder.append(" ").append(value);
        }
    }

    /**
     * Convert Entity sang DTO trả frontend.
     */
    private DictionaryResponse convertToDictionaryResponse(DictionaryEntry entry) {
        DictionaryResponse response = new DictionaryResponse();

        response.setWord(entry.getWord());
        response.setPronunciation(entry.getPronunciation());
        response.setReading(entry.getReading());
        response.setPartOfSpeech(entry.getPartOfSpeech());
        response.setNote(entry.getNote());

        List<String> meanings = new ArrayList<>();

        if (entry.getMeanings() != null) {
            for (DictionaryMeaning meaning : entry.getMeanings()) {
                if (meaning != null && !isBlank(meaning.getMeaning())) {
                    meanings.add(meaning.getMeaning());
                }
            }
        }

        response.setMeanings(meanings);

        List<DictionaryResponse.ExampleItem> examples = new ArrayList<>();

        if (entry.getExamples() != null) {
            for (DictionaryExample example : entry.getExamples()) {
                if (example == null) {
                    continue;
                }

                DictionaryResponse.ExampleItem item = new DictionaryResponse.ExampleItem();
                item.setSentence(example.getExampleSentence());
                item.setReading(example.getExampleReading());
                item.setTranslation(example.getExampleTranslation());

                examples.add(item);
            }
        }

        response.setExamples(examples);

        List<String> relatedWords = new ArrayList<>();

        if (entry.getRelatedWords() != null) {
            for (DictionaryRelatedWord relatedWord : entry.getRelatedWords()) {
                if (relatedWord != null && !isBlank(relatedWord.getRelatedWord())) {
                    relatedWords.add(relatedWord.getRelatedWord());
                }
            }
        }

        response.setRelatedWords(relatedWords);

        return response;
    }

    /**
     * Tự detect mode nếu frontend không truyền mode.
     */
    private String detectMode(String mode, String text) {
        if (!isBlank(mode)) {
            return mode.trim().toLowerCase();
        }

        if (isBlank(text)) {
            return "word";
        }

        String cleanText = text.trim();

        if (cleanText.split("\\s+").length == 1) {
            return "word";
        }

        return "sentence";
    }

    private String defaultLanguage(String language, String defaultValue) {
        if (isBlank(language)) {
            return defaultValue;
        }

        return language.trim().toLowerCase();
    }

    /**
     * Dùng cho normalizedWord.
     */
    private String normalize(String text) {
        return normalizeForSearch(text);
    }

    /**
     * Chuẩn hóa để search:
     *
     * trường học -> truong hoc
     * xué xiào   -> xue xiao
     * Đăng nhập  -> dang nhap
     */
    private String normalizeForSearch(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .trim()
                .toLowerCase()
                .replace("đ", "d")
                .replaceAll("[,.;:!?()\\[\\]{}\"']", " ")
                .replaceAll("\\s+", " ");
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * Khoảng cách Levenshtein để tìm sai chính tả nhẹ.
     */
    private int levenshteinDistance(String a, String b) {
        if (a == null) {
            a = "";
        }

        if (b == null) {
            b = "";
        }

        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1
                        ),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    private record SearchResult(DictionaryEntry entry, int score) {
    }
}