package com.example.aidictionary.service;

import com.example.aidictionary.dto.AnalyzeRequest;
import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.DictionaryCheckResponse;
import com.example.aidictionary.dto.DictionaryResponse;
import com.example.aidictionary.dto.GrammarHistoryResponse;
import com.example.aidictionary.dto.SaveDictionaryRequest;
import com.example.aidictionary.dto.SaveDictionaryResponse;
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

    /**
     * Chống nhiều request trong cùng một instance gọi Gemini cho cùng một từ.
     * Có thể bật unique index tùy chọn trong database/ khi chạy nhiều instance.
     */
    private final ConcurrentMap<String, CompletableFuture<AnalyzeResponse>> inFlightWordRequests =
            new ConcurrentHashMap<>();

    /**
     * Không đặt @Transactional ở đây. Chỉ các thao tác DB ngắn trong
     * DictionaryPersistenceService mới mở transaction; thời gian chờ Gemini
     * không giữ transaction hoặc database connection.
     */
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        if (request == null || isBlank(request.getText())) {
            throw new BadRequestException("Nội dung tra cứu không được để trống.");
        }

        String text = request.getText().trim();
        String mode = detectMode(request.getMode(), text);
        String sourceLanguage = defaultLanguage(request.getSourceLanguage(), "vi");
        String targetLanguage = defaultLanguage(request.getTargetLanguage(), "zh");

        return switch (mode) {
            case "word" -> analyzeWord(text, sourceLanguage, targetLanguage);
            case "sentence" -> aiService.analyzeSentence(text, sourceLanguage, targetLanguage);
            case "grammar" -> analyzeGrammar(text, sourceLanguage, targetLanguage);
            default -> throw new BadRequestException("Mode không hợp lệ: " + mode);
        };
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

    private AnalyzeResponse analyzeWord(
            String text,
            String sourceLanguage,
            String targetLanguage
    ) {
        Optional<DictionaryResponse> cached = persistenceService.findExistingWord(
                text,
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
                persistenceService.normalizeForSearch(text)
        );

        CompletableFuture<AnalyzeResponse> ownerFuture = new CompletableFuture<>();
        CompletableFuture<AnalyzeResponse> existingFuture =
                inFlightWordRequests.putIfAbsent(requestKey, ownerFuture);

        if (existingFuture != null) {
            return await(existingFuture);
        }

        try {
            // Double-check sau khi giành quyền xử lý để đóng race condition.
            Optional<DictionaryResponse> secondCheck = persistenceService.findExistingWord(
                    text,
                    sourceLanguage,
                    targetLanguage
            );
            if (secondCheck.isPresent()) {
                AnalyzeResponse response = cachedResponse(secondCheck.get());
                ownerFuture.complete(response);
                return response;
            }

            AnalyzeResponse response = aiService.analyzeWord(
                    text,
                    sourceLanguage,
                    targetLanguage
            );
            DictionaryResponse dictionary = response.getDictionary();
            if (dictionary == null) {
                throw new GeminiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "Gemini không trả về dữ liệu từ điển hợp lệ."
                );
            }

            boolean hasTranslationGroups = dictionary.getTranslationGroups() != null
                    && dictionary.getTranslationGroups().stream()
                    .filter(group -> group != null && group.getItems() != null)
                    .flatMap(group -> group.getItems().stream())
                    .anyMatch(item -> item != null && !isBlank(item.getWord()));

            if (isBlank(dictionary.getWord())
                    && dictionary.getRecommendation() != null
                    && !isBlank(dictionary.getRecommendation().getDefaultWord())) {
                dictionary.setWord(dictionary.getRecommendation().getDefaultWord().trim());
            }

            if (isBlank(dictionary.getWord()) && hasTranslationGroups) {
                dictionary.getTranslationGroups().stream()
                        .filter(group -> group != null && group.getItems() != null)
                        .flatMap(group -> group.getItems().stream())
                        .filter(item -> item != null && !isBlank(item.getWord()))
                        .findFirst()
                        .ifPresent(item -> dictionary.setWord(item.getWord().trim()));
            }

            if (isBlank(dictionary.getWord())) {
                throw new GeminiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "Gemini không trả về từ mặc định hoặc danh sách bản dịch hợp lệ."
                );
            }

            SaveDictionaryRequest saveRequest = new SaveDictionaryRequest();
            saveRequest.setType("word");
            saveRequest.setSourceLanguage(sourceLanguage);
            saveRequest.setTargetLanguage(targetLanguage);
            saveRequest.setSearchKeyword(text);
            saveRequest.setDictionary(response.getDictionary());

            try {
                persistenceService.saveDictionary(saveRequest);
            } catch (DataIntegrityViolationException conflict) {
                // Một instance khác có thể đã lưu trước. Chỉ bỏ qua lỗi khi đọc lại được dữ liệu.
                Optional<DictionaryResponse> savedByAnotherRequest = persistenceService.findExistingWord(
                        text,
                        sourceLanguage,
                        targetLanguage
                );
                if (savedByAnotherRequest.isEmpty()) {
                    throw conflict;
                }
                response.setDictionary(savedByAnotherRequest.get());
            }

            if (isBlank(response.getMessage())) {
                response.setMessage("Phân tích bằng Gemini và đã lưu vào database.");
            }
            ownerFuture.complete(response);
            return response;
        } catch (RuntimeException exception) {
            ownerFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightWordRequests.remove(requestKey, ownerFuture);
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
                .message("Lấy dữ liệu từ database.")
                .build();
    }

    private AnalyzeResponse await(CompletableFuture<AnalyzeResponse> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new GeminiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể nhận kết quả phân tích Gemini.",
                    cause
            );
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
