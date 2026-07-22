export function getApiBaseUrl() {
  return (
    import.meta.env.VITE_API_BASE_URL ||
    'https://ai-dictionary-backend-36vo.onrender.com'
  );
}