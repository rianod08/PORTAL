export type BusinessType =
  | "Restaurant / Cafe"
  | "Bar / Nightlife"
  | "Gym / Fitness"
  | "Salon / Beauty"
  | "Clinic / Wellness"
  | "Event Venue"
  | "Wedding Venue"
  | "Retail Showroom"
  | "Studio / Creative Space"
  | "Other";

export type OpeningHour = {
  day: string;
  open: string;
  close: string;
  closed?: boolean;
};

export interface Business {
  id: string;
  slug: string;
  name: string;
  type: BusinessType;
  description: string;
  logoUrl?: string;
  coverImageUrl?: string;
  address: string;
  phone: string;
  email: string;
  website?: string;
  instagram?: string;
  bookingUrl?: string;
  menuUrl?: string;
  openingHours: OpeningHour[];
  themeId: string;
  themeOverrides?: Partial<
    Pick<Theme, "accentColor" | "buttonStyle" | "hotspotStyle" | "glassIntensity">
  >;
  status: "draft" | "published";
  createdAt: string;
  updatedAt: string;
}

export interface ShopfrontButton {
  id: string;
  businessId: string;
  label: string;
  type:
    | "booking"
    | "tour"
    | "menu"
    | "directions"
    | "call"
    | "email"
    | "website"
    | "instagram"
    | "events"
    | "offers"
    | "custom";
  url?: string;
  icon: string;
  order: number;
  isPrimary: boolean;
  isEnabled: boolean;
}

export interface Tour {
  id: string;
  businessId: string;
  title: string;
  description?: string;
  status: "draft" | "published";
  defaultSceneId?: string;
}

export interface TourScene {
  id: string;
  tourId: string;
  name: string;
  description?: string;
  imageUrl: string;
  thumbnailUrl?: string;
  order: number;
}

export interface Hotspot {
  id: string;
  sceneId: string;
  label: string;
  type:
    | "booking"
    | "menu"
    | "info"
    | "image"
    | "video"
    | "contact"
    | "product"
    | "scene"
    | "external";
  x: number;
  y: number;
  targetUrl?: string;
  targetSceneId?: string;
  icon: string;
  description?: string;
  isEnabled: boolean;
}

export interface Theme {
  id: string;
  name: string;
  description: string;
  mode: "light" | "dark";
  accentColor: string;
  backgroundStyle: string;
  buttonStyle: string;
  hotspotStyle: string;
  glassIntensity: number;
}

export interface BookingLead {
  id: string;
  businessId: string;
  source: "shopfront" | "tour" | "hotspot";
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  requestedDate?: string;
  requestedTime?: string;
  partySize?: number;
  message?: string;
  status: "new" | "contacted" | "confirmed" | "cancelled";
  createdAt: string;
}

export interface AnalyticsEvent {
  id: string;
  businessId: string;
  eventType:
    | "page_view"
    | "tour_start"
    | "button_click"
    | "hotspot_click"
    | "booking_open"
    | "booking_submit"
    | "menu_open"
    | "directions_click"
    | "call_click"
    | "share_click"
    | "publish";
  targetId?: string;
  targetLabel?: string;
  source?: string;
  timestamp: string;
}

export type AnalyticsSummary = {
  totalVisits: number;
  tourStarts: number;
  bookingOpens: number;
  bookingSubmissions: number;
  menuOpens: number;
  directionsClicks: number;
  callClicks: number;
  hotspotClicks: number;
  buttonClicks: number;
  publishEvents: number;
  conversionRate: number;
  mostViewedScene?: string;
  recentEvents: AnalyticsEvent[];
  sparkline: number[];
};

export type PortalStore = {
  version: number;
  activeBusinessId: string;
  businesses: Business[];
  buttons: ShopfrontButton[];
  tours: Tour[];
  scenes: TourScene[];
  hotspots: Hotspot[];
  bookingLeads: BookingLead[];
  analyticsEvents: AnalyticsEvent[];
};
