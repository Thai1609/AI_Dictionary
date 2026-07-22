# Dictionary translation groups

Các file đã cập nhật:

- `DictionaryResponse.java`: thêm `translationGroups`, mỗi nhóm chứa các từ cùng loại từ.
- `AiService.java`: prompt từ điển yêu cầu Gemini trả nhiều từ theo loại từ và giữ đầy đủ cấu trúc từng từ.
- `DictionaryService.java`: nhận diện cụm ngắn như `bảo vệ` là `word`; kiểm tra dữ liệu theo `translationGroups`.
- `DictionaryPersistenceService.java`: lưu và đọc `translationGroups` trong cột `translations_json`; bổ sung toàn bộ dữ liệu nhóm vào `search_text`.

Không cần thêm cột database nếu project đã có:

- `translations_json`
- `recommendation_json`

Request nên gửi rõ mode khi tra từ:

```json
{
  "text": "bảo vệ",
  "mode": "word",
  "sourceLanguage": "vi",
  "targetLanguage": "zh"
}
```

Nếu không truyền mode, backend sẽ coi cụm tối đa 4 từ và tối đa 40 ký tự, không có dấu kết câu, là từ/cụm từ.
