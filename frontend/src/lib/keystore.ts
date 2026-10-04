/**
 * IndexedDB storage for this device's wrapped private keys and for pinned contact fingerprints
 * (trust-on-first-use). Nothing here is ever sent to the server.
 */

const DB_NAME = "cipherchat";
const DB_VERSION = 2;
const KEYS = "keys";
const CONTACTS = "contacts";

/**
 * One account's private key on this device, encrypted ("wrapped") with a device key.
 * The device key is a non-extractable WebCrypto key: the browser keeps its bytes and only lets
 * this site use it to encrypt and decrypt, so it can never be read or exported, even by our own code.
 */
export interface DeviceKeyRecord {
  username: string;
  publicKey: string;
  fingerprint: string;
  deviceKey: CryptoKey;
  iv: Uint8Array;
  wrappedPrivateKey: ArrayBuffer;
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
    request.onupgradeneeded = (event) => {
      const db = request.result;
      // Version 1 stored passphrase-locked keys that had to be unlocked on every login.
      // They are replaced by device-wrapped keys, set up again with the passphrase on next sign-in.
      if (event.oldVersion < 2 && db.objectStoreNames.contains(KEYS)) db.deleteObjectStore(KEYS);
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
  get: (username: string) => tx<DeviceKeyRecord | undefined>(KEYS, "readonly", (s) => s.get(username)),
  put: (record: DeviceKeyRecord) => tx(KEYS, "readwrite", (s) => s.put(record)),
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
