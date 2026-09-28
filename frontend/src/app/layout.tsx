import type { Metadata, Viewport } from "next";
import { Inter } from "next/font/google";
import { headers } from "next/headers";
import { Providers } from "@/components/providers";
import { apiBaseUrl, wsUrl } from "@/lib/runtime-config";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "CipherChat",
  description: "End-to-end encrypted chat with OpenPGP",
};

export const viewport: Viewport = {
  themeColor: "#111315",
  width: "device-width",
  initialScale: 1,
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // Reading request headers opts into dynamic rendering, which the per-request CSP nonce requires.
  await headers();
  const api = apiBaseUrl();

  return (
    <html lang="en" className={`${inter.variable} h-full antialiased`}>
      <body className="min-h-full font-sans">
        <Providers apiUrl={api} wsUrl={wsUrl(api)}>
          {children}
        </Providers>
      </body>
    </html>
  );
}
