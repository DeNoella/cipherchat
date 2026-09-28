import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";
import { formatFingerprint, type SignatureStatus } from "@/lib/crypto";

export function cx(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(" ");
}

type ButtonVariant = "primary" | "secondary" | "ghost";

export function Button({
  variant = "primary",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return (
    <button
      className={cx(
        "inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition-colors",
        "disabled:cursor-not-allowed disabled:opacity-50",
        variant === "primary" && "bg-accent text-accent-ink hover:bg-accent/90",
        variant === "secondary" && "border border-line-strong text-ink hover:bg-raised",
        variant === "ghost" && "text-muted hover:bg-raised hover:text-ink",
        className,
      )}
      {...props}
    />
  );
}

export function Field({
  label,
  hint,
  error,
  className,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const inputId = id ?? props.name;
  return (
    <div className={cx("space-y-1.5", className)}>
      <label htmlFor={inputId} className="block text-sm text-muted">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={Boolean(error)}
        aria-describedby={error || hint ? `${inputId}-note` : undefined}
        className={cx(
          "w-full rounded-md border bg-surface px-3 py-2 text-sm text-ink placeholder:text-faint",
          "focus:outline-none focus-visible:outline-none focus:border-accent",
          error ? "border-danger/70" : "border-line",
        )}
        {...props}
      />
      {(error || hint) && (
        <p id={`${inputId}-note`} className={cx("text-xs", error ? "text-danger" : "text-faint")}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

export function Notice({ tone = "info", children }: { tone?: "info" | "warn" | "danger" | "ok"; children: ReactNode }) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={cx(
        "rounded-md border px-3 py-2.5 text-sm leading-relaxed",
        tone === "info" && "border-line bg-surface text-muted",
        tone === "ok" && "border-accent/40 bg-accent/5 text-ink",
        tone === "warn" && "border-warn/40 bg-warn/5 text-ink",
        tone === "danger" && "border-danger/40 bg-danger/5 text-ink",
      )}
    >
      {children}
    </div>
  );
}

export function LockIcon({ className = "h-3.5 w-3.5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" className={className} aria-hidden>
      <rect x="3" y="7" width="10" height="7" rx="1.5" />
      <path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" />
    </svg>
  );
}

function CheckIcon({ className = "h-3.5 w-3.5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" className={className} aria-hidden>
      <path d="M3.5 8.5l3 3 6-7" />
    </svg>
  );
}

function AlertIcon({ className = "h-3.5 w-3.5" }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" className={className} aria-hidden>
      <path d="M8 2.5l6 11H2l6-11z" />
      <path d="M8 6.5v3M8 11.5v.01" />
    </svg>
  );
}

export function SignatureBadge({ status }: { status: SignatureStatus }) {
  if (status === "verified") {
    return (
      <span className="inline-flex items-center gap-1 text-[11px] text-accent" title="Signed by the sender's key">
        <CheckIcon className="h-3 w-3" /> Verified
      </span>
    );
  }
  return (
    <span
      className="inline-flex items-center gap-1 text-[11px] text-danger"
      title="The signature does not match the sender's current public key"
    >
      <AlertIcon className="h-3 w-3" /> {status === "unsigned" ? "Unsigned" : "Signature invalid"}
    </span>
  );
}

export function Fingerprint({ value, className }: { value: string; className?: string }) {
  return (
    <code className={cx("block break-all font-mono text-xs leading-relaxed tracking-wide text-muted", className)}>
      {formatFingerprint(value)}
    </code>
  );
}

export function Spinner({ label = "Loading" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-faint" role="status">
      <span className="h-3 w-3 animate-pulse rounded-full bg-line-strong" />
      {label}
    </div>
  );
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <section className={cx("rounded-lg border border-line bg-surface p-5 sm:p-6", className)}>{children}</section>;
}
