/** Typed REST client for the CipherChat backend. */

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  hasPublicKey: boolean;
  fingerprint?: string;
  hasKeyBackup: boolean;
}

export interface KeyBackupResponse {
  fingerprint: string;
  /** Private key still locked with the user's passphrase. */
  keyBackup: string;
  updatedAt: string;
}

export interface KeyResponse {
  username: string;
  publicKey: string;
  fingerprint: string;
  algorithm: string;
  keyCreatedAt: string;
  uploadedAt: string;
}

export interface UserSummary {
  username: string;
  hasPublicKey: boolean;
  fingerprint?: string;
}

export interface ConversationResponse {
  id: number;
  peer: UserSummary;
  createdAt: string;
  lastMessageAt?: string;
}

export interface MessageResponse {
  id: number;
  conversationId: number;
  sender: string;
  ciphertext: string;
  attachmentId?: string;
  createdAt: string;
}

export interface AttachmentResponse {
  id: string;
  conversationId: number;
  sizeBytes: number;
  createdAt: string;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

export function createApi(baseUrl: string, getToken: () => string | null, onUnauthorized: () => void) {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    const token = getToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");

    let response: Response;
    try {
      response = await fetch(baseUrl + path, { ...init, headers, cache: "no-store" });
    } catch {
      throw new ApiError(0, "Cannot reach the server. Check your connection.");
    }
    if (response.status === 401 && token) onUnauthorized();
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new ApiError(response.status, body?.message ?? `Request failed (${response.status})`, body?.fieldErrors);
    }
    if (response.headers.get("Content-Type")?.includes("application/json")) {
      return response.json() as Promise<T>;
    }
    return new Uint8Array(await response.arrayBuffer()) as T;
  }

  const json = (body: unknown) => JSON.stringify(body);

  return {
    register: (username: string, password: string, publicKey: string, keyBackup: string) =>
      request<AuthResponse>("/api/auth/register", {
        method: "POST",
        body: json({ username, password, publicKey, keyBackup }),
      }),
    login: (username: string, password: string) =>
      request<AuthResponse>("/api/auth/login", { method: "POST", body: json({ username, password }) }),
    uploadKey: (publicKey: string) =>
      request<KeyResponse>("/api/keys/me", { method: "PUT", body: json({ publicKey }) }),
    uploadKeyBackup: (keyBackup: string) =>
      request<KeyBackupResponse>("/api/keys/me/backup", { method: "PUT", body: json({ keyBackup }) }),
    getKeyBackup: () => request<KeyBackupResponse>("/api/keys/me/backup"),
    getKey: (username: string) => request<KeyResponse>(`/api/keys/${encodeURIComponent(username)}`),
    searchUsers: (query: string) => request<UserSummary[]>(`/api/users?query=${encodeURIComponent(query)}`),
    conversations: () => request<ConversationResponse[]>("/api/conversations"),
    startConversation: (username: string) =>
      request<ConversationResponse>("/api/conversations", { method: "POST", body: json({ username }) }),
    messages: (conversationId: number, before?: number) =>
      request<MessageResponse[]>(
        `/api/conversations/${conversationId}/messages?size=50${before ? `&before=${before}` : ""}`,
      ),
    sendMessage: (recipientUsername: string, ciphertext: string, attachmentId?: string) =>
      request<MessageResponse>("/api/messages", {
        method: "POST",
        body: json({ recipientUsername, ciphertext, attachmentId }),
      }),
    uploadAttachment: (recipientUsername: string, encrypted: Uint8Array) => {
      const form = new FormData();
      form.append("recipientUsername", recipientUsername);
      form.append("file", new Blob([encrypted as BlobPart], { type: "application/octet-stream" }), "attachment.pgp");
      return request<AttachmentResponse>("/api/attachments", { method: "POST", body: form });
    },
    downloadAttachment: (id: string) => request<Uint8Array>(`/api/attachments/${encodeURIComponent(id)}`),
  };
}

export type Api = ReturnType<typeof createApi>;
