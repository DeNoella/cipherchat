# API Testing Guide

Every CipherChat endpoint, in the order you would naturally test them: **register → login → upload key → key backup → find users → start a conversation → upload an attachment → send a message → read history → download → WebSocket**.

All example responses below were captured from a real run against `docker compose up`. Tokens, keys and ciphertext are shortened with `…`.

- [Before you start](#before-you-start)
- [Error format](#error-format-all-endpoints)
- [1. Register](#1-register) · [2. Login](#2-login) · [3. Upload your public key](#3-upload-your-public-key) · [3a. Upload your key backup](#3a-upload-your-key-backup) · [3b. Download your key backup](#3b-download-your-key-backup-new-device) · [4. Get a user's public key](#4-get-a-users-public-key) · [5. Search users](#5-search-users) · [6. Start a conversation](#6-start-a-conversation) · [7. List conversations](#7-list-conversations) · [8. Upload an attachment](#8-upload-an-encrypted-attachment) · [9. Send a message](#9-send-a-message) · [10. Read history](#10-read-message-history) · [11. Download an attachment](#11-download-an-attachment) · [12. WebSocket](#12-real-time-delivery-websocket--stomp) · [13. Health](#13-health-check)
- [Testing with Swagger UI](#testing-with-swagger-ui) · [Testing with Postman](#testing-with-postman)

---

## Before you start

1. Start the stack: `cp .env.example .env && docker compose up --build` (or run the backend manually, see the README). Put random values in `.env` first; the stack includes a dev-mode Vault that the backend needs to start.
2. Run the commands from the **repository root**, so the sample files in [`docs/samples/`](docs/samples) resolve.
3. You need `curl` and [`jq`](https://jqlang.github.io/jq/).

```bash
export API=http://localhost:8080
```

### About the sample files

The server **refuses plaintext**. Keys must be real OpenPGP public keys, and messages must be real OpenPGP ciphertext addressed to both participants' keys. To let you test without any PGP tooling, `docs/samples/` contains throwaway material generated with OpenPGP.js (the same library the web app uses):

| File | What it is |
|---|---|
| `alice.pub.asc` | Alice's public key |
| `bob.pub.asc` | Bob's public key |
| `alice-to-bob.asc` | An armored message signed by Alice, encrypted to Alice **and** Bob |
| `alice-to-bob.bin` | A binary encrypted "file" (for attachments), encrypted to Alice and Bob |
| `dave.pub.asc` | Dave's public key (for the key backup steps) |
| `dave.key-backup.asc` | Dave's private key **locked with the passphrase** `test passphrase dave`: what the browser uploads as a key backup |

> Want to use your own key instead? With GnuPG: `gpg --quick-gen-key you ed25519 sign never`, then `gpg --quick-add-key <FPR> cv25519 encr never`, then `gpg --armor --export you`. To encrypt a message: `echo hi | gpg --armor --encrypt --sign -r bob -r you`.

---

## Error format (all endpoints)

Every error, including 401/403 from the security layer, has the same JSON shape and never contains stack traces:

```json
{
  "timestamp": "2026-09-28T14:00:20.686228012Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": { "password": "must be 10-72 characters" }
}
```

`fieldErrors` is only present for validation failures. To avoid leaking which ids exist, resources you are not allowed to see return **404**, not 403.

---

## 1. Register

Creates an account and returns a JWT. `publicKey` and `keyBackup` are optional (the web app sends both; here we add Alice's key in step 3, register Bob with his key directly, and Dave with key + backup in step 3a).

| | |
|---|---|
| **Method / URL** | `POST /api/auth/register` |
| **Headers** | `Content-Type: application/json` |
| **Auth** | none |

**Request body**

```json
{ "username": "alice", "password": "correct horse battery" }
```

Rules: username is 3–32 characters from `[A-Za-z0-9_]` (stored lowercase); password is 10–72 characters; `publicKey` is an optional ASCII-armored public key; `keyBackup` is an optional passphrase-locked private key that must match `publicKey` (see [3a](#3a-upload-your-key-backup)).

**curl**

```bash
curl -s -X POST $API/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct horse battery"}' | jq

# Bob, registered together with his public key:
curl -s -X POST $API/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg k "$(cat docs/samples/bob.pub.asc)" \
        '{username:"bob",password:"bob password 123",publicKey:$k}')" | jq
```

**Expected: `201 Created`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresIn": 43200,
  "username": "alice",
  "hasPublicKey": false,
  "hasKeyBackup": false
}
```

With a key (Bob):

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9…", "tokenType": "Bearer", "expiresIn": 43200,
  "username": "bob", "hasPublicKey": true, "fingerprint": "4F31BC122A5884A3268BA26BF76507735FAC07F9",
  "hasKeyBackup": false }
```

**Common errors**

| Status | When | `message` |
|---|---|---|
| 400 | Invalid username/password | `Validation failed` + `fieldErrors` |
| 400 | `publicKey` is not a valid public key | e.g. `Malformed OpenPGP public key` |
| 400 | `keyBackup` is not passphrase-locked or does not match `publicKey` | see [3a](#3a-upload-your-key-backup) |
| 503 | Vault is unreachable while storing `keyBackup` | `Key backup storage is unavailable. Try again in a moment.` |
| 409 | Username taken (case-insensitive) | `Username is already taken` |
| 429 | More than 20 auth requests per minute from your IP | `Too many attempts. Try again later.` |

---

## 2. Login

Exchanges username and password for a JWT (valid 12 hours by default).

| | |
|---|---|
| **Method / URL** | `POST /api/auth/login` |
| **Headers** | `Content-Type: application/json` |

**curl** (saves the tokens for later steps)

```bash
export TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct horse battery"}' | jq -r .token)

export BOB_TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"bob password 123"}' | jq -r .token)

echo $TOKEN
```

**Expected: `200 OK`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresIn": 43200,
  "username": "alice",
  "hasPublicKey": false
}
```

**Common errors**

| Status | When | Response |
|---|---|---|
| 401 | Wrong password **or** unknown user (same message, so usernames can't be probed) | `Invalid username or password` |
| 429 | 5 failed logins for the same username within 1 minute; the correct password is also refused until the window resets | `Too many attempts. Try again later.` plus header `Retry-After: 57` |

**Test the rate limit:**

```bash
for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{http_code} ' -X POST $API/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"bob","password":"nope nope"}'; done; echo
curl -si -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"bob password 123"}' | grep -E "^HTTP|Retry-After"
# 401 401 401 401 401
# HTTP/1.1 429
# Retry-After: 57
```

> Wait a minute afterwards (or restart the backend) before logging in as Bob again.

---

> **All endpoints below require** `Authorization: Bearer $TOKEN`. Without it you get:
>
> ```json
> {"timestamp":"…","status":401,"error":"Unauthorized","message":"Authentication required","path":"/api/conversations"}
> ```

## 3. Upload your public key

Uploads or replaces your OpenPGP public key. The server parses it with **BouncyCastle** and checks:

- it is exactly one public key, and not a private key;
- the self-signatures verify;
- it is not revoked or expired;
- it has an encryption subkey.

It then stores a canonical copy and its fingerprint.

| | |
|---|---|
| **Method / URL** | `PUT /api/keys/me` |
| **Headers** | `Authorization: Bearer $TOKEN`, `Content-Type: application/json` |

**Request body**

```json
{ "publicKey": "-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nxjMEarplTBYJKwYBBAHaRw8BAQdA…\n-----END PGP PUBLIC KEY BLOCK-----\n" }
```

**curl**

```bash
curl -s -X PUT $API/api/keys/me \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg k "$(cat docs/samples/alice.pub.asc)" '{publicKey:$k}')" | jq
```

**Expected: `200 OK`**

```json
{
  "username": "alice",
  "publicKey": "-----BEGIN PGP PUBLIC KEY BLOCK-----…",
  "fingerprint": "7147F18A13AAF8A20B2FD9491AB1729EE3B9BB23",
  "algorithm": "EdDSA/Ed25519 + ECDH/Curve25519",
  "keyCreatedAt": "2026-09-28T13:02:04Z",
  "uploadedAt": "2026-09-28T14:00:22.900357236Z"
}
```

**Common errors**

| Status | Sent | `message` |
|---|---|---|
| 400 | A private key block | `That is a PRIVATE key. Never upload it; upload your public key only` |
| 400 | Corrupt or garbage armor | `Malformed OpenPGP public key` |
| 400 | Two keys concatenated | `Upload exactly one public key` |
| 400 | Revoked / expired / no encryption subkey / RSA < 2048 | e.g. `Key has expired` |
| 400 | Empty `publicKey` | `Validation failed`, `fieldErrors.publicKey` |
| 401 | Missing/invalid token | `Authentication required` |

```bash
# Try uploading a private key; it is rejected before anything is stored:
curl -s -X PUT $API/api/keys/me -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"publicKey":"-----BEGIN PGP PRIVATE KEY BLOCK-----\n..."}' | jq .message
```

---

## 3a. Upload your key backup

Stores your **private key locked with your key passphrase**, so you can sign in on a new device with only your password and passphrase. The web app does this automatically at sign-up (in the `keyBackup` field of register). The server **cannot open** the backup. It checks with BouncyCastle that:

- it is exactly one OpenPGP private key, and **every secret key is locked with a passphrase** (an unprotected key would be a usable private key on the server, so it is refused);
- its fingerprint matches your current public key.

Then it adds a second lock with **Vault Transit** (envelope encryption, bound to your user ID) and stores only that ciphertext (`vault:v1:…`) in the database. Replacing your public key with a different one deletes the old backup.

| | |
|---|---|
| **Method / URL** | `PUT /api/keys/me/backup` |
| **Headers** | `Authorization: Bearer $TOKEN`, `Content-Type: application/json` |

**Request body**

```json
{ "keyBackup": "-----BEGIN PGP PRIVATE KEY BLOCK-----\n\nxYYEasK0VxYJKwYBBAHaRw8BAQdA…\n-----END PGP PRIVATE KEY BLOCK-----\n" }
```

**curl** (Dave: register with public key + backup in one call, as the web app does, then replace the backup)

```bash
curl -s -X POST $API/api/auth/register -H 'Content-Type: application/json' \
  -d "$(jq -n --arg k "$(cat docs/samples/dave.pub.asc)" --arg b "$(cat docs/samples/dave.key-backup.asc)" \
        '{username:"dave",password:"dave password 123",publicKey:$k,keyBackup:$b}')" | jq
DAVE_TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"dave","password":"dave password 123"}' | jq -r .token)

curl -s -X PUT $API/api/keys/me/backup \
  -H "Authorization: Bearer $DAVE_TOKEN" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg b "$(cat docs/samples/dave.key-backup.asc)" '{keyBackup:$b}')" | jq
```

**Expected:** register returns `201` with `"hasPublicKey": true` and `"hasKeyBackup": true`; the upload returns `200 OK`:

```json
{
  "fingerprint": "75E1E4FC4428813585621268CBB5D2942BADD569",
  "keyBackup": "-----BEGIN PGP PRIVATE KEY BLOCK-----…",
  "updatedAt": "2026-10-04T21:25:55.346111066Z"
}
```

**Common errors**

| Status | Sent | `message` |
|---|---|---|
| 400 | A private key without a passphrase | `Key backup must be locked with a passphrase. Never upload an unprotected private key` |
| 400 | A backup of a different key | `Key backup does not match your public key` |
| 400 | No public key on the account yet | `Upload your public key before its backup` |
| 400 | A public key, garbage, or two keys | e.g. `Expected an ASCII-armored OpenPGP private key backup` |
| 401 | Missing/invalid token | `Authentication required` |
| 503 | Vault unreachable | `Key backup storage is unavailable. Try again in a moment.` |

To see what the database really holds (only the Vault ciphertext, never the OpenPGP block):

```bash
docker compose exec db psql -U cipherchat -d cipherchat -c \
  "select username, left(key_backup, 30) from users where key_backup is not null;"
#  dave     | vault:v1:buiRA3i0fkd9WLM5EoasT…
```

---

## 3b. Download your key backup (new device)

What the web app calls when you sign in on a browser that does not have your key yet. The server removes only the Vault layer; you get back the backup **still locked with your passphrase**, and the browser unlocks it locally. A wrong passphrase is therefore detected in the browser, never on the server.

| | |
|---|---|
| **Method / URL** | `GET /api/keys/me/backup` |
| **Headers** | `Authorization: Bearer $TOKEN` |

```bash
curl -s $API/api/keys/me/backup -H "Authorization: Bearer $DAVE_TOKEN" | jq -r .keyBackup > dave-backup.asc
# Prove it is still passphrase-locked (asks for "test passphrase dave"):
gpg --import dave-backup.asc
```

**Expected: `200 OK`** with the same JSON shape as 3a.

**Common errors**

| Status | `message` |
|---|---|
| 404 | `No key backup is stored for this account` |
| 401 | `Authentication required` |
| 503 | `Key backup storage is unavailable. Try again in a moment.` (Vault down, or the stored ciphertext cannot be decrypted) |

---

## 4. Get a user's public key

Fetches a user's key so you can encrypt to them. **Compare the fingerprint out-of-band** before trusting it.

| | |
|---|---|
| **Method / URL** | `GET /api/keys/{username}` |
| **Headers** | `Authorization: Bearer $TOKEN` |

```bash
curl -s $API/api/keys/bob -H "Authorization: Bearer $TOKEN" | jq
```

**Expected: `200 OK`**

```json
{
  "username": "bob",
  "publicKey": "-----BEGIN PGP PUBLIC KEY BLOCK-----…",
  "fingerprint": "4F31BC122A5884A3268BA26BF76507735FAC07F9",
  "algorithm": "EdDSA/Ed25519 + ECDH/Curve25519",
  "keyCreatedAt": "2026-09-28T13:02:04Z",
  "uploadedAt": "2026-09-28T14:00:20.615798Z"
}
```

**Common errors**

| Status | `message` |
|---|---|
| 404 | `User not found` |
| 404 | `User has not uploaded a public key yet` |
| 400 | Username contains invalid characters |

---

## 5. Search users

Prefix search on usernames, excluding yourself, with at most 20 results.

| | |
|---|---|
| **Method / URL** | `GET /api/users?query={prefix}` |
| **Headers** | `Authorization: Bearer $TOKEN` |

```bash
curl -s "$API/api/users?query=b" -H "Authorization: Bearer $TOKEN" | jq
```

**Expected: `200 OK`**

```json
[
  { "username": "bob", "hasPublicKey": true, "fingerprint": "4F31BC122A5884A3268BA26BF76507735FAC07F9" }
]
```

**Common errors**: `400` with `Missing parameter 'query'`, or `Validation failed` when the query contains characters other than letters, digits or `_`.

---

## 6. Start a conversation

Gets or creates the one-to-one conversation with another user. It is idempotent: both participants get the same `id`. (Sending a message or attachment also creates it implicitly.)

| | |
|---|---|
| **Method / URL** | `POST /api/conversations` |
| **Headers** | `Authorization: Bearer $TOKEN`, `Content-Type: application/json` |

```bash
curl -s -X POST $API/api/conversations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"bob"}' | jq
```

**Expected: `200 OK`**

```json
{
  "id": 1,
  "peer": { "username": "bob", "hasPublicKey": true, "fingerprint": "4F31BC122A5884A3268BA26BF76507735FAC07F9" },
  "createdAt": "2026-09-28T14:00:23.192044971Z"
}
```

**Common errors**: `404 User not found`; `400 You cannot start a conversation with yourself`.

---

## 7. List conversations

Your conversations, most recent activity first.

| | |
|---|---|
| **Method / URL** | `GET /api/conversations` |
| **Headers** | `Authorization: Bearer $TOKEN` |

```bash
curl -s $API/api/conversations -H "Authorization: Bearer $BOB_TOKEN" | jq
```

**Expected: `200 OK`** (as Bob, after step 9)

```json
[
  {
    "id": 1,
    "peer": { "username": "alice", "hasPublicKey": true, "fingerprint": "7147F18A13AAF8A20B2FD9491AB1729EE3B9BB23" },
    "createdAt": "2026-09-28T14:00:23.192045Z",
    "lastMessageAt": "2026-09-28T14:00:23.782800Z"
  }
]
```

---

## 8. Upload an encrypted attachment

Uploads a file that is **already encrypted** (a binary or armored OpenPGP message) for a conversation. The server:

- checks that the file is OpenPGP ciphertext addressed to both participants' keys;
- enforces the size limit;
- stores the blob under a random UUID.

The file name and type are never sent; they travel inside the encrypted message (step 9).

| | |
|---|---|
| **Method / URL** | `POST /api/attachments` |
| **Headers** | `Authorization: Bearer $TOKEN` (curl sets the `multipart/form-data` boundary automatically) |
| **Form fields** | `recipientUsername` (text), `file` (binary) |
| **Limit** | 10 MB plaintext (enforced in the browser); the server accepts ciphertext up to 10.5 MB to allow for OpenPGP overhead |

```bash
export ATTACHMENT_ID=$(curl -s -X POST $API/api/attachments \
  -H "Authorization: Bearer $TOKEN" \
  -F recipientUsername=bob \
  -F file=@docs/samples/alice-to-bob.bin | tee /dev/stderr | jq -r .id)
```

**Expected: `201 Created`**

```json
{
  "id": "6d94349c-1d9b-463a-8cf9-1625454c9fdb",
  "conversationId": 1,
  "sizeBytes": 2497,
  "createdAt": "2026-09-28T14:00:23.443018667Z"
}
```

**Common errors**

| Status | When | `message` |
|---|---|---|
| 400 | File is not OpenPGP ciphertext (e.g. an unencrypted file) | `Content must be an OpenPGP encrypted message` |
| 400 | Encrypted, but not to the recipient's current key | `Message is not encrypted to the recipient's current public key` |
| 400 | Empty file | `Attachment is empty` |
| 400 | Recipient has no key | `Recipient has not uploaded a public key yet` |
| 400 | `file` part missing | `Missing multipart part 'file'` |
| 413 | Over the limit | `Attachment exceeds the 10 MB limit` |

```bash
# An unencrypted file is refused:
head -c 1000 /dev/urandom > /tmp/plain.bin
curl -s -X POST $API/api/attachments -H "Authorization: Bearer $TOKEN" \
  -F recipientUsername=bob -F file=@/tmp/plain.bin | jq .message
```

---

## 9. Send a message

Stores an encrypted, signed message and pushes it to both participants over WebSocket. The server enforces the end-to-end contract: the body must be an **ASCII-armored OpenPGP message encrypted to the recipient's current key and to the sender's own key**.

| | |
|---|---|
| **Method / URL** | `POST /api/messages` |
| **Headers** | `Authorization: Bearer $TOKEN`, `Content-Type: application/json` |

**Request body**

```json
{
  "recipientUsername": "bob",
  "ciphertext": "-----BEGIN PGP MESSAGE-----\n\nwV4DEdygjOVM6GQSAQdA…\n-----END PGP MESSAGE-----\n",
  "attachmentId": "6d94349c-1d9b-463a-8cf9-1625454c9fdb"
}
```

`attachmentId` is optional. It must be an attachment **you** uploaded to **this** conversation, and not already linked to another message.

> In the web app, the decrypted plaintext is a small JSON envelope: `{"v":1,"text":"…","attachment":{"id","name","type","size"}}`.

**curl**

```bash
curl -s -X POST $API/api/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg c "$(cat docs/samples/alice-to-bob.asc)" --arg a "$ATTACHMENT_ID" \
        '{recipientUsername:"bob",ciphertext:$c,attachmentId:$a}')" | jq
```

**Expected: `201 Created`**

```json
{
  "id": 1,
  "conversationId": 1,
  "sender": "alice",
  "ciphertext": "-----BEGIN PGP MESSAGE-----…",
  "attachmentId": "6d94349c-1d9b-463a-8cf9-1625454c9fdb",
  "createdAt": "2026-09-28T14:00:23.782800479Z"
}
```

**Common errors**

| Status | When | `message` |
|---|---|---|
| 400 | Plaintext body | `Ciphertext must be an ASCII-armored OpenPGP message` |
| 400 | Signed but not encrypted | `Content must be an OpenPGP encrypted message` |
| 400 | Not encrypted to the recipient's key | `Message is not encrypted to the recipient's current public key` |
| 400 | Not encrypted to your own key | `Message must also be encrypted to your own public key` |
| 400 | You have no key / recipient has no key | `Upload your public key before sending messages` / `Recipient has not uploaded a public key yet` |
| 400 | Bad `attachmentId` | `Unknown attachment` or `Attachment is already linked to a message` |
| 404 | Unknown recipient | `User not found` |
| 413 | Ciphertext over 200,000 characters | `Message is too large` |

```bash
# Plaintext is refused, so the server can never become a plaintext store by accident:
curl -s -X POST $API/api/messages -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientUsername":"bob","ciphertext":"hello in the clear"}' | jq .message
```

---

## 10. Read message history

Ciphertext history, **oldest first**. Page backwards with `before` (a message id).

| | |
|---|---|
| **Method / URL** | `GET /api/conversations/{id}/messages?size=50&before={messageId}` |
| **Headers** | `Authorization: Bearer $TOKEN` |
| **Query** | `size` 1–100 (default 50), `before` optional |

```bash
curl -s "$API/api/conversations/1/messages?size=50" -H "Authorization: Bearer $BOB_TOKEN" | jq
```

**Expected: `200 OK`**

```json
[
  {
    "id": 1,
    "conversationId": 1,
    "sender": "alice",
    "ciphertext": "-----BEGIN PGP MESSAGE-----…",
    "attachmentId": "6d94349c-1d9b-463a-8cf9-1625454c9fdb",
    "createdAt": "2026-09-28T14:00:23.782800Z"
  }
]
```

**Common errors**: `404 Conversation not found` if the id doesn't exist **or you are not a participant**; `400 Invalid value for 'id'` for a non-numeric id.

```bash
# A third user cannot read it:
EVE=$(curl -s -X POST $API/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"eve","password":"eve password 123"}' | jq -r .token)
curl -s $API/api/conversations/1/messages -H "Authorization: Bearer $EVE" | jq .status   # 404
```

---

## 11. Download an attachment

Returns the encrypted bytes exactly as uploaded. Decryption happens in the client.

| | |
|---|---|
| **Method / URL** | `GET /api/attachments/{id}` |
| **Headers** | `Authorization: Bearer $TOKEN` |

```bash
curl -s -D - -o downloaded.pgp $API/api/attachments/$ATTACHMENT_ID -H "Authorization: Bearer $BOB_TOKEN" | head -6
cmp downloaded.pgp docs/samples/alice-to-bob.bin && echo "identical ciphertext"
```

**Expected: `200 OK`** with binary body

```
HTTP/1.1 200
Cache-Control: no-store
Content-Disposition: attachment; filename="6d94349c-1d9b-463a-8cf9-1625454c9fdb.pgp"
Content-Type: application/octet-stream
X-Content-Type-Options: nosniff
```

**Common errors**: `404 Attachment not found` (unknown id, **or not a participant**); `400 Invalid value for 'id'` if the id is not a UUID.

---

## 12. Real-time delivery (WebSocket / STOMP)

| | |
|---|---|
| **URL** | `ws://localhost:8080/ws` (plain WebSocket, STOMP 1.2) |
| **CONNECT header** | `Authorization: Bearer <token>`, sent in the STOMP CONNECT frame, never in the URL |
| **Subscribe to** | `/user/queue/messages` (the only destination allowed) |
| **Payload** | The same JSON as `POST /api/messages` returns |
| **Allowed origins** | The configured frontend origin only |

Clients cannot `SEND` over the socket; messages go through `POST /api/messages` so they are validated. CONNECT without a valid token and SUBSCRIBE to any other destination are rejected with a STOMP `ERROR` frame.

**Quick test with Node** (uses `@stomp/stompjs`, already in `frontend/node_modules`):

```bash
cd frontend && BOB_TOKEN=$BOB_TOKEN node -e '
const { Client } = require("@stomp/stompjs");
const c = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: { Authorization: "Bearer " + process.env.BOB_TOKEN },
  onConnect: () => { console.log("connected; waiting…");
    c.subscribe("/user/queue/messages", f => { console.log("received", JSON.parse(f.body).id); process.exit(0); }); },
  onStompError: f => { console.error("STOMP error:", f.headers.message); process.exit(1); },
});
c.activate();'
```

While it waits, repeat step 9 in another terminal (as Alice) and the message id appears instantly. Node 22+ provides the global `WebSocket` used here. The automated equivalent is `WebSocketIntegrationTest` in the backend.

---

## 13. Health check

```bash
curl -s $API/actuator/health   # {"status":"UP"}  (200, no auth; details are hidden)
```

---

## Testing with Swagger UI

1. Open **<http://localhost:8080/swagger-ui.html>**.
2. Expand **Auth → POST /api/auth/login**, click **Try it out**, enter `{"username":"alice","password":"correct horse battery"}` and **Execute**.
3. Copy the `token` value from the response.
4. Click **Authorize** (top right), paste the token (without the `Bearer ` prefix) and confirm.
5. Every other endpoint can now be called with **Try it out**. For `PUT /api/keys/me` and `POST /api/messages`, paste the contents of the sample files as the JSON string value (newlines as `\n`). For `POST /api/attachments`, Swagger shows a file picker; choose `docs/samples/alice-to-bob.bin`.

The raw OpenAPI document is at <http://localhost:8080/v3/api-docs>.

## Testing with Postman

Import [`docs/cipherchat.postman_collection.json`](docs/cipherchat.postman_collection.json) (**File → Import**).

- The collection variable `baseUrl` defaults to `http://localhost:8080`.
- The requests are numbered in the same order as this guide. **Login** requests store `token`/`bobToken` automatically, and **Upload attachment** stores `attachmentId`.
- Bodies that need a key or ciphertext already contain the sample values from `docs/samples/`. For the attachment upload, select `docs/samples/alice-to-bob.bin` in the `file` field (Postman cannot embed files).
- Run the whole collection with the **Collection Runner**. Each request has tests that check the expected status code. It can be re-run against the same database: the two register requests accept `201` (created) or `409` (already exists from a previous run).
- Or from the command line, from the repository root (20 requests and 24 assertions, all passing on a fresh stack):

  ```bash
  npx newman run docs/cipherchat.postman_collection.json --working-dir .
  ```

## Automated tests

- `cd backend && ./mvnw verify`: 58 JUnit 5 / MockMvc tests cover every endpoint above, including all the error cases, the rate limiter and the WebSocket handshake. They start a real Vault in Docker with Testcontainers (Docker must be running), configure it with `vault/init.sh`, and check that secrets come from Vault, that backups are stored as Transit ciphertext bound to their user, that key rotation keeps old backups readable, and that the backend's Vault policy allows nothing else.
- `cd frontend && npm run test:e2e`: a real-browser run of the complete encrypted flow.
