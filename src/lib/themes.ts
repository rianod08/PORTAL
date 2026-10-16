import type { Theme } from "./types";

export const THEMES: Theme[] = [
  {
    id: "opal-glass",
    name: "Opal Glass",
    description: "Light translucent panels with pearl, cyan, lavender, and mint accents.",
    mode: "light",
    accentColor: "#49bdf2",
    backgroundStyle:
      "linear-gradient(145deg, #f7fbff 0%, #dff5ff 35%, #f4eafe 68%, #e8fff6 100%)",
    buttonStyle: "floating-pill",
    hotspotStyle: "opal-ring",
    glassIntensity: 82,
  },
  {
    id: "denim-glass",
    name: "Denim Glass",
    description: "Deep blue glass with cyan and violet accents for a premium night look.",
    mode: "dark",
    accentColor: "#6bd4ff",
    backgroundStyle:
      "linear-gradient(145deg, #061426 0%, #0c315d 46%, #1c2f66 74%, #130f29 100%)",
    buttonStyle: "luminous-pill",
    hotspotStyle: "cyan-pulse",
    glassIntensity: 88,
  },
  {
    id: "warm-cafe",
    name: "Warm Cafe",
    description: "Amber overlays, coffee-toned backgrounds, and soft cream cards.",
    mode: "light",
    accentColor: "#d98835",
    backgroundStyle:
      "linear-gradient(145deg, #fff4df 0%, #f3c88d 38%, #c97848 72%, #5b3427 100%)",
    buttonStyle: "cream-pill",
    hotspotStyle: "amber-dot",
    glassIntensity: 72,
  },
  {
    id: "luxury-venue",
    name: "Luxury Venue",
    description: "Dark glass, gold accents, and elegant contrast for premium spaces.",
    mode: "dark",
    accentColor: "#e8c46c",
    backgroundStyle:
      "linear-gradient(145deg, #08090c 0%, #1f1d24 44%, #3b3020 78%, #0d0b0a 100%)",
    buttonStyle: "gold-pill",
    hotspotStyle: "gold-ring",
    glassIntensity: 90,
  },
  {
    id: "clean-studio",
    name: "Clean Studio",
    description: "Minimal white and gray glass with a neutral professional tone.",
    mode: "light",
    accentColor: "#64748b",
    backgroundStyle:
      "linear-gradient(145deg, #ffffff 0%, #edf2f7 44%, #d9e4ee 78%, #f7fafc 100%)",
    buttonStyle: "minimal-pill",
    hotspotStyle: "slate-dot",
    glassIntensity: 64,
  },
];

export function getThemeById(themeId: string): Theme {
  return THEMES.find((theme) => theme.id === themeId) ?? THEMES[0];
}
