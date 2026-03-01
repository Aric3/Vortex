// Frontend config (Vue 3 + Vite 5).
// Use relative '/api' so dev proxy can avoid CORS; production can set VITE_API_BASE.
export const API_BASE: string = (import.meta as any).env?.VITE_API_BASE || "/api";

export function apiUrl(path: string): string {
  if (!path.startsWith("/")) path = "/" + path;
  return `${API_BASE}${path}`;
}
