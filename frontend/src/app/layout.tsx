import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "FITNet | Network Collaboration Platform",
  description: "Collaborate, find online users, and share files securely with FITNet.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
