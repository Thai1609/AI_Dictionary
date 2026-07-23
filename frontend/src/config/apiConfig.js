export function getApiBaseUrl() {
  // Return empty string to route requests to the local Express server,
  // which will proxy them to the external Spring Boot backend via http-proxy-middleware.
  return '';
}