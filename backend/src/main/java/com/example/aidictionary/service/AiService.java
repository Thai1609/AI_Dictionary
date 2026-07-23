package com.example.aidictionary.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.WordLookupResponse;
import com.example.aidictionary.dto.gemini.GeminiRequest;
import com.example.aidictionary.dto.gemini.GeminiResponse;
import com.example.aidictionary.exception.GeminiServiceException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AiService {

	private final WebClient geminiWebClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public AiService(@Qualifier("geminiWebClient") WebClient geminiWebClient) {
		this.geminiWebClient = geminiWebClient;
	}

	@Value("${gemini.api-key:}")
	private String geminiApiKey;

	@Value("${gemini.model:gemini-3.5-flash}")
	private String geminiModel;

	@Value("${gemini.fallback-model:gemini-3.1-flash-lite}")
	private String geminiFallbackModel;

	@Value("${gemini.timeout-ms:20000}")
	private long timeoutMs;

	@Value("${gemini.max-retries:2}")
	private int maxRetries;

	@Value("${gemini.retry-base-delay-ms:2000}")
	private long retryBaseDelayMs;

	@Value("${gemini.min-request-interval-ms:1200}")
	private long minRequestIntervalMs;

	private final Object requestRateLock = new Object();
	private long nextRequestAllowedAt;

	public WordLookupResponse lookupWordOptions(
			String text,
			String sourceLanguage,
			String targetLanguage
	) {
		String aiText = requestGeminiText(
				buildWordLookupPrompt(text, sourceLanguage, targetLanguage)
		);
		try {
			WordLookupResponse response = jsonMapper.readValue(
					cleanJson(aiText),
					WordLookupResponse.class
			);
			sanitizeWordOptions(response);
			return response;
		} catch (JacksonException exception) {
			throw new GeminiServiceException(
					HttpStatus.BAD_GATEWAY,
					"Gemini trả về danh sách từ không hợp lệ.",
					exception
			);
		}
	}

	public AnalyzeResponse analyzeWordDetail(
			String selectedWord,
			String originalQuery,
			String sourceLanguage,
			String targetLanguage
	) {
		return callGeminiAndParse(
				buildWordDetailPrompt(
						selectedWord,
						originalQuery,
						sourceLanguage,
						targetLanguage
				),
				"word",
				selectedWord
		);
	}

	public AnalyzeResponse analyzeSentence(String sentence, String sourceLanguage, String targetLanguage) {
		return callGeminiAndParse(
				buildSentencePrompt(sentence, sourceLanguage, targetLanguage),
				"sentence",
				null
		);
	}

	public AnalyzeResponse checkGrammar(String text, String sourceLanguage, String targetLanguage) {
		return callGeminiAndParse(
				buildGrammarPrompt(text, sourceLanguage, targetLanguage),
				"grammar",
				null
		);
	}

	private AnalyzeResponse callGeminiAndParse(String prompt, String type, String searchedWord) {
		String aiText = requestGeminiText(prompt);
		try {
			String json = cleanJson(aiText);
			json = normalizeWordResponse(json);

			AnalyzeResponse response = jsonMapper.readValue(json, AnalyzeResponse.class);
			if (response.getType() == null || response.getType().isBlank()) {
				response.setType(type);
			}
			if ("word".equals(type)) {
				RelatedWordSanitizer.sanitize(response.getDictionary(), searchedWord);
			}
			response.setRawAiResponse(aiText);
			return response;
		} catch (JacksonException exception) {
			throw new GeminiServiceException(HttpStatus.BAD_GATEWAY, "Gemini trả về JSON không hợp lệ.", exception);
		}
	}

	private String requestGeminiText(String prompt) {
		if (geminiApiKey == null || geminiApiKey.isBlank()) {
			throw new GeminiServiceException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Gemini API key chưa được cấu hình."
			);
		}

		GeminiResponse geminiResponse;
		try {
			geminiResponse = callGemini(prompt, geminiModel);
		} catch (GeminiServiceException firstError) {
			boolean canFallback = isModelUnavailable(firstError)
					&& geminiFallbackModel != null
					&& !geminiFallbackModel.isBlank()
					&& !geminiFallbackModel.equals(geminiModel);
			if (!canFallback) {
				throw firstError;
			}
			geminiResponse = callGemini(prompt, geminiFallbackModel);
		}

		if (geminiResponse == null
				|| geminiResponse.getFirstText() == null
				|| geminiResponse.getFirstText().isBlank()) {
			throw new GeminiServiceException(
					HttpStatus.BAD_GATEWAY,
					"Gemini không trả về dữ liệu."
			);
		}
		return geminiResponse.getFirstText();
	}

	private GeminiResponse callGemini(String prompt, String model) {
		int retryLimit = Math.max(maxRetries, 0);

		for (int attempt = 0; attempt <= retryLimit; attempt++) {
			waitForRequestSlot();

			try {
				return geminiWebClient.post()
						.uri(uriBuilder -> uriBuilder
								.path("/models/{model}:generateContent")
								.queryParam("key", geminiApiKey)
								.build(model))
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(new GeminiRequest(prompt))
						.retrieve()
						.bodyToMono(GeminiResponse.class)
						.timeout(Duration.ofMillis(Math.max(timeoutMs, 1000L)))
						.block();
			} catch (WebClientResponseException exception) {
				int statusCode = exception.getStatusCode().value();
				boolean transientError = statusCode == 429 || statusCode == 503;

				if (transientError && attempt < retryLimit) {
					sleepBeforeRetry(exception, attempt);
					continue;
				}

				if (statusCode == 429) {
					throw new GeminiServiceException(
							HttpStatus.TOO_MANY_REQUESTS,
							"Gemini đã vượt giới hạn request hoặc token của project. Vui lòng thử lại sau.",
							exception
					);
				}

				throw new GeminiServiceException(
						HttpStatus.BAD_GATEWAY,
						"Gemini API trả về lỗi HTTP " + statusCode + ".",
						exception
				);
			} catch (RuntimeException exception) {
				if (containsCause(exception, TimeoutException.class)) {
					throw new GeminiServiceException(
							HttpStatus.GATEWAY_TIMEOUT,
							"Gemini phản hồi quá thời gian cho phép " + timeoutMs + " ms.",
							exception
					);
				}
				throw new GeminiServiceException(
						HttpStatus.BAD_GATEWAY,
						"Không thể kết nối đến Gemini API.",
						exception
				);
			}
		}

		throw new GeminiServiceException(HttpStatus.BAD_GATEWAY, "Không thể gọi Gemini API.");
	}

	private boolean isModelUnavailable(GeminiServiceException exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof WebClientResponseException webClientError) {
				int statusCode = webClientError.getStatusCode().value();
				return statusCode == 400 || statusCode == 404;
			}
			current = current.getCause();
		}
		return false;
	}

	private void waitForRequestSlot() {
		long interval = Math.max(minRequestIntervalMs, 0L);
		if (interval == 0L) {
			return;
		}

		synchronized (requestRateLock) {
			long now = System.currentTimeMillis();
			long waitMs = Math.max(nextRequestAllowedAt - now, 0L);
			if (waitMs > 0L) {
				sleep(waitMs);
			}
			nextRequestAllowedAt = System.currentTimeMillis() + interval;
		}
	}

	private void sleepBeforeRetry(WebClientResponseException exception, int attempt) {
		long retryAfterMs = parseRetryAfterMillis(exception);
		long exponentialDelay = Math.max(retryBaseDelayMs, 250L)
				* (1L << Math.min(attempt, 6));
		long jitterMs = ThreadLocalRandom.current().nextLong(200L, 751L);
		long delayMs = Math.max(retryAfterMs, exponentialDelay + jitterMs);
		sleep(Math.min(delayMs, 60_000L));
	}

	private long parseRetryAfterMillis(WebClientResponseException exception) {
		String retryAfter = exception.getHeaders().getFirst("Retry-After");
		if (retryAfter == null || retryAfter.isBlank()) {
			return 0L;
		}

		try {
			return Math.max(Long.parseLong(retryAfter.trim()), 0L) * 1000L;
		} catch (NumberFormatException ignored) {
			try {
				ZonedDateTime retryAt = ZonedDateTime.parse(
						retryAfter.trim(),
						DateTimeFormatter.RFC_1123_DATE_TIME
				);
				return Math.max(retryAt.toInstant().toEpochMilli() - System.currentTimeMillis(), 0L);
			} catch (DateTimeParseException ignoredDate) {
				return 0L;
			}
		}
	}

	private void sleep(long delayMs) {
		try {
			Thread.sleep(Math.max(delayMs, 0L));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new GeminiServiceException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Tiến trình gọi Gemini đã bị gián đoạn.",
					exception
			);
		}
	}

	private boolean containsCause(Throwable throwable, Class<? extends Throwable> expectedType) {
		Throwable current = throwable;
		while (current != null) {
			if (expectedType.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private String cleanJson(String text) {
		if (text == null) {
			return "{}";
		}

		String clean = text.trim();
		if (clean.startsWith("```json")) {
			clean = clean.substring(7);
		}
		if (clean.startsWith("```")) {
			clean = clean.substring(3);
		}
		if (clean.endsWith("```")) {
			clean = clean.substring(0, clean.length() - 3);
		}

		clean = clean.trim();
		int firstBrace = clean.indexOf('{');
		int lastBrace = clean.lastIndexOf('}');
		if (firstBrace >= 0 && lastBrace > firstBrace) {
			clean = clean.substring(firstBrace, lastBrace + 1);
		}
		return clean.trim();
	}

	private String normalizeWordResponse(String json) {

		try {

			JsonNode root = jsonMapper.readTree(json);

			if (!(root instanceof ObjectNode rootObject)) {
				return json;
			}

			JsonNode dictionaryNode = rootObject.get("dictionary");

			if (!(dictionaryNode instanceof ObjectNode dictionaryObject)) {
				return json;
			}

			JsonNode translationGroups = rootObject.get("translationGroups");

			if (translationGroups != null && !translationGroups.isNull()) {

				dictionaryObject.set("translationGroups", translationGroups);

				rootObject.remove("translationGroups");
			}

			JsonNode recommendation = rootObject.get("recommendation");

			if (recommendation != null && !recommendation.isNull()) {

				dictionaryObject.set("recommendation", recommendation);

				rootObject.remove("recommendation");
			}

			return jsonMapper.writeValueAsString(rootObject);

		} catch (Exception e) {
			return json;
		}
	}

	private void sanitizeWordOptions(WordLookupResponse response) {
		if (response == null) {
			throw new GeminiServiceException(
					HttpStatus.BAD_GATEWAY,
					"Gemini không trả về danh sách từ."
			);
		}

		List<WordLookupResponse.WordOption> source = response.getOptions();
		if (source == null) {
			response.setOptions(List.of());
			return;
		}

		Map<String, WordLookupResponse.WordOption> unique = new LinkedHashMap<>();
		for (WordLookupResponse.WordOption option : source) {
			if (option == null || option.getWord() == null || option.getWord().isBlank()) {
				continue;
			}
			option.setWord(option.getWord().trim());
			option.setPronunciation(trimToNull(option.getPronunciation()));
			option.setReading(trimToNull(option.getReading()));
			option.setPartOfSpeech(trimToNull(option.getPartOfSpeech()));
			option.setUsage(trimToNull(option.getUsage()));
			option.setReason(trimToNull(option.getReason()));
			option.setMeanings(cleanTextList(option.getMeanings()));

			String key = normalizeOptionWord(option.getWord());
			WordLookupResponse.WordOption existing = unique.get(key);
			if (existing == null) {
				unique.put(key, option);
			} else if (option.isRecommended()) {
				existing.setRecommended(true);
				if (existing.getReason() == null) {
					existing.setReason(option.getReason());
				}
			}
		}

		List<WordLookupResponse.WordOption> cleaned = new ArrayList<>(unique.values());
		boolean recommendedFound = false;
		for (WordLookupResponse.WordOption option : cleaned) {
			if (!option.isRecommended()) {
				continue;
			}
			if (recommendedFound) {
				option.setRecommended(false);
			} else {
				recommendedFound = true;
			}
		}
		if (!recommendedFound && !cleaned.isEmpty()) {
			cleaned.get(0).setRecommended(true);
		}
		response.setOptions(cleaned);
	}

	private List<String> cleanTextList(List<String> values) {
		if (values == null) {
			return List.of();
		}
		Map<String, String> unique = new LinkedHashMap<>();
		for (String value : values) {
			String clean = trimToNull(value);
			if (clean != null) {
				unique.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
			}
		}
		return new ArrayList<>(unique.values());
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizeOptionWord(String value) {
		return value == null
				? ""
				: value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private String buildWordLookupPrompt(String text, String sourceLanguage, String targetLanguage) {
		return """
				Bạn là chuyên gia từ điển dành cho người Việt học tiếng Trung.

				Nhiệm vụ hiện tại chỉ là tạo DANH SÁCH LỰA CHỌN NGẮN GỌN.
				Không phân tích chi tiết, không tạo câu ví dụ và không tạo relatedWords.

				- Nội dung người dùng tra: "%s"
				- Ngôn ngữ nguồn: "%s"
				- Ngôn ngữ đích: "%s"

				Chỉ trả về JSON hợp lệ, không markdown và không nội dung ngoài JSON:
				{
				  "query": "",
				  "sourceLanguage": "",
				  "targetLanguage": "",
				  "options": [
				    {
				      "word": "",
				      "pronunciation": "",
				      "reading": "",
				      "partOfSpeech": "",
				      "meanings": [""],
				      "usage": "",
				      "recommended": true,
				      "reason": ""
				    }
				  ],
				  "message": ""
				}

				Quy tắc:
				- Trả 3 đến 8 lựa chọn thực sự hữu ích và khác nhau về ngữ cảnh sử dụng.
				- options phải bao gồm cả từ phù hợp nhất và các từ gần nghĩa/liên quan đáng học.
				- Không lặp cùng một word.
				- Chỉ đúng một lựa chọn có recommended=true.
				- usage giải thích ngắn gọn khi nào dùng từ đó và khác gì với lựa chọn khác.
				- partOfSpeech và mọi giải thích phải viết bằng tiếng Việt.
				- Nếu word là tiếng Trung, dùng chữ Hán phồn thể; pronunciation và reading dùng pinyin có dấu.
				- meanings chỉ chứa nghĩa tiếng Việt ngắn gọn.
				- Không tạo examples, relatedWords, translationGroups hoặc dữ liệu chi tiết khác.
				""".formatted(text, sourceLanguage, targetLanguage);
	}

	private String buildWordDetailPrompt(
			String selectedWord,
			String originalQuery,
			String sourceLanguage,
			String targetLanguage
	) {
		return """
				Bạn là chuyên gia từ điển dành cho người Việt học tiếng Trung.

				Người dùng đã chọn một từ từ danh sách gợi ý và bây giờ mới yêu cầu xem chi tiết.
				- Từ được chọn: "%s"
				- Từ khóa tra ban đầu: "%s"
				- Ngôn ngữ nguồn ban đầu: "%s"
				- Ngôn ngữ đích ban đầu: "%s"

				Chỉ trả về JSON hợp lệ, không markdown và không nội dung ngoài JSON:
				{
				  "type": "word",
				  "dictionary": {
				    "word": "",
				    "pronunciation": "",
				    "reading": "",
				    "partOfSpeech": "",
				    "meanings": [""],
				    "examples": [
				      {
				        "sentence": "",
				        "reading": "",
				        "translation": ""
				      }
				    ],
				    "relatedWords": [""],
				    "note": ""
				  },
				  "message": ""
				}

				Quy tắc:
				- dictionary.word phải đúng từ được chọn, không thay bằng từ gần nghĩa khác.
				- Chỉ phân tích chi tiết đúng một từ; không trả translationGroups và recommendation.
				- partOfSpeech, meanings, note và phần giải thích phải bằng tiếng Việt.
				- Nếu từ được chọn là tiếng Trung, word dùng chữ Hán phồn thể và reading dùng pinyin có dấu.
				- examples gồm 2 đến 4 ví dụ tự nhiên trong đời sống, học tập hoặc công việc.
				- examples[].sentence dùng tiếng Trung khi từ được chọn là tiếng Trung.
				- examples[].reading là pinyin đầy đủ có dấu.
				- examples[].translation là tiếng Việt tự nhiên.
				- relatedWords không được chứa từ được chọn hoặc từ khóa ban đầu.
				- Không lặp từ trong relatedWords; nếu không có thì trả mảng rỗng [].
				""".formatted(
				selectedWord,
				originalQuery,
				sourceLanguage,
				targetLanguage
		);
	}

	private String buildSentencePrompt(String sentence, String sourceLanguage, String targetLanguage) {
		return """
				Bạn là AI Dictionary dành cho người Việt học tiếng Trung.

				Hãy phân tích câu sau:
				- Câu người dùng nhập: "%s"
				- Ngôn ngữ câu gốc: "%s"
				- Ngôn ngữ muốn học / muốn dịch sang: "%s"

				Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.
				Tuyệt đối không dùng tiếng Anh trong bất kỳ field nào.

				Format JSON bắt buộc:

				{
				  "type": "sentence",
				  "dictionary": {
				    "word": "",
				    "originalSentence": "",
				    "pronunciation": "",
				    "reading": "",
				    "translation": "",
				    "naturalVersion": "",
				    "keyPhrases": [
				      {
				        "phrase": "",
				        "reading": "",
				        "meaning": ""
				      }
				    ],
				    "grammarPoints": [
				      {
				        "pattern": "",
				        "explanation": "",
				        "example": ""
				      }
				    ],
				    "note": ""
				  },
				  "message": ""
				}

				Quy tắc bắt buộc:
				- Người dùng là người Việt, nên mọi phần giải thích phải viết bằng tiếng Việt.
				- Không được dùng tiếng Anh trong meaning, explanation, note, example.
				- originalSentence là câu gốc người dùng nhập.
				- Nếu sourceLanguage là vi và targetLanguage là zh:
				  + translation phải là bản dịch tiếng Trung tự nhiên.
				  + naturalVersion phải là cách nói tiếng Trung tự nhiên hơn nếu cần.
				  + reading phải là pinyin đầy đủ của câu tiếng Trung trong translation hoặc naturalVersion.
				  + keyPhrases[].phrase là cụm từ tiếng Trung quan trọng.
				  + keyPhrases[].reading là pinyin của cụm từ đó.
				  + keyPhrases[].meaning là nghĩa tiếng Việt của cụm từ đó.
				  + grammarPoints[].pattern là cấu trúc tiếng Trung.
				  + grammarPoints[].explanation là giải thích bằng tiếng Việt.
				  + grammarPoints[].example là ví dụ tiếng Trung kèm nghĩa tiếng Việt nếu cần.

				- Nếu sourceLanguage là zh và targetLanguage là vi:
				  + translation phải là bản dịch tiếng Việt.
				  + reading phải là pinyin đầy đủ của câu tiếng Trung gốc.
				  + naturalVersion là câu tiếng Việt tự nhiên hơn nếu cần.
				  + keyPhrases[].phrase là cụm từ tiếng Trung quan trọng.
				  + keyPhrases[].reading là pinyin.
				  + keyPhrases[].meaning là nghĩa tiếng Việt.
				  + grammarPoints[].explanation phải là tiếng Việt.

				Ví dụ:
				Input: "biết thân biết phận"
				Output đúng:
				- translation: "认清本分"
				- naturalVersion: "安分守己"
				- keyPhrases[].meaning: "nhận rõ vị trí, bổn phận của mình"
				- grammarPoints[].explanation: "Cấu trúc này diễn tả việc hiểu rõ thân phận, vị trí hoặc giới hạn của bản thân."
				Không được trả meaning bằng tiếng Anh như: "To know oneself".

				"""
				.formatted(sentence, sourceLanguage, targetLanguage);
	}

	private String buildGrammarPrompt(String text, String sourceLanguage, String targetLanguage) {
		return """
				Bạn là AI chuyên kiểm tra và sửa ngữ pháp.

				Hãy kiểm tra câu sau:
				- Câu: "%s"
				- Ngôn ngữ gốc: "%s"
				- Ngôn ngữ đích: "%s"

				Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.

				Format JSON bắt buộc:

				{
				  "type": "grammar",
				  "grammar": {
				    "originalText": "",
				    "correctedText": "",
				    "naturalText": "",
				    "translation": "",
				    "isCorrect": true,
				    "errors": [
				      {
				        "wrong": "",
				        "correct": "",
				        "reason": ""
				      }
				    ],
				    "grammarPoints": [
				      {
				        "pattern": "",
				        "meaning": "",
				        "example": ""
				      }
				    ],
				    "note": ""
				  },
				  "message": ""
				}

				Yêu cầu:
				- isCorrect là true nếu câu đúng, false nếu câu có lỗi.
				- Nếu câu sai, correctedText là câu đã sửa.
				- Nếu câu đúng, correctedText giữ nguyên.
				- naturalText là cách nói tự nhiên hơn.
				- translation dịch sang ngôn ngữ đích.
				- errors liệt kê lỗi sai nếu có.
				""".formatted(text, sourceLanguage, targetLanguage);
	}
}
