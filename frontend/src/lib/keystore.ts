/**
 * IndexedDB storage for the passphrase-encrypted private key and for pinned contact
 * fingerprints (trust-on-first-use). Nothing here is ever sent to the server.
 */

const DB_NAME = "cipherchat";
const DB_VERSION = 1;
const KEYS = "keys";
const CONTACTS = "contacts";

export interface StoredKey {
  username: string;
  publicKey: string;
  encryptedPrivateKey: string;
  fingerprint: string;
  createdAt: string;
}

export interface PinnedContact {
  /** "<owner>:<peer>" so several local accounts don't share trust decisions. */
  id: string;
  fingerprint: string;
  verified: boolean;
  firstSeen: string;
}

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(KEYS)) db.createObjectStore(KEYS, { keyPath: "username" });
      if (!db.objectStoreNames.contains(CONTACTS)) db.createObjectStore(CONTACTS, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function tx<T>(store: string, mode: IDBTransactionMode, run: (s: IDBObjectStore) => IDBRequest<T>) {
  const db = await open();
  try {
    return await new Promise<T>((resolve, reject) => {
      const request = run(db.transaction(store, mode).objectStore(store));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } finally {
    db.close();
  }
}

export const keystore = {
  get: (username: string) => tx<StoredKey | undefined>(KEYS, "readonly", (s) => s.get(username)),
  put: (key: StoredKey) => tx(KEYS, "readwrite", (s) => s.put(key)),
  remove: (username: string) => tx(KEYS, "readwrite", (s) => s.delete(username)),
};

export const contacts = {
  get: (owner: string, peer: string) =>
    tx<PinnedContact | undefined>(CONTACTS, "readonly", (s) => s.get(`${owner}:${peer}`)),
  put: (owner: string, peer: string, fingerprint: string, verified: boolean) =>
    tx(CONTACTS, "readwrite", (s) =>
      s.put({ id: `${owner}:${peer}`, fingerprint, verified, firstSeen: new Date().toISOString() }),
    ),
};
