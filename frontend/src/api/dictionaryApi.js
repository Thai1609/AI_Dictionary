import { getApiBaseUrl } from '../config/apiConfig';

function normalizeBaseUrl() {
  return (getApiBaseUrl())
    .trim()
    .replace(/\/+$/, '');
}

function buildJsonHeaders() {
  return {
    'Content-Type': 'application/json',
  };
}

function buildGetHeaders() {
  return {};
}

export async function checkHealth() {
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
    return response.json();
  }

  const text = await response.text();

  return {
    status: 'ok',
    message: text,
  };
}

export async function searchDictionary(keyword) {
  const cleanKeyword = keyword?.trim();

  if (!cleanKeyword) {
    throw new Error('Vui lòng nhập từ khóa cần tìm.');
  }

  const baseUrl = normalizeBaseUrl();

  const params = new URLSearchParams();
  params.append('keyword', cleanKeyword);

  const response = await fetch(
    `${baseUrl}/api/dictionary/search?${params.toString()}`,
    {
      method: 'GET',
      headers: buildGetHeaders(),
    }
  );

  if (!response.ok) {
    const errorText = await response.text().catch(() => '');
    throw new Error(errorText || `Không thể tìm kiếm trong database. Status: ${response.status}`);
  }

  return response.json();
}

export async function analyzeText({ text, mode, sourceLanguage, targetLanguage }) {
  const effectiveMode = mode === 'search' ? 'word' : mode;
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
    const errorText = await response.text().catch(() => '');
    let errorMessage = `Yêu cầu thất bại với trạng thái ${response.status}`;

    try {
      const errorJson = JSON.parse(errorText);
      errorMessage = errorJson.message || errorMessage;
    } catch {
      errorMessage = errorText || errorMessage;
    }

    if (errorMessage.includes('429') || errorMessage.includes('Too Many Requests')) {
      throw new Error(
        'Hệ thống AI đang quá tải (429 Too Many Requests). Vui lòng thử lại sau ít phút hoặc sử dụng các từ đã lưu trong Database.'
      );
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
    throw new Error(data.message);
  }

  return data;
}

export async function saveWord({
  type,
  sourceLanguage,
  targetLanguage,
  searchKeyword,
  dictionary,
}) {
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
    const errorText = await response.text().catch(() => '');
    let errorMessage = `Không thể lưu từ, lỗi trạng thái ${response.status}`;

    try {
      const errorJson = JSON.parse(errorText);
      errorMessage = errorJson.message || errorMessage;
    } catch {
      errorMessage = errorText || errorMessage;
    }

    throw new Error(errorMessage);
  }

  return response.json();
}
