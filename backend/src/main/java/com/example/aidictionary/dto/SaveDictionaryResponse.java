package com.example.aidictionary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SaveDictionaryResponse {

    private boolean success;

    private String message;

    private Long id;
}