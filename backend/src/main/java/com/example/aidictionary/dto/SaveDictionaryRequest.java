package com.example.aidictionary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDictionaryRequest {

    @Size(max = 30, message = "Type không được vượt quá 30 ký tự.")
    private String type;

    @Size(max = 20, message = "Ngôn ngữ nguồn không được vượt quá 20 ký tự.")
    private String sourceLanguage;

    @Size(max = 20, message = "Ngôn ngữ đích không được vượt quá 20 ký tự.")
    private String targetLanguage;

    @Size(max = 500, message = "Từ khóa tìm kiếm không được vượt quá 500 ký tự.")
    private String searchKeyword;

    @Valid
    @NotNull(message = "Dữ liệu từ điển không được để trống.")
    private DictionaryResponse dictionary;
}
