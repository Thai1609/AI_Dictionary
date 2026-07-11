package com.example.aidictionary.controller;

import com.example.aidictionary.dto.AnalyzeRequest;
import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.DictionaryCheckResponse;
import com.example.aidictionary.dto.DictionaryResponse;
import com.example.aidictionary.dto.GrammarHistoryResponse;
import com.example.aidictionary.dto.SaveDictionaryRequest;
import com.example.aidictionary.dto.SaveDictionaryResponse;
import com.example.aidictionary.service.DictionaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Dictionary Backend is running");
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(
            @Valid @RequestBody AnalyzeRequest request
    ) {
        return ResponseEntity.ok(dictionaryService.analyze(request));
    }

    @PostMapping("/save")
    public ResponseEntity<SaveDictionaryResponse> save(
            @Valid @RequestBody SaveDictionaryRequest request
    ) {
        SaveDictionaryResponse response = dictionaryService.saveDictionary(request);
        HttpStatus status = response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<DictionaryResponse>> search(
            @RequestParam
            @NotBlank(message = "Keyword không được để trống.")
            @Size(max = 500, message = "Keyword không được vượt quá 500 ký tự.")
            String keyword,
            @RequestParam(required = false)
            @Size(max = 20, message = "Ngôn ngữ nguồn không được vượt quá 20 ký tự.")
            String sourceLanguage,
            @RequestParam(required = false)
            @Size(max = 20, message = "Ngôn ngữ đích không được vượt quá 20 ký tự.")
            String targetLanguage
    ) {
        return ResponseEntity.ok(dictionaryService.searchDictionary(
                keyword,
                sourceLanguage,
                targetLanguage
        ));
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<DictionaryResponse> getDetail(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(dictionaryService.getDictionaryDetail(id));
    }

    @GetMapping("/exists")
    public ResponseEntity<DictionaryCheckResponse> checkExists(
            @RequestParam
            @NotBlank(message = "Từ kiểm tra không được để trống.")
            @Size(max = 500, message = "Từ kiểm tra không được vượt quá 500 ký tự.")
            String word,
            @RequestParam(required = false)
            @Size(max = 20, message = "Ngôn ngữ nguồn không được vượt quá 20 ký tự.")
            String sourceLanguage,
            @RequestParam(required = false)
            @Size(max = 20, message = "Ngôn ngữ đích không được vượt quá 20 ký tự.")
            String targetLanguage
    ) {
        return ResponseEntity.ok(dictionaryService.checkWordExists(
                word,
                sourceLanguage,
                targetLanguage
        ));
    }

    @GetMapping("/grammar-history")
    public ResponseEntity<List<GrammarHistoryResponse>> getGrammarHistory(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit phải lớn hơn hoặc bằng 1.")
            @Max(value = 100, message = "Limit không được vượt quá 100.")
            int limit
    ) {
        return ResponseEntity.ok(dictionaryService.getGrammarHistory(limit));
    }

    @GetMapping("/grammar-history/{id}")
    public ResponseEntity<GrammarHistoryResponse> getGrammarHistoryDetail(
            @PathVariable @Positive Long id
    ) {
        return ResponseEntity.ok(dictionaryService.getGrammarHistoryDetail(id));
    }
}
