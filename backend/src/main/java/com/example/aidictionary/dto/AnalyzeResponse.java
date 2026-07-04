package com.example.aidictionary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeResponse {

	private String type;

	private DictionaryResponse dictionary;

	private GrammarResponse grammar;

	private String message;

	private String rawAiResponse;

}