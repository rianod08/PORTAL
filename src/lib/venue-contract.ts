import type { Business, BusinessType, ShopfrontButton, Theme } from "./types";
import { THEMES } from "./themes";
import { safeUrl } from "./tour-contract";

export const BUSINESS_TYPES: BusinessType[] = [
  "Restaurant / Cafe",
  "Bar / Nightlife",
  "Gym / Fitness",
  "Salon / Beauty",
  "Clinic / Wellness",
  "Event Venue",
  "Wedding Venue",
  "Retail Showroom",
  "Studio / Creative Space",
  "Other",
];
export const VENUE_BUTTON_TYPES = [
  "booking",
  "tour",
  "menu",
  "directions",
  "call",
  "email",
  "website",
  "instagram",
  "custom",
] as const;
export type VenueButton = Omit<ShopfrontButton, "businessId" | "type"> & {
  type: (typeof VENUE_BUTTON_TYPES)[number];
};
export type VenueDocument = Omit<
  Business,
  | "id"
  | "slug"
  | "status"
  | "createdAt"
  | "updatedAt"
  | "logoUrl"
  | "coverImageUrl"
> & {
  version: 1;
  logoAssetPath?: string;
  coverAssetPath?: string;
  buttons: VenueButton[];
};
export type Venue = {
  id: string;
  owner_id: string;
  slug: string;
  document: VenueDocument;
  main_tour_id: string | null;
  revision: number;
  published_revision: number | null;
  published_document: VenueDocument | null;
  published_tour_id: string | null;
  updated_at: string;
  created_at: string;
};
export type PublicVenue = {
  id: string;
  slug: string;
  document: VenueDocument;
  revision: number;
  tourSlug: string | null;
  logoUrl?: string;
  coverImageUrl?: string;
};
export type VenueView = PublicVenue & { preview?: boolean };
export function emptyVenue(name = ""): VenueDocument {
  return {
    version: 1,
    name,
    type: "Other",
    description: "",
    address: "",
    phone: "",
    email: "",
    openingHours: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map(
      (day) => ({ day, open: "09:00", close: "17:00", closed: false }),
    ),
    themeId: THEMES[0].id,
    buttons: [
      {
        id: "tour",
        label: "360° Tour",
        type: "tour",
        icon: "route",
        order: 0,
        isPrimary: true,
        isEnabled: true,
      },
      {
        id: "booking",
        label: "Book now",
        type: "booking",
        icon: "calendar",
        order: 1,
        isPrimary: false,
        isEnabled: true,
      },
      {
        id: "directions",
        label: "Directions",
        type: "directions",
        icon: "map-pin",
        order: 2,
        isPrimary: false,
        isEnabled: true,
      },
    ],
  };
}
export function validVenueSlug(slug: unknown): slug is string {
  return (
    typeof slug === "string" &&
    /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) &&
    slug.length >= 3 &&
    slug.length <= 80 &&
    !slug.startsWith("tour-")
  );
}
export function venueAssets(doc: VenueDocument): string[] {
  return [
    ...new Set(
      [doc.logoAssetPath, doc.coverAssetPath].filter((p): p is string => !!p),
    ),
  ];
}
export function validateVenue(value: unknown): string[] {
  if (!value || typeof value !== "object") return ["Invalid venue document."];
  const d = value as VenueDocument;
  if (
    d.version !== 1 ||
    typeof d.name !== "string" ||
    !d.name.trim() ||
    d.name.length > 160 ||
    !BUSINESS_TYPES.includes(d.type) ||
    !THEMES.some((t) => t.id === d.themeId)
  )
    return ["Enter a venue name, business type, and theme."];
  const errors: string[] = [];
  for (const key of ["description", "address", "phone", "email"] as const)
    if (
      typeof d[key] !== "string" ||
      d[key].length > (key === "description" ? 5000 : 320)
    )
      errors.push(`Invalid ${key}.`);
  if (
    typeof d.email === "string" &&
    d.email &&
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(d.email)
  )
    errors.push("Enter a valid contact email.");
  if (typeof d.phone === "string" && /[\r\n]/.test(d.phone))
    errors.push("Invalid phone number.");
  for (const key of ["website", "instagram", "bookingUrl", "menuUrl"] as const)
    if (
      d[key] !== undefined &&
      (typeof d[key] !== "string" ||
        d[key]!.length > 2048 ||
        (d[key] && !safeUrl(d[key]!)))
    )
      errors.push(`Enter a full http or https ${key}.`);
  for (const key of ["logoAssetPath", "coverAssetPath"] as const)
    if (
      d[key] !== undefined &&
      (typeof d[key] !== "string" ||
        !/^[a-f0-9-]{36}\/[a-f0-9-]{36}\/[a-f0-9-]{36}\.(jpg|png|webp)$/.test(
          d[key]!,
        ))
    )
      errors.push("Invalid venue image path.");
  if (
    !Array.isArray(d.openingHours) ||
    d.openingHours.length !== 7 ||
    new Set(d.openingHours.map((h) => h?.day)).size !== 7 ||
    d.openingHours.some(
      (h) =>
        !h ||
        !["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].includes(h.day) ||
        typeof h.closed !== "boolean" ||
        !/^([01]\d|2[0-3]):[0-5]\d$/.test(h.open) ||
        !/^([01]\d|2[0-3]):[0-5]\d$/.test(h.close),
    )
  )
    errors.push("Set valid opening hours for all seven days.");
  if (
    d.themeOverrides !== undefined &&
    (!d.themeOverrides ||
      Object.keys(d.themeOverrides).some((k) => k !== "accentColor") ||
      !/^#[\da-f]{6}$/i.test(d.themeOverrides.accentColor ?? ""))
  )
    errors.push("Choose a valid accent colour.");
  if (!Array.isArray(d.buttons) || d.buttons.length > 20)
    return [...errors, "Use at most 20 buttons."];
  const ids = new Set<string>();
  for (const b of d.buttons) {
    if (
      !b ||
      typeof b.id !== "string" ||
      !/^[\w-]{1,120}$/.test(b.id) ||
      ids.has(b.id) ||
      typeof b.label !== "string" ||
      !b.label.trim() ||
      b.label.length > 80 ||
      !VENUE_BUTTON_TYPES.includes(b.type) ||
      typeof b.icon !== "string" ||
      b.icon.length > 80 ||
      !Number.isInteger(b.order) ||
      b.order < 0 ||
      typeof b.isPrimary !== "boolean" ||
      typeof b.isEnabled !== "boolean"
    ) {
      errors.push("Invalid or duplicate storefront button.");
      continue;
    }
    ids.add(b.id);
    if (
      b.url !== undefined &&
      (typeof b.url !== "string" ||
        b.url.length > 2048 ||
        (b.url && !safeUrl(b.url)))
    )
      errors.push(`Enter a full http or https link for ${b.label}.`);
    if (b.type === "custom" && b.isEnabled && !b.url)
      errors.push(`Add a link for ${b.label}.`);
  }
  return errors;
}
export function venueTheme(d: VenueDocument): Theme {
  return {
    ...(THEMES.find((t) => t.id === d.themeId) ?? THEMES[0]),
    ...d.themeOverrides,
  };
}
export function venueBusiness(v: VenueView): Business {
  return {
    ...v.document,
    id: v.id,
    slug: v.slug,
    status: v.preview ? "draft" : "published",
    createdAt: "",
    updatedAt: "",
    logoUrl: v.logoUrl,
    coverImageUrl: v.coverImageUrl,
  };
}
export function venueButtonHref(b: VenueButton, v: VenueView): string | null {
  const d = v.document;
  switch (b.type) {
    case "tour":
      return v.tourSlug ? `/v/${encodeURIComponent(v.slug)}/tour` : null;
    case "call":
      return d.phone ? `tel:${encodeURIComponent(d.phone)}` : null;
    case "email":
      return d.email ? `mailto:${encodeURIComponent(d.email)}` : null;
    case "directions":
      return d.address
        ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(d.address)}`
        : null;
    default: {
      const url =
        b.url ||
        (
          {
            booking: d.bookingUrl,
            menu: d.menuUrl,
            website: d.website,
            instagram: d.instagram,
          } as Record<string, string | undefined>
        )[b.type];
      return url && safeUrl(url) ? url : null;
    }
  }
}
