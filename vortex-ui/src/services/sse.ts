import { apiUrl } from "./config";

export type SSEOptions = {
  withCredentials?: boolean;
};

/**
 * Create a browser-native SSE connection.
 * Caller MUST close the returned EventSource when component unmounts.
 */
export function createSSE(path: string, opts: SSEOptions = {}): EventSource {
  const url = apiUrl(path);
  // withCredentials is not supported in all browsers; keep optional.
  // @ts-ignore
  return new EventSource(url, { withCredentials: !!opts.withCredentials });
}

export function safeJsonParse<T = any>(raw: string): T | null {
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}
