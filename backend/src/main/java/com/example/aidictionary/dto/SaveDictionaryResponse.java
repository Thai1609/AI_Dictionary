package com.example.aidictionary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SaveDictionaryResponse {

    private boolean success;
    private boolean created;
    private String message;
    private Long id;

    public SaveDictionaryResponse(boolean success, String message, Long id) {
        this(success, false, message, id);
    }
}
