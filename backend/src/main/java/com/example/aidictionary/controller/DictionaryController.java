package com.example.aidictionary.controller;

import com.example.aidictionary.dto.AnalyzeRequest;
import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.DictionaryResponse;
import com.example.aidictionary.dto.SaveDictionaryRequest;
import com.example.aidictionary.dto.SaveDictionaryResponse;
import com.example.aidictionary.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping("/health")
    public String health() {
        return "AI Dictionary Backend is running";
    }

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@RequestBody AnalyzeRequest request) {
        return dictionaryService.analyze(request);
    }

    @PostMapping("/save")
    public SaveDictionaryResponse save(@RequestBody SaveDictionaryRequest request) {
        return dictionaryService.saveDictionary(request);
    }

    @GetMapping("/search")
    public List<DictionaryResponse> search(@RequestParam String keyword) {
        return dictionaryService.searchDictionary(keyword);
    }
}