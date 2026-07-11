package com.example.aidictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalyzeRequest {

    @NotBlank(message = "Nội dung tra cứu không được để trống.")
    @Size(max = 2000, message = "Nội dung tra cứu không được vượt quá 2000 ký tự.")
    private String text;

    @Pattern(
            regexp = "(?i)word|sentence|grammar",
            message = "Mode chỉ chấp nhận word, sentence hoặc grammar."
    )
    private String mode;

    @Size(max = 20, message = "Ngôn ngữ nguồn không được vượt quá 20 ký tự.")
    private String sourceLanguage = "vi";

    @Size(max = 20, message = "Ngôn ngữ đích không được vượt quá 20 ký tự.")
    private String targetLanguage = "zh";
}
