package com.example.aidictionary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalyzeRequest {

    @NotBlank
    private String text;

    private String mode;

    private String sourceLanguage = "en";

    private String targetLanguage = "vi";
}