package com.example.aidictionary.service;

import com.example.aidictionary.dto.AnalyzeResponse;
import com.example.aidictionary.dto.gemini.GeminiRequest;
import com.example.aidictionary.dto.gemini.GeminiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class AiService {

    private final WebClient webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.fallback-model:gemini-2.5-flash}")
    private String geminiFallbackModel;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    public AnalyzeResponse analyzeWord(String word, String sourceLanguage, String targetLanguage) {
        String prompt = buildWordPrompt(word, sourceLanguage, targetLanguage);
        return callGeminiAndParse(prompt, "word");
    }

    public AnalyzeResponse analyzeSentence(String sentence, String sourceLanguage, String targetLanguage) {
        String prompt = buildSentencePrompt(sentence, sourceLanguage, targetLanguage);
        return callGeminiAndParse(prompt, "sentence");
    }

    public AnalyzeResponse checkGrammar(String text, String sourceLanguage, String targetLanguage) {
        String prompt = buildGrammarPrompt(text, sourceLanguage, targetLanguage);
        return callGeminiAndParse(prompt, "grammar");
    }

    private AnalyzeResponse callGeminiAndParse(String prompt, String type) {
        try {
            if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
                return errorResponse(type, "Gemini API key chưa được cấu hình.");
            }

            GeminiResponse geminiResponse;

            try {
                geminiResponse = callGemini(prompt, geminiModel);
            } catch (Exception firstError) {
                String errorMessage = firstError.getMessage();

                if (errorMessage != null &&
                        (errorMessage.contains("429")
                                || errorMessage.contains("RESOURCE_EXHAUSTED")
                                || errorMessage.toLowerCase().contains("quota"))) {
                    geminiResponse = callGemini(prompt, geminiFallbackModel);
                } else {
                    throw firstError;
                }
            }

            if (geminiResponse == null || geminiResponse.getFirstText() == null) {
                return errorResponse(type, "Gemini không trả về dữ liệu.");
            }

            String aiText = geminiResponse.getFirstText();
            String cleanJson = cleanJson(aiText);

            AnalyzeResponse response = objectMapper.readValue(cleanJson, AnalyzeResponse.class);

            if (response.getType() == null || response.getType().trim().isEmpty()) {
                response.setType(type);
            }

            response.setRawAiResponse(aiText);

            return response;

        } catch (Exception e) {
            return AnalyzeResponse.builder()
                    .type(type)
                    .message("Lỗi khi gọi Gemini AI: " + e.getMessage())
                    .build();
        }
    }

    private GeminiResponse callGemini(String prompt, String model) {
        String url = geminiBaseUrl
                + "/models/"
                + model
                + ":generateContent?key="
                + geminiApiKey;

        GeminiRequest request = new GeminiRequest(prompt);

        return webClientBuilder
                .post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .block();
    }

    private AnalyzeResponse errorResponse(String type, String message) {
        return AnalyzeResponse.builder()
                .type(type)
                .message(message)
                .build();
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

        int firstBrace = clean.indexOf("{");
        int lastBrace = clean.lastIndexOf("}");

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1);
        }

        return clean.trim();
    }

    private String buildWordPrompt(String word, String sourceLanguage, String targetLanguage) {
        return """
                Bạn là AI Dictionary dành cho người học tiếng Việt và tiếng Trung.

                Hãy phân tích từ sau:
                - Từ người dùng nhập: "%s"
                - Ngôn ngữ người dùng nhập: "%s"
                - Ngôn ngữ muốn trả về/học: "%s"

                Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.

                Format JSON bắt buộc:

                {
                  "type": "word",
                  "dictionary": {
                    "word": "",
                    "pronunciation": "",
                    "reading": "",
                    "partOfSpeech": "",
                    "meanings": [
                      ""
                    ],
                    "examples": [
                      {
                        "sentence": "",
                        "reading": "",
                        "translation": ""
                      }
                    ],
                    "relatedWords": [
                      ""
                    ],
                    "note": ""
                  },
                  "message": ""
                }

                Quy tắc chung:
                - Chỉ xử lý 2 ngôn ngữ: tiếng Việt và tiếng Trung.
                - meanings luôn giải thích bằng tiếng Việt, dễ hiểu cho người Việt.
                - partOfSpeech ghi bằng tiếng Việt, ví dụ: danh từ, động từ, tính từ, cụm danh từ.
                - examples phải thực tế, gần đời sống, học tập, công việc hoặc giao tiếp hằng ngày.
                - note viết bằng tiếng Việt, gồm mẹo học, cách dùng, lỗi thường gặp hoặc từ dễ nhầm.
                - Nếu có nhiều nghĩa, trả về 2 đến 5 nghĩa phổ biến nhất.

                Trường hợp 1: sourceLanguage là vi hoặc tiếng Việt, targetLanguage là zh, zh-CN, Chinese hoặc tiếng Trung
                - Người dùng nhập tiếng Việt và muốn học tiếng Trung.
                - dictionary.word phải là chữ Hán tiếng Trung tương ứng.
                - pronunciation phải là pinyin có dấu thanh.
                - reading phải là pinyin có dấu thanh, có thể giống pronunciation.
                - meanings giải thích nghĩa bằng tiếng Việt.
                - examples[].sentence phải là câu tiếng Trung.
                - examples[].reading phải là pinyin đầy đủ của câu tiếng Trung.
                - examples[].translation phải là bản dịch tiếng Việt tự nhiên.
                - relatedWords nên gồm: chữ Hán + pinyin + nghĩa tiếng Việt.
                - Không được tạo examples[].sentence bằng tiếng Việt.

                Ví dụ:
                Nếu input là "trường học":
                - dictionary.word: "学校"
                - pronunciation: "xué xiào"
                - reading: "xué xiào"
                - examples[].sentence: "我每天去学校。"
                - examples[].reading: "wǒ měi tiān qù xué xiào."
                - examples[].translation: "Tôi đi đến trường mỗi ngày."

                Trường hợp 2: sourceLanguage là zh, zh-CN, Chinese hoặc tiếng Trung, targetLanguage là vi hoặc tiếng Việt
                - Người dùng nhập tiếng Trung và muốn hiểu nghĩa tiếng Việt.
                - dictionary.word phải là từ/cụm từ tiếng Việt tương ứng.
                - pronunciation để trống.
                - reading để trống.
                - meanings giải thích nghĩa bằng tiếng Việt.
                - examples[].sentence phải là câu ví dụ tiếng Việt.
                - examples[].reading để trống.
                - examples[].translation để trống hoặc null, vì không cần dịch tiếng Việt sang tiếng Việt.
                - relatedWords nên là các từ tiếng Việt liên quan, đồng nghĩa, trái nghĩa hoặc cụm từ hay đi chung.
                - Không cần pinyin trong trường hợp trả về tiếng Việt.

                Ví dụ:
                Nếu input là "学校":
                - dictionary.word: "trường học"
                - pronunciation: ""
                - reading: ""
                - examples[].sentence: "Tôi đi đến trường mỗi ngày."
                - examples[].reading: ""
                - examples[].translation: ""

                Trường hợp 3: sourceLanguage và targetLanguage đều là vi hoặc đều là zh
                - Nếu cả hai đều là tiếng Việt: phân tích từ tiếng Việt bằng tiếng Việt.
                - Nếu cả hai đều là tiếng Trung: phân tích từ tiếng Trung, pronunciation và reading là pinyin, meanings giải thích bằng tiếng Việt.
                - Với tiếng Trung, examples[].sentence phải là tiếng Trung, examples[].reading là pinyin, examples[].translation là tiếng Việt.

                Yêu cầu chất lượng:
                - Nếu trả về tiếng Trung, luôn có chữ Hán và pinyin có dấu thanh.
                - Nếu trả về tiếng Việt, không cần pinyin.
                - Không tự tạo cách đọc tiếng Việt sai dấu.
                - Không đặt pinyin trong translation; pinyin phải nằm ở reading.
                - translation chỉ dùng để dịch câu ví dụ sang tiếng Việt khi examples[].sentence là tiếng Trung.
                """.formatted(word, sourceLanguage, targetLanguage);
    }

    private String buildSentencePrompt(String sentence, String sourceLanguage, String targetLanguage) {
        return """
                Bạn là AI Dictionary chuyên phân tích câu.

                Hãy phân tích câu sau:
                - Câu: "%s"
                - Ngôn ngữ gốc: "%s"
                - Ngôn ngữ đích: "%s"

                Chỉ trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.

                Format JSON bắt buộc:

                {
                  "type": "sentence",
                  "dictionary": {
                    "originalSentence": "",
                    "translation": "",
                    "naturalVersion": "",
                    "keyPhrases": [
                      {
                        "phrase": "",
                        "meaning": "",
                        "note": ""
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
                - translation dịch sang ngôn ngữ đích.
                - naturalVersion là cách nói tự nhiên hơn nếu cần.
                - keyPhrases giải thích các cụm từ quan trọng trong câu.
                - grammarPoints giải thích ngữ pháp chính.
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
                - Nếu câu sai, correctedText là câu đã sửa.
                - Nếu câu đúng, correctedText giữ nguyên.
                - naturalText là cách nói tự nhiên hơn.
                - translation dịch sang ngôn ngữ đích.
                - errors liệt kê lỗi sai nếu có.
                """.formatted(text, sourceLanguage, targetLanguage);
    }
}