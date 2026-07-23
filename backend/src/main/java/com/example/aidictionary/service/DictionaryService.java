package com.example.aidictionary.service;

import com.example.aidictionary.dto.AnalyzeRequest;
import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.DictionaryCheckResponse;
import com.example.aidictionary.dto.DictionaryResponse;
import com.example.aidictionary.dto.GrammarHistoryResponse;
import com.example.aidictionary.dto.SaveDictionaryRequest;
import com.example.aidictionary.dto.SaveDictionaryResponse;
import com.example.aidictionary.dto.WordDetailRequest;
import com.example.aidictionary.dto.WordLookupResponse;
import com.example.aidictionary.exception.BadRequestException;
import com.example.aidictionary.exception.GeminiServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final AiService aiService;
    private final DictionaryPersistenceService persistenceService;

    /** Chống nhiều request xem chi tiết cùng một từ gọi Gemini đồng thời. */
    private final ConcurrentMap<String, CompletableFuture<AnalyzeResponse>> inFlightDetailRequests =
            new ConcurrentHashMap<>();

    /** Chống nhiều request tạo danh sách gợi ý cho cùng một nội dung. */
    private final ConcurrentMap<String, CompletableFuture<WordLookupResponse>> inFlightLookupRequests =
            new ConcurrentHashMap<>();

    /**
     * Luồng mới:
     * - word: chỉ trả danh sách từ và cách dùng, không lưu database.
     * - sentence/grammar: giữ nguyên hành vi hiện tại.
     */
    public Object analyze(AnalyzeRequest request) {
        validateAnalyzeRequest(request);

        String text = request.getText().trim();
        String mode = detectMode(request.getMode(), text);
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");

        return switch (mode) {
            case "word" -> lookupWordOptions(text, sourceLanguage, targetLanguage);
            case "sentence" -> aiService.analyzeSentence(text, sourceLanguage, targetLanguage);
            case "grammar" -> analyzeGrammar(text, sourceLanguage, targetLanguage);
            default -> throw new BadRequestException("Mode không hợp lệ: " + mode);
        };
    }

    public WordLookupResponse lookupWordOptions(AnalyzeRequest request) {
        validateAnalyzeRequest(request);
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");
        return lookupWordOptions(request.getText().trim(), sourceLanguage, targetLanguage);
    }

    /**
     * Chỉ khi người dùng chọn một từ để xem chi tiết mới:
     * 1. đọc database;
     * 2. nếu chưa có thì gọi Gemini;
     * 3. lưu đúng từ đã được chọn;
     * 4. trả chi tiết cho frontend.
     */
    public AnalyzeResponse getOrCreateWordDetail(WordDetailRequest request) {
        if (request == null || isBlank(request.getWord())) {
            throw new BadRequestException("Từ cần xem chi tiết không được để trống.");
        }

        String selectedWord = request.getWord().trim();
        String originalQuery = isBlank(request.getOriginalQuery())
                ? selectedWord
                : request.getOriginalQuery().trim();
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");

        Optional<DictionaryResponse> cached = persistenceService.findExistingWord(
                selectedWord,
                sourceLanguage,
                targetLanguage
        );
        if (cached.isPresent()) {
            return cachedResponse(cached.get());
        }

        String requestKey = String.join(
                "|",
                sourceLanguage,
                targetLanguage,
                persistenceService.normalizeForSearch(selectedWord)
        );

        CompletableFuture<AnalyzeResponse> ownerFuture = new CompletableFuture<>();
        CompletableFuture<AnalyzeResponse> existingFuture =
                inFlightDetailRequests.putIfAbsent(requestKey, ownerFuture);

        if (existingFuture != null) {
            return await(existingFuture);
        }

        try {
            Optional<DictionaryResponse> secondCheck = persistenceService.findExistingWord(
                    selectedWord,
                    sourceLanguage,
                    targetLanguage
            );
            if (secondCheck.isPresent()) {
                AnalyzeResponse response = cachedResponse(secondCheck.get());
                ownerFuture.complete(response);
                return response;
            }

            AnalyzeResponse response = aiService.analyzeWordDetail(
                    selectedWord,
                    originalQuery,
                    sourceLanguage,
                    targetLanguage
            );
            DictionaryResponse dictionary = response.getDictionary();
            if (dictionary == null) {
                throw new GeminiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "Gemini không trả về dữ liệu chi tiết hợp lệ."
                );
            }

            // Từ được lưu phải là đúng lựa chọn người dùng vừa bấm.
            dictionary.setWord(selectedWord);
            dictionary.setTranslationGroups(null);
            dictionary.setRecommendation(null);
            RelatedWordSanitizer.sanitize(dictionary, selectedWord);
            RelatedWordSanitizer.sanitize(dictionary, originalQuery);

            SaveDictionaryRequest saveRequest = new SaveDictionaryRequest();
            saveRequest.setType("word");
            saveRequest.setSourceLanguage(sourceLanguage);
            saveRequest.setTargetLanguage(targetLanguage);
            saveRequest.setSearchKeyword(selectedWord);
            saveRequest.setDictionary(dictionary);

            try {
                persistenceService.saveDictionary(saveRequest);
            } catch (DataIntegrityViolationException conflict) {
                Optional<DictionaryResponse> savedByAnotherRequest = persistenceService.findExistingWord(
                        selectedWord,
                        sourceLanguage,
                        targetLanguage
                );
                if (savedByAnotherRequest.isEmpty()) {
                    throw conflict;
                }
                response.setDictionary(savedByAnotherRequest.get());
            }

            response.setType("word");
            response.setMessage("Đã tải chi tiết và lưu từ đã chọn vào database.");
            ownerFuture.complete(response);
            return response;
        } catch (RuntimeException exception) {
            ownerFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightDetailRequests.remove(requestKey, ownerFuture);
        }
    }

    public SaveDictionaryResponse saveDictionary(SaveDictionaryRequest request) {
        return persistenceService.saveDictionary(request);
    }

    public List<DictionaryResponse> searchDictionary(
            String keyword,
            String sourceLanguage,
            String targetLanguage
    ) {
        return persistenceService.searchDictionary(keyword, sourceLanguage, targetLanguage);
    }

    public DictionaryResponse getDictionaryDetail(Long id) {
        return persistenceService.getDictionaryDetail(id);
    }

    public void deleteDictionaryEntry(Long id) {
        persistenceService.deleteDictionaryEntry(id);
    }

    public DictionaryCheckResponse checkWordExists(
            String word,
            String sourceLanguage,
            String targetLanguage
    ) {
        return persistenceService.checkWordExists(word, sourceLanguage, targetLanguage);
    }

    public List<GrammarHistoryResponse> getGrammarHistory(int limit) {
        return persistenceService.getGrammarHistory(limit);
    }

    public GrammarHistoryResponse getGrammarHistoryDetail(Long id) {
        return persistenceService.getGrammarHistoryDetail(id);
    }

    private WordLookupResponse lookupWordOptions(
            String text,
            String sourceLanguage,
            String targetLanguage
    ) {
        String requestKey = String.join(
                "|",
                sourceLanguage,
                targetLanguage,
                persistenceService.normalizeForSearch(text)
        );

        CompletableFuture<WordLookupResponse> ownerFuture = new CompletableFuture<>();
        CompletableFuture<WordLookupResponse> existingFuture =
                inFlightLookupRequests.putIfAbsent(requestKey, ownerFuture);

        if (existingFuture != null) {
            return awaitLookup(existingFuture);
        }

        try {
            WordLookupResponse response = aiService.lookupWordOptions(
                    text,
                    sourceLanguage,
                    targetLanguage
            );
            response.setQuery(text);
            response.setSourceLanguage(sourceLanguage);
            response.setTargetLanguage(targetLanguage);
            if (isBlank(response.getMessage())) {
                response.setMessage("Chọn một từ để xem chi tiết. Danh sách này chưa được lưu vào database.");
            }
            ownerFuture.complete(response);
            return response;
        } catch (RuntimeException exception) {
            ownerFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightLookupRequests.remove(requestKey, ownerFuture);
        }
    }

    private AnalyzeResponse analyzeGrammar(
            String text,
            String sourceLanguage,
            String targetLanguage
    ) {
        AnalyzeResponse response = aiService.checkGrammar(
                text,
                sourceLanguage,
                targetLanguage
        );
        persistenceService.saveGrammarCheck(
                text,
                sourceLanguage,
                targetLanguage,
                response
        );
        return response;
    }

    private AnalyzeResponse cachedResponse(DictionaryResponse dictionary) {
        return AnalyzeResponse.builder()
                .type("word")
                .dictionary(dictionary)
                .message("Lấy chi tiết từ database.")
                .build();
    }

    private AnalyzeResponse await(CompletableFuture<AnalyzeResponse> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            throw unwrapFutureException(exception, "Không thể nhận kết quả chi tiết từ Gemini.");
        }
    }

    private WordLookupResponse awaitLookup(CompletableFuture<WordLookupResponse> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            throw unwrapFutureException(exception, "Không thể nhận danh sách từ gợi ý từ Gemini.");
        }
    }

    private RuntimeException unwrapFutureException(
            CompletionException exception,
            String fallbackMessage
    ) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new GeminiServiceException(HttpStatus.BAD_GATEWAY, fallbackMessage, cause);
    }

    private void validateAnalyzeRequest(AnalyzeRequest request) {
        if (request == null || isBlank(request.getText())) {
            throw new BadRequestException("Nội dung tra cứu không được để trống.");
        }
    }

    private String detectMode(String mode, String text) {
        if (!isBlank(mode) && !"auto".equalsIgnoreCase(mode.trim())) {
            String normalizedMode = mode.trim().toLowerCase();
            if (!List.of("word", "sentence", "grammar").contains(normalizedMode)) {
                throw new BadRequestException("Mode không hợp lệ: " + mode);
            }
            return normalizedMode;
        }

        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isEmpty()) {
            return "word";
        }

        if (cleanText.matches(".*[.!?。！？；;].*")) {
            return "sentence";
        }

        String[] tokens = cleanText.split("\\s+");
        if (tokens.length <= 4 && cleanText.length() <= 40) {
            return "word";
        }

        return "sentence";
    }

    private String defaultLanguage(String language, String defaultValue) {
        return isBlank(language) ? defaultValue : language.trim().toLowerCase();
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
