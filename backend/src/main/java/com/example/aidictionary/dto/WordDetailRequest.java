package com.example.aidictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WordDetailRequest {

    @NotBlank(message = "Từ cần xem chi tiết không được để trống.")
    @Size(max = 500, message = "Từ cần xem chi tiết không được vượt quá 500 ký tự.")
    private String word;

    @Size(max = 500, message = "Từ khóa ban đầu không được vượt quá 500 ký tự.")
    private String originalQuery;

    @Size(max = 20, message = "Ngôn ngữ nguồn không được vượt quá 20 ký tự.")
    private String sourceLanguage;

    @Size(max = 20, message = "Ngôn ngữ đích không được vượt quá 20 ký tự.")
    private String targetLanguage;
}
