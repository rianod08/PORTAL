import type { Metadata } from "next";
import { AccountLinkRedirect } from "@/components/AccountLinkRedirect";
import "./globals.css";

export const metadata: Metadata = {
  title: "Portal | 360° Venue Tours",
  description:
    "Turn your real venue into an interactive 360° tour with bookings, menus, and shareable links.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col"><AccountLinkRedirect />{children}</body>
    </html>
  );
}
