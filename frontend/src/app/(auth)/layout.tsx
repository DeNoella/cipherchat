import type { ReactNode } from "react";
import Link from "next/link";
import { LockIcon } from "@/components/ui";

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col justify-center px-4 py-12">
      <Link href="/" className="mb-10 flex items-center gap-2 text-ink">
        <LockIcon className="h-5 w-5 text-accent" />
        <span className="text-lg font-medium tracking-tight">CipherChat</span>
      </Link>
      {children}
    </main>
  );
}
