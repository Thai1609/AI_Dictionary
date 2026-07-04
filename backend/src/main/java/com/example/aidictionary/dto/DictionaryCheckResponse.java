package com.example.aidictionary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryCheckResponse {

    private boolean exists;

    private String message;

    private Long id;
}