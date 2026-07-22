package com.example.aidictionary.service;

import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.gemini.GeminiRequest;
import com.example.aidictionary.dto.gemini.GeminiResponse;
import com.example.aidictionary.exception.GeminiServiceException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class AiService {

    private final WebClient geminiWebClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public AiService(@Qualifier("geminiWebClient") WebClient geminiWebClient) {
        this.geminiWebClient = geminiWebClient;
    }
    
    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Value("${gemini.fallback-model:gemini-2.5-flash}")
    private String geminiFallbackModel;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${gemini.timeout-ms:20000}")
    private long timeoutMs;

    public AnalyzeResponse analyzeWord(String word, String sourceLanguage, String targetLanguage) {
        return callGeminiAndParse(buildWordPrompt(word, sourceLanguage, targetLanguage), "word");
    }

    public AnalyzeResponse analyzeSentence(String sentence, String sourceLanguage, String targetLanguage) {
        return callGeminiAndParse(buildSentencePrompt(sentence, sourceLanguage, targetLanguage), "sentence");
    }

    public AnalyzeResponse checkGrammar(String text, String sourceLanguage, String targetLanguage) {
        return callGeminiAndParse(buildGrammarPrompt(text, sourceLanguage, targetLanguage), "grammar");
    }

    private AnalyzeResponse callGeminiAndParse(String prompt, String type) {
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
            boolean canFallback = isQuotaError(firstError)
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

        String aiText = geminiResponse.getFirstText();
        try {
            AnalyzeResponse response = jsonMapper.readValue(
                    cleanJson(aiText),
                    AnalyzeResponse.class
            );
            if (response.getType() == null || response.getType().isBlank()) {
                response.setType(type);
            }
            response.setRawAiResponse(aiText);
            return response;
        } catch (JacksonException exception) {
            throw new GeminiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "Gemini trả về JSON không hợp lệ.",
                    exception
            );
        }
    }

    private GeminiResponse callGemini(String prompt, String model) {
        String url = geminiBaseUrl
                + "/models/"
                + model
                + ":generateContent?key="
                + geminiApiKey;

        try {
            return geminiWebClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new GeminiRequest(prompt))
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, 1000L)))
                    .block();
        } catch (WebClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().value() == 429
                    ? HttpStatus.TOO_MANY_REQUESTS
                    : HttpStatus.BAD_GATEWAY;
            throw new GeminiServiceException(
                    status,
                    "Gemini API trả về lỗi HTTP " + exception.getStatusCode().value() + ".",
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

    private boolean isQuotaError(GeminiServiceException exception) {
        return exception.getStatus() == HttpStatus.TOO_MANY_REQUESTS
                || (exception.getMessage() != null
                && exception.getMessage().toLowerCase().contains("quota"));
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

    private String buildWordPrompt(String word, String sourceLanguage, String targetLanguage) {
        return """
                Bạn là chuyên gia từ điển tiếng Việt - tiếng Trung dành cho người Việt học tiếng Trung.

                Hãy phân tích từ hoặc cụm từ sau:
                - Nội dung người dùng nhập: "%s"
                - Ngôn ngữ nguồn: "%s"
                - Ngôn ngữ đích: "%s"

                Chỉ trả về một JSON hợp lệ. Không dùng markdown và không viết nội dung ngoài JSON.

                Format JSON bắt buộc:
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
                    "translations": [
                      {
                        "word": "",
                        "pronunciation": "",
                        "reading": "",
                        "partOfSpeech": "",
                        "meaning": "",
                        "usage": "",
                        "examples": [
                          {
                            "sentence": "",
                            "reading": "",
                            "translation": ""
                          }
                        ]
                      }
                    ],
                    "recommendation": {
                      "defaultWord": "",
                      "reason": ""
                    },
                    "note": ""
                  },
                  "message": ""
                }

                Quy tắc bắt buộc:
                - Chỉ xử lý tiếng Việt và tiếng Trung.
                - Nếu một từ nguồn có nhiều cách dịch tiếng Trung thông dụng, phải trả về tất cả cách dịch quan trọng trong translations.
                - Không gộp các từ khác ngữ cảnh thành một nghĩa duy nhất.
                - Mỗi translation phải giải thích rõ nghĩa và hoàn cảnh sử dụng bằng tiếng Việt.
                - Với bản dịch tiếng Trung, word dùng chữ Hán phồn thể; pronunciation và reading dùng pinyin có dấu thanh.
                - translations nên có từ 1 đến 5 lựa chọn thông dụng, sắp xếp từ phù hợp/phổ biến nhất đến ít phổ biến hơn.
                - dictionary.word phải bằng recommendation.defaultWord và là lựa chọn mặc định phù hợp nhất.
                - dictionary.pronunciation, reading, partOfSpeech, meanings và examples mô tả từ mặc định.
                - partOfSpeech ghi bằng tiếng Việt.
                - examples phải gần đời sống, học tập hoặc công việc.
                - examples[].sentence là tiếng Trung nếu từ đích là tiếng Trung.
                - examples[].reading là pinyin đầy đủ có dấu thanh.
                - examples[].translation là bản dịch tiếng Việt tự nhiên.
                - relatedWords có thể gồm chữ Hán + pinyin + nghĩa tiếng Việt.
                - note nêu khác biệt dễ nhầm hoặc mẹo sử dụng.

                Ví dụ về nguyên tắc phân biệt:
                - "bảo vệ" có thể gồm 保安 (nhân viên bảo vệ), 保衛 (bảo vệ/phòng vệ quốc gia), 保護 (giữ khỏi bị tổn hại).
                - Không được chỉ trả về 保護 nếu các nghĩa còn lại phù hợp và thông dụng.

                Nếu nguồn là tiếng Trung và đích là tiếng Việt:
                - dictionary.word là bản dịch tiếng Việt mặc định.
                - translations liệt kê các nghĩa tiếng Việt khác nhau theo ngữ cảnh.
                - pronunciation và reading có thể dùng pinyin của từ tiếng Trung gốc.

                Nếu nguồn và đích cùng ngôn ngữ:
                - Vẫn phân tích nghĩa, từ loại, ví dụ và các cách dùng khác nhau.
                """.formatted(word, sourceLanguage, targetLanguage);
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

            """.formatted(sentence, sourceLanguage, targetLanguage);
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
