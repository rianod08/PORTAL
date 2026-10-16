import type { CSSProperties } from "react";
import type { Business, Theme } from "./types";

export function cn(...values: Array<string | false | null | undefined>): string {
  return values.filter(Boolean).join(" ");
}

export function slugify(value: string): string {
  const slug = value
    .toLowerCase()
    .trim()
    .replace(/&/g, "and")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

  return slug || "venue";
}

export function createId(prefix = "id"): string {
  const random =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID().slice(0, 8)
      : Math.random().toString(36).slice(2, 10);
  return `${prefix}-${random}`;
}

export function nowIso(): string {
  return new Date().toISOString();
}

export function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

export function isBrowser(): boolean {
  return typeof window !== "undefined";
}

export function hapticTap(): void {
  if (!isBrowser()) return;
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  if ("vibrate" in navigator) {
    navigator.vibrate(8);
  }
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function mediaBackground(value?: string): CSSProperties {
  if (!value) return {};
  if (value.startsWith("gradient:")) {
    return { backgroundImage: value.replace("gradient:", "") };
  }
  return { backgroundImage: `url(${value})` };
}

export function makeGradient(seed: "cafe" | "fitness" | "venue" | "salon" | "studio"): string {
  const gradients: Record<string, string> = {
    cafe:
      "gradient:linear-gradient(120deg, rgba(77,43,27,.92), rgba(219,139,67,.82) 36%, rgba(255,231,185,.9) 68%, rgba(95,56,37,.9)), radial-gradient(circle at 20% 28%, rgba(255,255,255,.38), transparent 30%)",
    fitness:
      "gradient:linear-gradient(120deg, rgba(7,22,42,.96), rgba(20,92,138,.9) 38%, rgba(103,233,255,.72) 66%, rgba(36,22,86,.92)), radial-gradient(circle at 78% 22%, rgba(255,255,255,.3), transparent 26%)",
    venue:
      "gradient:linear-gradient(120deg, rgba(11,11,15,.95), rgba(72,55,35,.88) 36%, rgba(238,196,111,.72) 62%, rgba(23,19,17,.94)), radial-gradient(circle at 62% 24%, rgba(255,255,255,.24), transparent 30%)",
    salon:
      "gradient:linear-gradient(120deg, rgba(255,236,248,.98), rgba(231,175,219,.85) 38%, rgba(151,218,226,.74) 68%, rgba(255,255,255,.92)), radial-gradient(circle at 24% 20%, rgba(255,255,255,.6), transparent 34%)",
    studio:
      "gradient:linear-gradient(120deg, rgba(255,255,255,.95), rgba(221,232,242,.9) 38%, rgba(178,205,222,.78) 70%, rgba(246,250,255,.92)), radial-gradient(circle at 24% 26%, rgba(255,255,255,.62), transparent 32%)",
  };
  return gradients[seed];
}

export function readableTextForTheme(theme: Theme): string {
  return theme.mode === "dark" ? "text-white" : "text-slate-950";
}

export function mutedTextForTheme(theme: Theme): string {
  return theme.mode === "dark" ? "text-white/72" : "text-slate-700";
}

export function publicVenueUrl(business: Business): string {
  if (isBrowser()) return `${window.location.origin}${window.location.pathname.startsWith("/demo") ? "/demo" : ""}/v/${business.slug}`;
  return `http://localhost:3000/v/${business.slug}`;
}

export function mapUrl(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
}

export function formatPercent(value: number): string {
  return `${Math.round(value)}%`;
}
