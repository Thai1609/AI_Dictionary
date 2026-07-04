import { getApiBaseUrl } from '../config/apiConfig';

function normalizeBaseUrl() {
  return (getApiBaseUrl() || 'http://localhost:8080').trim().replace(/\/+$/, '');
}

function buildJsonHeaders() {
  return {
    'Content-Type': 'application/json',
    'ngrok-skip-browser-warning': 'true',
  };
}

function buildGetHeaders() {
  return {
    'ngrok-skip-browser-warning': 'true',
  };
}

/**
 * Health check for the dictionary API.
 */
export async function checkHealth() {
  try {
    const baseUrl = normalizeBaseUrl();

    const response = await fetch(`${baseUrl}/api/dictionary/health`, {
      method: 'GET',
      headers: buildGetHeaders(),
    });

    if (!response.ok) {
      throw new Error(`Health check failed with status ${response.status}`);
    }

    const contentType = response.headers.get('content-type');

    if (contentType && contentType.includes('application/json')) {
      return await response.json();
    }

    const text = await response.text();

    return {
      status: 'ok',
      message: text,
    };
  } catch (error) {
    console.error('Health check error:', error);
    throw error;
  }
}

/**
 * Search dictionary from backend database.
 */
export async function searchDictionary(keyword) {
  const cleanKeyword = keyword?.trim();

  if (!cleanKeyword) {
    throw new Error("Vui lòng nhập từ khóa cần tìm.");
  }

  const baseUrl = getApiBaseUrl().replace(/\/+$/, "");

  const params = new URLSearchParams();
  params.append("keyword", cleanKeyword);

  const response = await fetch(
    `${baseUrl}/api/dictionary/search?${params.toString()}`,
    {
      method: "GET",
      headers: {
        "ngrok-skip-browser-warning": "true",
      },
    }
  );

  if (!response.ok) {
    let errorMessage = `Không thể tìm kiếm trong database. Status: ${response.status}`;

    try {
      const errorText = await response.text();
      if (errorText) {
        errorMessage = errorText;
      }
    } catch (e) {
      // giữ error mặc định
    }

    throw new Error(errorMessage);
  }

  return response.json();
}

/**
 * Analyze word, sentence, or grammar.
 */
export async function analyzeText({ text, mode, sourceLanguage, targetLanguage }) {
  const effectiveMode = mode === 'search' ? 'word' : mode;

  try {
    const baseUrl = normalizeBaseUrl();

    const response = await fetch(`${baseUrl}/api/dictionary/analyze`, {
      method: 'POST',
      headers: buildJsonHeaders(),
      body: JSON.stringify({
        text,
        mode: effectiveMode,
        sourceLanguage,
        targetLanguage,
      }),
    });

    if (!response.ok) {
      let errorMessage = `Yêu cầu thất bại với trạng thái ${response.status}`;

      try {
        const errorText = await response.text();

        try {
          const errorJson = JSON.parse(errorText);

          if (errorJson && errorJson.message) {
            errorMessage = errorJson.message;
          }
        } catch (e) {
          if (errorText) {
            errorMessage = errorText;
          }
        }
      } catch (e) {
        // giữ error mặc định
      }

      throw new Error(errorMessage);
    }

    const data = await response.json();

    if (
      data &&
      data.message &&
      data.dictionary === undefined &&
      data.grammar === undefined
    ) {
      if (data.message.includes('429') || data.message.includes('Too Many Requests')) {
        throw new Error(
          'Hệ thống AI đang quá tải (429 Too Many Requests). Vui lòng thử lại sau ít phút hoặc sử dụng các từ đã lưu trong Database.'
        );
      }

      throw new Error(data.message);
    }

    return data;
  } catch (error) {
    console.error('Analyze text error:', error);

    if (
      error.message &&
      (error.message.includes('429') || error.message.includes('Too Many Requests'))
    ) {
      throw new Error(
        'Hệ thống AI đang quá tải (429 Too Many Requests). Vui lòng thử lại sau ít phút hoặc sử dụng các từ đã lưu trong Database.'
      );
    }

    throw error;
  }
}

/**
 * Save word into backend database.
 *
 * Quan trọng:
 * - searchKeyword là keyword gốc người dùng nhập.
 * - Ví dụ user nhập "du lịch", AI trả về "旅游"
 * - Khi save phải gửi:
 *   searchKeyword = "du lịch"
 *   dictionary.word = "旅游"
 */
export async function saveWord({
  type,
  sourceLanguage,
  targetLanguage,
  searchKeyword,
  dictionary,
}) {
  try {
    if (!dictionary) {
      throw new Error('Không có dữ liệu từ để lưu.');
    }

    const baseUrl = normalizeBaseUrl();

    const response = await fetch(`${baseUrl}/api/dictionary/save`, {
      method: 'POST',
      headers: buildJsonHeaders(),
      body: JSON.stringify({
        type,
        sourceLanguage,
        targetLanguage,
        searchKeyword,
        dictionary,
      }),
    });

    if (!response.ok) {
      let errorMessage = `Không thể lưu từ, lỗi trạng thái ${response.status}`;

      try {
        const errorText = await response.text();

        try {
          const errorJson = JSON.parse(errorText);

          if (errorJson && errorJson.message) {
            errorMessage = errorJson.message;
          }
        } catch (e) {
          if (errorText) {
            errorMessage = errorText;
          }
        }
      } catch (e) {
        // giữ error mặc định
      }

      throw new Error(errorMessage);
    }

    const result = await response.json();

    return result;
  } catch (error) {
    console.error('Save word error:', error);
    throw error;
  }
}