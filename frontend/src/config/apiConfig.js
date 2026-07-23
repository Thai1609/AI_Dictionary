export function getApiBaseUrl() {
  // Nếu có cấu hình VITE_API_BASE_URL (ví dụ trên Vercel), sẽ dùng đường dẫn đó gọi thẳng tới backend.
  // Ngược lại (chạy local ở AI Studio), trả về rỗng '' để gọi qua proxy của server.ts
  return import.meta.env.VITE_API_BASE_URL || '';
}
