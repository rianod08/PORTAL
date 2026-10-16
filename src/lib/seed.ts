import type {
  AnalyticsEvent,
  BookingLead,
  Business,
  Hotspot,
  OpeningHour,
  PortalStore,
  ShopfrontButton,
  Tour,
  TourScene,
} from "./types";
import { makeGradient } from "./utils";

const createdAt = "2026-07-01T09:00:00.000Z";

const hours: OpeningHour[] = [
  { day: "Mon", open: "08:00", close: "17:00" },
  { day: "Tue", open: "08:00", close: "17:00" },
  { day: "Wed", open: "08:00", close: "17:00" },
  { day: "Thu", open: "08:00", close: "20:00" },
  { day: "Fri", open: "08:00", close: "21:00" },
  { day: "Sat", open: "09:00", close: "21:00" },
  { day: "Sun", open: "10:00", close: "16:00" },
];

export const seedBusinesses: Business[] = [
  {
    id: "biz-bloom",
    slug: "bloom-cafe",
    name: "Bloom Cafe",
    type: "Restaurant / Cafe",
    description:
      "A bright neighborhood cafe with all-day brunch, private supper clubs, and a garden room made for slow mornings.",
    logoUrl: makeGradient("cafe"),
    coverImageUrl: makeGradient("cafe"),
    address: "14 Willow Lane, Dublin 2",
    phone: "+35315550123",
    email: "hello@bloomcafe.example",
    website: "https://bloomcafe.example",
    instagram: "https://instagram.com/bloomcafe",
    bookingUrl: "https://bloomcafe.example/book",
    menuUrl: "https://bloomcafe.example/menu",
    openingHours: hours,
    themeId: "warm-cafe",
    status: "published",
    createdAt,
    updatedAt: createdAt,
  },
  {
    id: "biz-atlas",
    slug: "atlas-fitness-studio",
    name: "Atlas Fitness Studio",
    type: "Gym / Fitness",
    description:
      "A boutique training studio with strength zones, small-group classes, recovery space, and friendly coaches.",
    logoUrl: makeGradient("fitness"),
    coverImageUrl: makeGradient("fitness"),
    address: "82 Camden Street, Dublin 2",
    phone: "+35315550144",
    email: "team@atlasfitness.example",
    website: "https://atlasfitness.example",
    instagram: "https://instagram.com/atlasfitness",
    bookingUrl: "https://atlasfitness.example/trial",
    menuUrl: "https://atlasfitness.example/classes",
    openingHours: hours,
    themeId: "denim-glass",
    status: "published",
    createdAt,
    updatedAt: createdAt,
  },
  {
    id: "biz-willow",
    slug: "the-willow-house",
    name: "The Willow House",
    type: "Wedding Venue",
    description:
      "An intimate garden venue with a restored dining room, ceremony lawn, and private planning suites.",
    logoUrl: makeGradient("venue"),
    coverImageUrl: makeGradient("venue"),
    address: "Willow House Estate, Kildare",
    phone: "+35315550218",
    email: "events@willowhouse.example",
    website: "https://willowhouse.example",
    instagram: "https://instagram.com/thewillowhouse",
    bookingUrl: "https://willowhouse.example/enquire",
    menuUrl: "https://willowhouse.example/packages",
    openingHours: hours,
    themeId: "luxury-venue",
    status: "published",
    createdAt,
    updatedAt: createdAt,
  },
  {
    id: "biz-luma",
    slug: "luma-salon",
    name: "Luma Salon",
    type: "Salon / Beauty",
    description:
      "A calm beauty studio offering hair, skin, brows, and event styling in a soft private setting.",
    logoUrl: makeGradient("salon"),
    coverImageUrl: makeGradient("salon"),
    address: "5 Clarendon Row, Dublin 2",
    phone: "+35315550302",
    email: "appointments@lumasalon.example",
    website: "https://lumasalon.example",
    instagram: "https://instagram.com/lumasalon",
    bookingUrl: "https://lumasalon.example/book",
    menuUrl: "https://lumasalon.example/treatments",
    openingHours: hours,
    themeId: "opal-glass",
    status: "published",
    createdAt,
    updatedAt: createdAt,
  },
];

const buttonSets: Record<string, Array<[string, ShopfrontButton["type"], string, boolean]>> = {
  "biz-bloom": [
    ["Book a Table", "booking", "calendar", true],
    ["Enter 360° Tour", "tour", "route", false],
    ["View Menu", "menu", "menu", false],
    ["Directions", "directions", "map-pin", false],
    ["Events", "events", "sparkles", false],
    ["Call Us", "call", "phone", false],
  ],
  "biz-atlas": [
    ["Book Free Trial", "booking", "calendar", true],
    ["Enter 360° Tour", "tour", "route", false],
    ["Memberships", "offers", "badge-percent", false],
    ["Class Timetable", "menu", "clock", false],
    ["Directions", "directions", "map-pin", false],
    ["Call", "call", "phone", false],
  ],
  "biz-willow": [
    ["Request Quote", "booking", "calendar", true],
    ["Enter 360° Tour", "tour", "route", false],
    ["View Packages", "menu", "book-open", false],
    ["Check Availability", "events", "sparkles", false],
    ["Directions", "directions", "map-pin", false],
    ["Contact", "email", "mail", false],
  ],
  "biz-luma": [
    ["Book Appointment", "booking", "calendar", true],
    ["Enter 360° Tour", "tour", "route", false],
    ["Treatments", "menu", "sparkles", false],
    ["Prices", "offers", "badge-percent", false],
    ["Directions", "directions", "map-pin", false],
    ["Call", "call", "phone", false],
  ],
};

export const seedButtons: ShopfrontButton[] = Object.entries(buttonSets).flatMap(
  ([businessId, buttons]) =>
    buttons.map(([label, type, icon, isPrimary], index) => ({
      id: `btn-${businessId.replace("biz-", "")}-${index}`,
      businessId,
      label,
      type,
      icon,
      order: index,
      isPrimary,
      isEnabled: true,
    })),
);

export const seedTours: Tour[] = seedBusinesses.map((business) => ({
  id: `tour-${business.id.replace("biz-", "")}`,
  businessId: business.id,
  title: `${business.name} Virtual Tour`,
  description: "Explore before you visit, then book from any room.",
  status: "published",
  defaultSceneId:
    business.id === "biz-bloom"
      ? "scene-bloom-2"
      : `scene-${business.id.replace("biz-", "")}-1`,
}));

const sceneNames: Record<string, string[]> = {
  "biz-bloom": ["Street Entrance", "Dining Room", "Garden Room"],
  "biz-atlas": ["Welcome Desk", "Strength Floor", "Recovery Space"],
  "biz-willow": ["Ceremony Lawn", "Dining Hall", "Planning Suite"],
  "biz-luma": ["Reception", "Styling Studio", "Treatment Room"],
};

const gradientsByBusiness: Record<string, ReturnType<typeof makeGradient>> = {
  "biz-bloom": makeGradient("cafe"),
  "biz-atlas": makeGradient("fitness"),
  "biz-willow": makeGradient("venue"),
  "biz-luma": makeGradient("salon"),
};

const sceneImageOverrides: Record<string, string> = {
  "scene-bloom-2": "/venues/bloom-dining-room.jpeg?v=8192",
  "scene-bloom-3": "/venues/bloom-garden-room.jpeg?v=8192",
};

export const seedScenes: TourScene[] = seedTours.flatMap((tour) =>
  (sceneNames[tour.businessId] ?? ["Main Room"]).map((name, index) => ({
    id: `scene-${tour.businessId.replace("biz-", "")}-${index + 1}`,
    tourId: tour.id,
    name,
    description:
      index === 0
        ? "First impression and arrival point."
        : index === 1
          ? "The core space customers want to explore."
          : "A secondary area with bookable details.",
    imageUrl:
      sceneImageOverrides[`scene-${tour.businessId.replace("biz-", "")}-${index + 1}`] ??
      index === 0
        ? gradientsByBusiness[tour.businessId]
        : index === 1
          ? gradientsByBusiness[tour.businessId].replace("120deg", "90deg")
          : gradientsByBusiness[tour.businessId].replace("120deg", "155deg"),
    thumbnailUrl:
      sceneImageOverrides[`scene-${tour.businessId.replace("biz-", "")}-${index + 1}`] ??
      gradientsByBusiness[tour.businessId],
    order: index,
  })),
);

function sceneId(businessId: string, index: number) {
  return `scene-${businessId.replace("biz-", "")}-${index}`;
}

export const seedHotspots: Hotspot[] = [
  {
    id: "hot-bloom-book",
    sceneId: sceneId("biz-bloom", 1),
    label: "Reserve this table",
    type: "booking",
    x: 48,
    y: 58,
    icon: "calendar",
    description: "Open the booking form from inside the tour.",
    isEnabled: true,
  },
  {
    id: "hot-bloom-menu",
    sceneId: sceneId("biz-bloom", 2),
    label: "See brunch menu",
    type: "menu",
    x: 66,
    y: 44,
    icon: "menu",
    description: "Seasonal plates, coffee, and private dining menus.",
    isEnabled: true,
  },
  {
    id: "hot-bloom-garden",
    sceneId: sceneId("biz-bloom", 1),
    label: "Garden Room",
    type: "scene",
    x: 74,
    y: 38,
    targetSceneId: sceneId("biz-bloom", 3),
    icon: "door-open",
    description: "Move into the covered garden room.",
    isEnabled: true,
  },
  {
    id: "hot-atlas-trial",
    sceneId: sceneId("biz-atlas", 1),
    label: "Book free trial",
    type: "booking",
    x: 42,
    y: 54,
    icon: "calendar",
    description: "Send a trial request to the team.",
    isEnabled: true,
  },
  {
    id: "hot-atlas-timetable",
    sceneId: sceneId("biz-atlas", 2),
    label: "Class timetable",
    type: "menu",
    x: 61,
    y: 39,
    icon: "clock",
    description: "Open weekly classes and memberships.",
    isEnabled: true,
  },
  {
    id: "hot-willow-quote",
    sceneId: sceneId("biz-willow", 1),
    label: "Request a quote",
    type: "booking",
    x: 53,
    y: 57,
    icon: "calendar",
    description: "Share date, guest count, and event notes.",
    isEnabled: true,
  },
  {
    id: "hot-willow-packages",
    sceneId: sceneId("biz-willow", 2),
    label: "View packages",
    type: "menu",
    x: 36,
    y: 45,
    icon: "book-open",
    description: "Ceremony, dinner, and exclusive hire packages.",
    isEnabled: true,
  },
  {
    id: "hot-luma-book",
    sceneId: sceneId("biz-luma", 1),
    label: "Book appointment",
    type: "booking",
    x: 46,
    y: 60,
    icon: "calendar",
    description: "Request a preferred stylist and time.",
    isEnabled: true,
  },
  {
    id: "hot-luma-prices",
    sceneId: sceneId("biz-luma", 2),
    label: "Treatment prices",
    type: "menu",
    x: 62,
    y: 42,
    icon: "badge-percent",
    description: "Open treatment and price list.",
    isEnabled: true,
  },
];

function event(
  id: string,
  businessId: string,
  eventType: AnalyticsEvent["eventType"],
  targetLabel?: string,
  source = "direct",
  dayOffset = 1,
): AnalyticsEvent {
  const date = new Date("2026-07-01T12:00:00.000Z");
  date.setDate(date.getDate() - dayOffset);
  date.setMinutes(date.getMinutes() + Number(id.replace(/\D/g, "").slice(-2) || 0));
  return {
    id,
    businessId,
    eventType,
    targetId: targetLabel?.toLowerCase().replace(/[^a-z0-9]+/g, "-"),
    targetLabel,
    source,
    timestamp: date.toISOString(),
  };
}

export const seedAnalytics: AnalyticsEvent[] = [
  ...Array.from({ length: 42 }, (_, index) =>
    event(`ev-bloom-view-${index}`, "biz-bloom", "page_view", undefined, index % 3 === 0 ? "qr" : "direct", index % 7),
  ),
  ...Array.from({ length: 16 }, (_, index) =>
    event(`ev-bloom-tour-${index}`, "biz-bloom", "tour_start", "Enter 360° Tour", "shopfront", index % 7),
  ),
  ...Array.from({ length: 12 }, (_, index) =>
    event(`ev-bloom-book-${index}`, "biz-bloom", "booking_open", "Book a Table", "shopfront", index % 7),
  ),
  ...Array.from({ length: 6 }, (_, index) =>
    event(`ev-bloom-submit-${index}`, "biz-bloom", "booking_submit", "Book a Table", "tour", index % 7),
  ),
  ...Array.from({ length: 10 }, (_, index) =>
    event(`ev-bloom-menu-${index}`, "biz-bloom", "menu_open", "View Menu", "shopfront", index % 7),
  ),
  ...Array.from({ length: 7 }, (_, index) =>
    event(`ev-bloom-hot-${index}`, "biz-bloom", "hotspot_click", "Reserve this table", "tour", index % 5),
  ),
  ...Array.from({ length: 22 }, (_, index) =>
    event(`ev-atlas-view-${index}`, "biz-atlas", "page_view", undefined, index % 2 ? "instagram" : "direct", index % 8),
  ),
  ...Array.from({ length: 14 }, (_, index) =>
    event(`ev-willow-view-${index}`, "biz-willow", "page_view", undefined, "email", index % 9),
  ),
  ...Array.from({ length: 18 }, (_, index) =>
    event(`ev-luma-view-${index}`, "biz-luma", "page_view", undefined, "instagram", index % 6),
  ),
];

export const seedBookingLeads: BookingLead[] = [
  {
    id: "lead-bloom-1",
    businessId: "biz-bloom",
    source: "tour",
    customerName: "Niamh Byrne",
    customerEmail: "niamh@example.com",
    customerPhone: "+353861234567",
    requestedDate: "2026-07-12",
    requestedTime: "19:30",
    partySize: 4,
    message: "Could we sit in the garden room if possible?",
    status: "new",
    createdAt: "2026-07-01T17:12:00.000Z",
  },
  {
    id: "lead-bloom-2",
    businessId: "biz-bloom",
    source: "shopfront",
    customerName: "Alex Murphy",
    customerEmail: "alex@example.com",
    requestedDate: "2026-07-08",
    requestedTime: "10:00",
    partySize: 2,
    message: "Brunch booking after viewing the menu.",
    status: "contacted",
    createdAt: "2026-06-30T11:04:00.000Z",
  },
];

export function createSeedStore(): PortalStore {
  return {
    version: 1,
    activeBusinessId: "biz-bloom",
    businesses: seedBusinesses,
    buttons: seedButtons,
    tours: seedTours,
    scenes: seedScenes,
    hotspots: seedHotspots,
    bookingLeads: seedBookingLeads,
    analyticsEvents: seedAnalytics,
  };
}
