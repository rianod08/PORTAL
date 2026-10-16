import type { Business, Hotspot, ShopfrontButton } from "./types";
import { openBookingModal, recordEvent } from "./data";
import { hapticTap, isBrowser, mapUrl, publicVenueUrl } from "./utils";

export type PublicActionContext = {
  business: Business;
  source?: string;
  targetId?: string;
  onMenu?: () => void;
  onOffers?: () => void;
  onContact?: () => void;
  onMore?: () => void;
  navigate?: (url: string) => void;
  toast?: (message: string) => void;
};

export function handleBookNow(context?: PublicActionContext): void {
  hapticTap();
  if (!context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "booking_open",
    targetId: context.targetId,
    targetLabel: "Book Now",
    source: context.source ?? "shopfront",
  });
  openBookingModal(context.source === "tour" ? "tour" : "shopfront", context.targetId);
}

export function handleEnterTour(context?: PublicActionContext): void {
  hapticTap();
  if (!context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "tour_start",
    targetLabel: "Enter 360° Tour",
    source: context.source ?? "shopfront",
  });
  context.navigate?.(`/v/${context.business.slug}/tour`);
}

export function handleOpenMenu(context?: PublicActionContext): void {
  hapticTap();
  if (!context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "menu_open",
    targetId: context.targetId,
    targetLabel: "View Menu",
    source: context.source ?? "shopfront",
  });
  context.onMenu?.();
}

export function handleDirections(context?: PublicActionContext): void {
  hapticTap();
  if (!context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "directions_click",
    targetLabel: "Directions",
    source: context.source ?? "shopfront",
  });
  if (isBrowser()) window.open(mapUrl(context.business.address), "_blank", "noopener,noreferrer");
}

export function handleCall(context?: PublicActionContext): void {
  hapticTap();
  if (!context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "call_click",
    targetLabel: "Call",
    source: context.source ?? "shopfront",
  });
  if (isBrowser()) window.location.href = `tel:${context.business.phone}`;
}

export function handleEmail(context?: PublicActionContext): void {
  hapticTap();
  if (!context || !isBrowser()) return;
  window.location.href = `mailto:${context.business.email}`;
}

export function handleWebsite(context?: PublicActionContext): void {
  hapticTap();
  if (!context || !isBrowser() || !context.business.website) return;
  window.open(context.business.website, "_blank", "noopener,noreferrer");
}

export function handleInstagram(context?: PublicActionContext): void {
  hapticTap();
  if (!context || !isBrowser() || !context.business.instagram) return;
  window.open(context.business.instagram, "_blank", "noopener,noreferrer");
}

export function handleCustomButton(button?: ShopfrontButton, context?: PublicActionContext): void {
  hapticTap();
  if (!button || !context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "button_click",
    targetId: button.id,
    targetLabel: button.label,
    source: context.source ?? "shopfront",
  });
  if (button.url && isBrowser()) window.open(button.url, "_blank", "noopener,noreferrer");
}

export function handleHotspotClick(hotspot?: Hotspot, context?: PublicActionContext): void {
  hapticTap();
  if (!hotspot || !context) return;
  recordEvent({
    businessId: context.business.id,
    eventType: "hotspot_click",
    targetId: hotspot.id,
    targetLabel: hotspot.label,
    source: context.source ?? "tour",
  });
}

export async function handleShareVenue(context?: PublicActionContext): Promise<void> {
  hapticTap();
  if (!context || !isBrowser()) return;
  const url = publicVenueUrl(context.business);
  recordEvent({
    businessId: context.business.id,
    eventType: "share_click",
    targetLabel: "Share venue",
    source: context.source ?? "shopfront",
  });
  if (navigator.share) {
    await navigator.share({ title: context.business.name, text: context.business.description, url });
  } else {
    await copyPublicLink(context.business);
    context.toast?.("Public link copied");
  }
}

export async function copyPublicLink(business?: Business): Promise<string> {
  if (!business || !isBrowser()) return "";
  const url = publicVenueUrl(business);
  await navigator.clipboard.writeText(url);
  return url;
}

export function generateQRCodeForVenue(business?: Business): string {
  if (!business) return "";
  return publicVenueUrl(business);
}
