---
title: Learn PGP from the beginning
---

# Learn PGP from the beginning

I assume nothing. You don't need to know anything about encryption, Java or Spring Boot to follow this course.

By the end, you'll be able to explain how PGP protects a message, and prove it using a real app: **CipherChat**.

## What is CipherChat?

CipherChat is a chat app I built. It's **end-to-end encrypted**: your browser locks each message before it leaves your computer, and only the person you're talking to can unlock it. The server in the middle only ever stores scrambled text.

- **Frontend:** Next.js (React). This is where all the encryption happens, using the OpenPGP.js library.
- **Backend:** Spring Boot (Java). It stores accounts, public keys and ciphertext, and it checks that what it receives really is encrypted.
- **Database:** PostgreSQL.

Every example in this course comes from CipherChat as it actually works. Every code snippet links to the exact lines on GitHub, pinned to a commit so the links never break.

![The CipherChat chat screen, showing an encrypted conversation with the Verified badge](/screenshots/chat.png)

## The parts

<SeriesNav course="/cybersecurity/pgp-from-the-beginning/" />

| Part | What you'll be able to explain |
| --- | --- |
| **I** | Why encryption exists, and the difference between one shared key and a public/private key pair. |
| **II** | How PGP really locks a message: session keys, signatures, fingerprints, and how your private key is protected (a passphrase once, then a device key). |
| **III** | The journey of your key (sign-up, sign-in, new device), of one message and one file, and what the server can and can't see. |
| **IV** | The Spring Boot backend, layer by layer (including HashiCorp Vault), using a restaurant as the running comparison. |
| **V** | The honest limits, hands-on proof with the running app, and interview answers. |

## How to use this course in one day

1. **Morning:** Parts I and II. These are the ideas. Answer every "Check yourself" question before revealing it.
2. **Midday:** Part III. Follow along with CipherChat open in your browser.
3. **Afternoon:** Part IV. Keep the code open on GitHub next to the article.
4. **Evening:** Part V. Do the hands-on proof, then read the interview answers out loud.

> **Tip:** Read the code links. In an interview, "here is the line that does it" is far more convincing than "I think it works like this".

## Run CipherChat yourself

You need Docker. From the `cipherchat` folder:

```bash
cp .env.example .env    # then replace each "replace-with-..." value with a random one
docker compose up --build
```

Then open <http://localhost:3000>. Full instructions are in the [README](https://github.com/angelabs-png/cipherchat#running-the-project).
