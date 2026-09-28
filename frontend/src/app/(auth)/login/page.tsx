"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { useApp } from "@/components/providers";
import { Button, Card, Field, Notice, Spinner } from "@/components/ui";
import { ApiError, type AuthResponse } from "@/lib/api";
import {
  generateKeyPair,
  readPrivateKeyFingerprint,
  unlockPrivateKey,
  WrongPassphraseError,
} from "@/lib/crypto";
import { keystore } from "@/lib/keystore";
import { session, useHydrated, useSession } from "@/lib/session";

type Stage =
  | { kind: "credentials" }
  | { kind: "import"; auth: AuthResponse; passphrase: string }
  | { kind: "create-key"; auth: AuthResponse; passphrase: string };

function describe(err: unknown): string {
  if (err instanceof WrongPassphraseError || err instanceof ApiError) return err.message;
  return err instanceof Error ? err.message : "Something went wrong";
}

function LoginForm() {
  const { api } = useApp();
  const router = useRouter();
  const params = useSearchParams();
  const hydrated = useHydrated();
  const { token, username: sessionUser, privateKey } = useSession();
  const [stage, setStage] = useState<Stage>({ kind: "credentials" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const locked = hydrated && token !== null && privateKey === null;

  /** Unlocks the locally stored key and checks it is the key the server has on record. */
  async function unlockStored(username: string, passphrase: string, serverFingerprint?: string | null) {
    const stored = await keystore.get(username);
    if (!stored) return null;
    if (serverFingerprint && stored.fingerprint !== serverFingerprint) {
      throw new Error(
        "The key stored on this device does not match the key on your account. Import your current key backup.",
      );
    }
    const key = await unlockPrivateKey(stored.encryptedPrivateKey, passphrase);
    return { key, stored };
  }

  async function onCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get("username") ?? "");
    const password = String(form.get("password") ?? "");
    const passphrase = String(form.get("passphrase") ?? "");
    setError(null);
    setBusy(true);
    try {
      const auth = await api.login(username, password);
      const unlocked = await unlockStored(auth.username, passphrase, auth.fingerprint);
      if (unlocked) {
        const { key, stored } = unlocked;
        session.signIn(auth.token, auth.username, key, stored.publicKey, stored.fingerprint);
        router.replace("/chats");
      } else {
        setStage({ kind: auth.hasPublicKey ? "import" : "create-key", auth, passphrase });
      }
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 429
          ? "Too many attempts. Wait a minute and try again."
          : describe(err),
      );
    } finally {
      setBusy(false);
    }
  }

  async function onUnlock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const passphrase = String(new FormData(event.currentTarget).get("passphrase") ?? "");
    setError(null);
    setBusy(true);
    try {
      const unlocked = sessionUser ? await unlockStored(sessionUser, passphrase) : null;
      if (!unlocked) throw new Error("No key for this account on this device. Sign out and sign in again.");
      session.unlock(unlocked.key, unlocked.stored.publicKey, unlocked.stored.fingerprint);
      router.replace(params.get("next")?.startsWith("/") ? params.get("next")! : "/chats");
    } catch (err) {
      setError(describe(err));
    } finally {
      setBusy(false);
    }
  }

  async function onImport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (stage.kind !== "import") return;
    const file = new FormData(event.currentTarget).get("backup");
    setError(null);
    setBusy(true);
    try {
      if (!(file instanceof File) || file.size === 0) throw new Error("Choose your key backup file (.asc)");
      if (file.size > 64 * 1024) throw new Error("That file is too large to be a key backup");
      const armored = await file.text();
      const fingerprint = await readPrivateKeyFingerprint(armored).catch(() => {
        throw new Error("That file is not an OpenPGP private key backup");
      });
      if (fingerprint !== stage.auth.fingerprint) {
        throw new Error("This backup belongs to a different key than the one on your account");
      }
      const key = await unlockPrivateKey(armored, stage.passphrase);
      const publicKey = key.toPublic().armor();
      await keystore.put({
        username: stage.auth.username,
        publicKey,
        encryptedPrivateKey: armored,
        fingerprint,
        createdAt: new Date().toISOString(),
      });
      session.signIn(stage.auth.token, stage.auth.username, key, publicKey, fingerprint);
      router.replace("/chats");
    } catch (err) {
      setError(describe(err));
    } finally {
      setBusy(false);
    }
  }

  async function onCreateKey() {
    if (stage.kind !== "create-key") return;
    setError(null);
    setBusy(true);
    try {
      const { auth, passphrase } = stage;
      const keys = await generateKeyPair(auth.username, passphrase);
      session.signIn(auth.token, auth.username, await unlockPrivateKey(keys.encryptedPrivateKey, passphrase),
        keys.publicKey, keys.fingerprint);
      await api.uploadKey(keys.publicKey);
      await keystore.put({ username: auth.username, ...keys, createdAt: new Date().toISOString() });
      router.replace("/profile?welcome=1");
    } catch (err) {
      session.signOut();
      setError(describe(err));
    } finally {
      setBusy(false);
    }
  }

  if (!hydrated) return <Spinner />;

  if (locked) {
    return (
      <Card>
        <h1 className="text-xl font-medium">Unlock your key</h1>
        <p className="mt-2 text-sm text-muted">
          Signed in as <span className="text-ink">{sessionUser}</span>. Your private key is locked after a reload.
        </p>
        <form onSubmit={onUnlock} className="mt-6 space-y-4">
          <Field label="Key passphrase" name="passphrase" type="password" autoComplete="off" required autoFocus />
          {error && <Notice tone="danger">{error}</Notice>}
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Unlocking…" : "Unlock"}
          </Button>
        </form>
        <button onClick={() => session.signOut()} className="mt-6 text-sm text-muted hover:text-ink">
          Sign out instead
        </button>
      </Card>
    );
  }

  if (stage.kind === "import") {
    return (
      <Card>
        <h1 className="text-xl font-medium">Import your key</h1>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          This device does not have your private key. Choose the encrypted backup you exported from your profile.
          It is unlocked here with the passphrase you entered and never uploaded.
        </p>
        <form onSubmit={onImport} className="mt-6 space-y-4">
          <input
            type="file"
            name="backup"
            accept=".asc,.txt,application/pgp-keys"
            className="block w-full text-sm text-muted file:mr-3 file:rounded-md file:border file:border-line-strong file:bg-transparent file:px-3 file:py-1.5 file:text-sm file:text-ink"
          />
          {error && <Notice tone="danger">{error}</Notice>}
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Importing…" : "Import and continue"}
          </Button>
        </form>
        <button onClick={() => setStage({ kind: "credentials" })} className="mt-6 text-sm text-muted hover:text-ink">
          Back
        </button>
      </Card>
    );
  }

  if (stage.kind === "create-key") {
    return (
      <Card>
        <h1 className="text-xl font-medium">Create an encryption key</h1>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          Your account has no key yet. We will generate one on this device, protected by the passphrase you entered.
        </p>
        {error && <div className="mt-4"><Notice tone="danger">{error}</Notice></div>}
        <Button onClick={onCreateKey} className="mt-6 w-full" disabled={busy}>
          {busy ? "Generating…" : "Generate key"}
        </Button>
      </Card>
    );
  }

  return (
    <Card>
      <h1 className="text-xl font-medium">Sign in</h1>
      {params.get("expired") && (
        <div className="mt-4"><Notice>Your session expired. Please sign in again.</Notice></div>
      )}
      <form onSubmit={onCredentials} className="mt-6 space-y-4">
        <Field label="Username" name="username" autoComplete="username" required autoFocus />
        <Field label="Password" name="password" type="password" autoComplete="current-password" required />
        <Field
          label="Key passphrase"
          name="passphrase"
          type="password"
          autoComplete="off"
          required
          hint="Unlocks your private key on this device only."
        />
        {error && <Notice tone="danger">{error}</Notice>}
        <Button type="submit" className="w-full" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </Button>
      </form>
      <p className="mt-6 text-sm text-muted">
        New here?{" "}
        <Link href="/register" className="text-ink underline decoration-line-strong underline-offset-4 hover:decoration-ink">
          Create an account
        </Link>
      </p>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <LoginForm />
    </Suspense>
  );
}
