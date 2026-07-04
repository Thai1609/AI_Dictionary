package com.example.aidictionary.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDictionaryRequest {

    private String type;

    private String sourceLanguage;

    private String targetLanguage;

    private String searchKeyword;

    private DictionaryResponse dictionary;
}