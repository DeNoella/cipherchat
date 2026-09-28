// Server-only: resolved per request so one Docker image works in any environment.
export function apiBaseUrl(): string {
  return (process.env.API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

export function wsUrl(api: string): string {
  return api.replace(/^http/, "ws") + "/ws";
}
