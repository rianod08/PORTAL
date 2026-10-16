import { createSeedStore } from "./seed";
import { getThemeById, THEMES } from "./themes";
import type {
  AnalyticsEvent,
  AnalyticsSummary,
  BookingLead,
  Business,
  BusinessType,
  Hotspot,
  OpeningHour,
  PortalStore,
  ShopfrontButton,
  Theme,
  Tour,
  TourScene,
} from "./types";
import { clamp, clone, createId, isBrowser, nowIso, slugify } from "./utils";

const STORE_KEY = "portal:store:v1";
const CHANGE_EVENT = "portal:data-change";

let memoryStore: PortalStore | null = null;
let cachedRawStore: string | null = null;
let cachedSnapshot: PortalStore | null = null;
const serverStoreSnapshot = createSeedStore();

const defaultHours: OpeningHour[] = [
  { day: "Mon", open: "09:00", close: "17:00" },
  { day: "Tue", open: "09:00", close: "17:00" },
  { day: "Wed", open: "09:00", close: "17:00" },
  { day: "Thu", open: "09:00", close: "18:00" },
  { day: "Fri", open: "09:00", close: "18:00" },
  { day: "Sat", open: "10:00", close: "16:00" },
  { day: "Sun", open: "10:00", close: "15:00", closed: true },
];

function emitChange(): void {
  if (!isBrowser()) return;
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT));
}

function migrateStore(store: PortalStore): PortalStore {
  const bloomDining = store.scenes.find((scene) => scene.id === "scene-bloom-2");
  const bloomGarden = store.scenes.find((scene) => scene.id === "scene-bloom-3");

  if (bloomDining) {
    bloomDining.imageUrl = "/venues/bloom-dining-room.jpeg?v=8192";
    bloomDining.thumbnailUrl = "/venues/bloom-dining-room.jpeg?v=8192";
  }

  if (bloomGarden) {
    bloomGarden.imageUrl = "/venues/bloom-garden-room.jpeg?v=8192";
    bloomGarden.thumbnailUrl = "/venues/bloom-garden-room.jpeg?v=8192";
  }

  const bloomTour = store.tours.find((tour) => tour.id === "tour-bloom");
  if (bloomTour) {
    bloomTour.defaultSceneId = "scene-bloom-2";
  }

  return store;
}

export function subscribeToPortalData(callback: () => void): () => void {
  if (!isBrowser()) return () => undefined;
  const handle = () => callback();
  window.addEventListener(CHANGE_EVENT, handle);
  window.addEventListener("storage", handle);
  return () => {
    window.removeEventListener(CHANGE_EVENT, handle);
    window.removeEventListener("storage", handle);
  };
}

export function getStore(): PortalStore {
  return clone(getStoreSnapshot());
}

export function getStoreSnapshot(): PortalStore {
  if (!isBrowser()) {
    if (!memoryStore) memoryStore = createSeedStore();
    return memoryStore;
  }

  const raw = window.localStorage.getItem(STORE_KEY);
  if (!raw) {
    const seeded = migrateStore(createSeedStore());
    const serialized = JSON.stringify(seeded);
    window.localStorage.setItem(STORE_KEY, serialized);
    cachedRawStore = serialized;
    cachedSnapshot = seeded;
    return seeded;
  }

  if (raw === cachedRawStore && cachedSnapshot) {
    return cachedSnapshot;
  }

  try {
    const parsed = migrateStore(JSON.parse(raw) as PortalStore);
    const migratedRaw = JSON.stringify(parsed);
    if (migratedRaw !== raw) {
      window.localStorage.setItem(STORE_KEY, migratedRaw);
    }
    cachedRawStore = migratedRaw;
    cachedSnapshot = parsed;
    return parsed;
  } catch {
    const seeded = migrateStore(createSeedStore());
    const serialized = JSON.stringify(seeded);
    window.localStorage.setItem(STORE_KEY, serialized);
    cachedRawStore = serialized;
    cachedSnapshot = seeded;
    return seeded;
  }
}

export function getServerStoreSnapshot(): PortalStore {
  return serverStoreSnapshot;
}

function saveStore(store: PortalStore): void {
  const next: PortalStore = { ...store, version: (store.version ?? 1) + 1 };
  if (isBrowser()) {
    const serialized = JSON.stringify(next);
    window.localStorage.setItem(STORE_KEY, serialized);
    cachedRawStore = serialized;
    cachedSnapshot = next;
  } else {
    memoryStore = clone(next);
  }
  emitChange();
}

function mutateStore<T>(mutator: (store: PortalStore) => T): T {
  const store = getStore();
  const result = mutator(store);
  saveStore(store);
  return result === undefined ? result : clone(result);
}

function requireBusiness(store: PortalStore, id: string): Business {
  const business = store.businesses.find((item) => item.id === id);
  if (!business) throw new Error("Business not found");
  return business;
}

function requireButton(store: PortalStore, id: string): ShopfrontButton {
  const button = store.buttons.find((item) => item.id === id);
  if (!button) throw new Error("Button not found");
  return button;
}

function requireTour(store: PortalStore, id: string): Tour {
  const tour = store.tours.find((item) => item.id === id);
  if (!tour) throw new Error("Tour not found");
  return tour;
}

function requireScene(store: PortalStore, id: string): TourScene {
  const scene = store.scenes.find((item) => item.id === id);
  if (!scene) throw new Error("Scene not found");
  return scene;
}

function requireHotspot(store: PortalStore, id: string): Hotspot {
  const hotspot = store.hotspots.find((item) => item.id === id);
  if (!hotspot) throw new Error("Hotspot not found");
  return hotspot;
}

export function resetDemoData(): PortalStore {
  const seeded = createSeedStore();
  saveStore(seeded);
  return getStore();
}

export function getActiveBusinessId(): string {
  return getStore().activeBusinessId;
}

export function setActiveBusinessId(businessId: string): void {
  mutateStore((store) => {
    requireBusiness(store, businessId);
    store.activeBusinessId = businessId;
  });
}

export function getActiveBusiness(): Business {
  const store = getStore();
  return clone(requireBusiness(store, store.activeBusinessId));
}

export function getBusinesses(): Business[] {
  return getStore().businesses;
}

export function getBusinessBySlug(slug: string): Business | null {
  return getStore().businesses.find((business) => business.slug === slug) ?? null;
}

export function getBusinessById(id: string): Business | null {
  return getStore().businesses.find((business) => business.id === id) ?? null;
}

export function generateUniqueSlug(name: string): string {
  const store = getStore();
  const base = slugify(name);
  let candidate = base;
  let index = 2;
  while (store.businesses.some((business) => business.slug === candidate)) {
    candidate = `${base}-${index}`;
    index += 1;
  }
  return candidate;
}

export function validateSlug(slug: string, currentBusinessId?: string): {
  valid: boolean;
  error?: string;
} {
  const normalized = slugify(slug);
  if (normalized.length < 3) return { valid: false, error: "Use at least 3 characters." };
  if (normalized !== slug) return { valid: false, error: "Use lowercase letters, numbers, and dashes." };
  const exists = getStore().businesses.some(
    (business) => business.slug === normalized && business.id !== currentBusinessId,
  );
  if (exists) return { valid: false, error: "That public link is already in use." };
  return { valid: true };
}

export function createBusiness(
  input: Partial<Business> & {
    name: string;
    type: BusinessType;
    description: string;
    address: string;
    phone: string;
    email: string;
  },
): Business {
  return mutateStore((store) => {
    const timestamp = nowIso();
    const business: Business = {
      id: input.id ?? createId("biz"),
      slug: input.slug ? generateUniqueSlug(input.slug) : generateUniqueSlug(input.name),
      name: input.name,
      type: input.type,
      description: input.description,
      logoUrl: input.logoUrl,
      coverImageUrl: input.coverImageUrl,
      address: input.address,
      phone: input.phone,
      email: input.email,
      website: input.website,
      instagram: input.instagram,
      bookingUrl: input.bookingUrl,
      menuUrl: input.menuUrl,
      openingHours: input.openingHours ?? defaultHours,
      themeId: input.themeId ?? "opal-glass",
      themeOverrides: input.themeOverrides,
      status: input.status ?? "draft",
      createdAt: input.createdAt ?? timestamp,
      updatedAt: timestamp,
    };
    store.businesses.push(business);
    store.activeBusinessId = business.id;
    return business;
  });
}

export function updateBusiness(id: string, patch: Partial<Business>): Business {
  return mutateStore((store) => {
    const business = requireBusiness(store, id);
    const nextSlug = patch.slug ? slugify(patch.slug) : business.slug;
    Object.assign(business, patch, {
      slug: nextSlug,
      updatedAt: nowIso(),
    });
    return business;
  });
}

export function deleteBusiness(id: string): void {
  mutateStore((store) => {
    requireBusiness(store, id);
    const tourIds = store.tours.filter((tour) => tour.businessId === id).map((tour) => tour.id);
    const sceneIds = store.scenes.filter((scene) => tourIds.includes(scene.tourId)).map((scene) => scene.id);
    store.businesses = store.businesses.filter((business) => business.id !== id);
    store.buttons = store.buttons.filter((button) => button.businessId !== id);
    store.tours = store.tours.filter((tour) => tour.businessId !== id);
    store.scenes = store.scenes.filter((scene) => !tourIds.includes(scene.tourId));
    store.hotspots = store.hotspots.filter((hotspot) => !sceneIds.includes(hotspot.sceneId));
    store.bookingLeads = store.bookingLeads.filter((lead) => lead.businessId !== id);
    store.analyticsEvents = store.analyticsEvents.filter((event) => event.businessId !== id);
    store.activeBusinessId = store.businesses[0]?.id ?? "";
  });
}

export function publishBusiness(id: string): Business {
  const business = updateBusiness(id, { status: "published" });
  recordEvent({ businessId: id, eventType: "publish", targetLabel: "Published", source: "dashboard" });
  return business;
}

export function unpublishBusiness(id: string): Business {
  return updateBusiness(id, { status: "draft" });
}

export function getButtonsForBusiness(businessId: string): ShopfrontButton[] {
  return getStore()
    .buttons.filter((button) => button.businessId === businessId)
    .sort((a, b) => a.order - b.order);
}

export function createButton(
  businessId: string,
  input: Partial<ShopfrontButton> & Pick<ShopfrontButton, "label" | "type">,
): ShopfrontButton {
  return mutateStore((store) => {
    requireBusiness(store, businessId);
    const order = store.buttons.filter((button) => button.businessId === businessId).length;
    if (input.isPrimary) {
      store.buttons.forEach((button) => {
        if (button.businessId === businessId) button.isPrimary = false;
      });
    }
    const button: ShopfrontButton = {
      id: input.id ?? createId("btn"),
      businessId,
      label: input.label,
      type: input.type,
      url: input.url,
      icon: input.icon ?? input.type,
      order: input.order ?? order,
      isPrimary: input.isPrimary ?? false,
      isEnabled: input.isEnabled ?? true,
    };
    store.buttons.push(button);
    return button;
  });
}

export function updateButton(buttonId: string, patch: Partial<ShopfrontButton>): ShopfrontButton {
  return mutateStore((store) => {
    const button = requireButton(store, buttonId);
    if (patch.isPrimary) {
      store.buttons.forEach((item) => {
        if (item.businessId === button.businessId) item.isPrimary = false;
      });
    }
    Object.assign(button, patch);
    return button;
  });
}

export function deleteButton(buttonId: string): void {
  mutateStore((store) => {
    const button = requireButton(store, buttonId);
    store.buttons = store.buttons.filter((item) => item.id !== buttonId);
    store.buttons
      .filter((item) => item.businessId === button.businessId)
      .sort((a, b) => a.order - b.order)
      .forEach((item, index) => {
        item.order = index;
      });
  });
}

export function reorderButtons(businessId: string, orderedIds: string[]): ShopfrontButton[] {
  return mutateStore((store) => {
    const order = new Map(orderedIds.map((id, index) => [id, index]));
    store.buttons.forEach((button) => {
      if (button.businessId === businessId && order.has(button.id)) {
        button.order = order.get(button.id) ?? button.order;
      }
    });
    return store.buttons
      .filter((button) => button.businessId === businessId)
      .sort((a, b) => a.order - b.order);
  });
}

export function toggleButton(buttonId: string): ShopfrontButton {
  return mutateStore((store) => {
    const button = requireButton(store, buttonId);
    button.isEnabled = !button.isEnabled;
    return button;
  });
}

export function setPrimaryButton(buttonId: string): ShopfrontButton {
  return updateButton(buttonId, { isPrimary: true });
}

export function getTourForBusiness(businessId: string): Tour | null {
  return getStore().tours.find((tour) => tour.businessId === businessId) ?? null;
}

export function createTour(
  businessId: string,
  input: Partial<Tour> & Pick<Tour, "title">,
): Tour {
  return mutateStore((store) => {
    requireBusiness(store, businessId);
    const tour: Tour = {
      id: input.id ?? createId("tour"),
      businessId,
      title: input.title,
      description: input.description,
      status: input.status ?? "draft",
      defaultSceneId: input.defaultSceneId,
    };
    store.tours.push(tour);
    return tour;
  });
}

export function updateTour(tourId: string, patch: Partial<Tour>): Tour {
  return mutateStore((store) => {
    const tour = requireTour(store, tourId);
    Object.assign(tour, patch);
    return tour;
  });
}

export function getScenesForTour(tourId: string): TourScene[] {
  return getStore()
    .scenes.filter((scene) => scene.tourId === tourId)
    .sort((a, b) => a.order - b.order);
}

export function createScene(
  tourId: string,
  input: Partial<TourScene> & Pick<TourScene, "name" | "imageUrl">,
): TourScene {
  return mutateStore((store) => {
    const tour = requireTour(store, tourId);
    const order = store.scenes.filter((scene) => scene.tourId === tourId).length;
    const scene: TourScene = {
      id: input.id ?? createId("scene"),
      tourId,
      name: input.name,
      description: input.description,
      imageUrl: input.imageUrl,
      thumbnailUrl: input.thumbnailUrl ?? input.imageUrl,
      order: input.order ?? order,
    };
    store.scenes.push(scene);
    if (!tour.defaultSceneId) tour.defaultSceneId = scene.id;
    return scene;
  });
}

export function updateScene(sceneId: string, patch: Partial<TourScene>): TourScene {
  return mutateStore((store) => {
    const scene = requireScene(store, sceneId);
    Object.assign(scene, patch);
    return scene;
  });
}

export function deleteScene(sceneId: string): void {
  mutateStore((store) => {
    const scene = requireScene(store, sceneId);
    store.scenes = store.scenes.filter((item) => item.id !== sceneId);
    store.hotspots = store.hotspots.filter((hotspot) => hotspot.sceneId !== sceneId);
    const remaining = store.scenes
      .filter((item) => item.tourId === scene.tourId)
      .sort((a, b) => a.order - b.order);
    remaining.forEach((item, index) => {
      item.order = index;
    });
    const tour = requireTour(store, scene.tourId);
    if (tour.defaultSceneId === sceneId) {
      tour.defaultSceneId = remaining[0]?.id;
    }
  });
}

export function reorderScenes(tourId: string, orderedIds: string[]): TourScene[] {
  return mutateStore((store) => {
    const order = new Map(orderedIds.map((id, index) => [id, index]));
    store.scenes.forEach((scene) => {
      if (scene.tourId === tourId && order.has(scene.id)) {
        scene.order = order.get(scene.id) ?? scene.order;
      }
    });
    return store.scenes.filter((scene) => scene.tourId === tourId).sort((a, b) => a.order - b.order);
  });
}

export function setDefaultScene(tourId: string, sceneId: string): Tour {
  return mutateStore((store) => {
    const tour = requireTour(store, tourId);
    const scene = requireScene(store, sceneId);
    if (scene.tourId !== tourId) throw new Error("Scene does not belong to this tour");
    tour.defaultSceneId = sceneId;
    return tour;
  });
}

export function getHotspotsForScene(sceneId: string): Hotspot[] {
  return getStore().hotspots.filter((hotspot) => hotspot.sceneId === sceneId);
}

export function createHotspot(
  sceneId: string,
  input: Partial<Hotspot> & Pick<Hotspot, "label" | "type" | "x" | "y">,
): Hotspot {
  return mutateStore((store) => {
    requireScene(store, sceneId);
    const hotspot: Hotspot = {
      id: input.id ?? createId("hot"),
      sceneId,
      label: input.label,
      type: input.type,
      x: clamp(input.x, 5, 95),
      y: clamp(input.y, 8, 92),
      targetUrl: input.targetUrl,
      targetSceneId: input.targetSceneId,
      icon: input.icon ?? input.type,
      description: input.description,
      isEnabled: input.isEnabled ?? true,
    };
    store.hotspots.push(hotspot);
    return hotspot;
  });
}

export function updateHotspot(hotspotId: string, patch: Partial<Hotspot>): Hotspot {
  return mutateStore((store) => {
    const hotspot = requireHotspot(store, hotspotId);
    Object.assign(hotspot, patch);
    hotspot.x = clamp(hotspot.x, 5, 95);
    hotspot.y = clamp(hotspot.y, 8, 92);
    return hotspot;
  });
}

export function deleteHotspot(hotspotId: string): void {
  mutateStore((store) => {
    requireHotspot(store, hotspotId);
    store.hotspots = store.hotspots.filter((hotspot) => hotspot.id !== hotspotId);
  });
}

export function moveHotspot(hotspotId: string, x: number, y: number): Hotspot {
  return updateHotspot(hotspotId, { x, y });
}

export function toggleHotspot(hotspotId: string): Hotspot {
  return mutateStore((store) => {
    const hotspot = requireHotspot(store, hotspotId);
    hotspot.isEnabled = !hotspot.isEnabled;
    return hotspot;
  });
}

export function getThemes(): Theme[] {
  return THEMES;
}

export function applyTheme(businessId: string, themeId: string): Business {
  getThemeById(themeId);
  return updateBusiness(businessId, { themeId });
}

export function getCurrentTheme(businessId: string): Theme {
  const business = getBusinessById(businessId);
  if (!business) return THEMES[0];
  const base = getThemeById(business.themeId);
  return { ...base, ...business.themeOverrides };
}

export function recordEvent(
  event: Omit<Partial<AnalyticsEvent>, "id" | "timestamp"> & {
    businessId: string;
    eventType: AnalyticsEvent["eventType"];
  },
): void {
  mutateStore((store) => {
    requireBusiness(store, event.businessId);
    store.analyticsEvents.push({
      id: createId("ev"),
      businessId: event.businessId,
      eventType: event.eventType,
      targetId: event.targetId,
      targetLabel: event.targetLabel,
      source: event.source ?? "direct",
      timestamp: nowIso(),
    });
  });
}

function countEvents(events: AnalyticsEvent[], type: AnalyticsEvent["eventType"]): number {
  return events.filter((event) => event.eventType === type).length;
}

export function getAnalyticsSummary(businessId: string): AnalyticsSummary {
  const events = getStore().analyticsEvents.filter((event) => event.businessId === businessId);
  const pageViews = countEvents(events, "page_view");
  const bookingSubmissions = countEvents(events, "booking_submit");
  const sparkline = Array.from({ length: 7 }, (_, index) => {
    const day = new Date();
    day.setDate(day.getDate() - (6 - index));
    const key = day.toISOString().slice(0, 10);
    return events.filter((event) => event.timestamp.slice(0, 10) === key).length;
  });

  const sceneClicks = new Map<string, number>();
  events
    .filter((event) => event.source?.startsWith("scene:"))
    .forEach((event) => {
      const name = event.source?.replace("scene:", "") ?? "";
      sceneClicks.set(name, (sceneClicks.get(name) ?? 0) + 1);
    });

  return {
    totalVisits: pageViews,
    tourStarts: countEvents(events, "tour_start"),
    bookingOpens: countEvents(events, "booking_open"),
    bookingSubmissions,
    menuOpens: countEvents(events, "menu_open"),
    directionsClicks: countEvents(events, "directions_click"),
    callClicks: countEvents(events, "call_click"),
    hotspotClicks: countEvents(events, "hotspot_click"),
    buttonClicks: countEvents(events, "button_click"),
    publishEvents: countEvents(events, "publish"),
    conversionRate: pageViews ? (bookingSubmissions / pageViews) * 100 : 0,
    mostViewedScene: [...sceneClicks.entries()].sort((a, b) => b[1] - a[1])[0]?.[0],
    recentEvents: events.slice(-8).reverse(),
    sparkline,
  };
}

export function getTopButtons(businessId: string): Array<{ label: string; clicks: number }> {
  const relevantTypes: AnalyticsEvent["eventType"][] = [
    "button_click",
    "booking_open",
    "menu_open",
    "directions_click",
    "call_click",
  ];
  const grouped = new Map<string, number>();
  getStore()
    .analyticsEvents.filter((event) => event.businessId === businessId && relevantTypes.includes(event.eventType))
    .forEach((event) => {
      const label = event.targetLabel ?? event.eventType.replace(/_/g, " ");
      grouped.set(label, (grouped.get(label) ?? 0) + 1);
    });
  return [...grouped.entries()]
    .map(([label, clicks]) => ({ label, clicks }))
    .sort((a, b) => b.clicks - a.clicks)
    .slice(0, 5);
}

export function getTopHotspots(businessId: string): Array<{ label: string; clicks: number }> {
  const grouped = new Map<string, number>();
  getStore()
    .analyticsEvents.filter((event) => event.businessId === businessId && event.eventType === "hotspot_click")
    .forEach((event) => {
      const label = event.targetLabel ?? "Hotspot";
      grouped.set(label, (grouped.get(label) ?? 0) + 1);
    });
  return [...grouped.entries()]
    .map(([label, clicks]) => ({ label, clicks }))
    .sort((a, b) => b.clicks - a.clicks)
    .slice(0, 5);
}

export function getTrafficSources(businessId: string): Array<{ source: string; percentage: number }> {
  const events = getStore().analyticsEvents.filter(
    (event) => event.businessId === businessId && event.eventType === "page_view",
  );
  const grouped = new Map<string, number>();
  events.forEach((event) => {
    const source = event.source || "direct";
    grouped.set(source, (grouped.get(source) ?? 0) + 1);
  });
  const total = events.length || 1;
  return [...grouped.entries()]
    .map(([source, count]) => ({ source, percentage: (count / total) * 100 }))
    .sort((a, b) => b.percentage - a.percentage);
}

export function getConversionRate(businessId: string): number {
  return getAnalyticsSummary(businessId).conversionRate;
}

export function clearAnalytics(businessId: string): void {
  mutateStore((store) => {
    store.analyticsEvents = store.analyticsEvents.filter((event) => event.businessId !== businessId);
  });
}

export function openBookingModal(source: BookingLead["source"], targetId?: string): void {
  if (!isBrowser()) return;
  window.dispatchEvent(new CustomEvent("portal:open-booking", { detail: { source, targetId } }));
}

export function submitBookingLead(
  input: Omit<Partial<BookingLead>, "id" | "createdAt"> &
    Pick<BookingLead, "businessId" | "source" | "customerName" | "customerEmail">,
): BookingLead {
  if (!input.customerName.trim()) throw new Error("Customer name is required");
  if (!input.customerEmail.includes("@")) throw new Error("A valid email is required");
  return mutateStore((store) => {
    requireBusiness(store, input.businessId);
    const lead: BookingLead = {
      id: createId("lead"),
      businessId: input.businessId,
      source: input.source,
      customerName: input.customerName,
      customerEmail: input.customerEmail,
      customerPhone: input.customerPhone,
      requestedDate: input.requestedDate,
      requestedTime: input.requestedTime,
      partySize: input.partySize,
      message: input.message,
      status: input.status ?? "new",
      createdAt: nowIso(),
    };
    store.bookingLeads.push(lead);
    return lead;
  });
}

export function getBookingLeads(businessId: string): BookingLead[] {
  return getStore()
    .bookingLeads.filter((lead) => lead.businessId === businessId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function updateBookingLeadStatus(
  leadId: string,
  status: BookingLead["status"],
): BookingLead {
  return mutateStore((store) => {
    const lead = store.bookingLeads.find((item) => item.id === leadId);
    if (!lead) throw new Error("Booking lead not found");
    lead.status = status;
    return lead;
  });
}

export function deleteBookingLead(leadId: string): void {
  mutateStore((store) => {
    store.bookingLeads = store.bookingLeads.filter((lead) => lead.id !== leadId);
  });
}

export function clearBookingLeads(businessId: string): void {
  mutateStore((store) => {
    store.bookingLeads = store.bookingLeads.filter((lead) => lead.businessId !== businessId);
  });
}

export function createDefaultButtonsForBusiness(businessId: string, type: BusinessType): ShopfrontButton[] {
  const labels: Record<BusinessType, Array<[string, ShopfrontButton["type"], string, boolean]>> = {
    "Restaurant / Cafe": [
      ["Book a Table", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["View Menu", "menu", "menu", false],
      ["Directions", "directions", "map-pin", false],
      ["Events", "events", "sparkles", false],
      ["Call", "call", "phone", false],
    ],
    "Bar / Nightlife": [
      ["Reserve Table", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Drinks Menu", "menu", "menu", false],
      ["Offers", "offers", "badge-percent", false],
      ["Directions", "directions", "map-pin", false],
      ["Call", "call", "phone", false],
    ],
    "Gym / Fitness": [
      ["Book Free Trial", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Memberships", "offers", "badge-percent", false],
      ["Class Timetable", "menu", "clock", false],
      ["Directions", "directions", "map-pin", false],
      ["Call", "call", "phone", false],
    ],
    "Salon / Beauty": [
      ["Book Appointment", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Treatments", "menu", "sparkles", false],
      ["Prices", "offers", "badge-percent", false],
      ["Directions", "directions", "map-pin", false],
      ["Call", "call", "phone", false],
    ],
    "Clinic / Wellness": [
      ["Request Appointment", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Services", "menu", "list", false],
      ["Directions", "directions", "map-pin", false],
      ["Email", "email", "mail", false],
      ["Call", "call", "phone", false],
    ],
    "Event Venue": [
      ["Request Quote", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Packages", "menu", "book-open", false],
      ["Availability", "events", "sparkles", false],
      ["Directions", "directions", "map-pin", false],
      ["Contact", "email", "mail", false],
    ],
    "Wedding Venue": [
      ["Request Quote", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Packages", "menu", "book-open", false],
      ["Availability", "events", "sparkles", false],
      ["Directions", "directions", "map-pin", false],
      ["Contact", "email", "mail", false],
    ],
    "Retail Showroom": [
      ["Book Visit", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Products", "menu", "shopping-bag", false],
      ["Offers", "offers", "badge-percent", false],
      ["Directions", "directions", "map-pin", false],
      ["Call", "call", "phone", false],
    ],
    "Studio / Creative Space": [
      ["Book Viewing", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Facilities", "menu", "list", false],
      ["Rates", "offers", "badge-percent", false],
      ["Directions", "directions", "map-pin", false],
      ["Email", "email", "mail", false],
    ],
    Other: [
      ["Book Now", "booking", "calendar", true],
      ["Enter 360° Tour", "tour", "route", false],
      ["Details", "menu", "list", false],
      ["Directions", "directions", "map-pin", false],
      ["Website", "website", "globe", false],
      ["Call", "call", "phone", false],
    ],
  };

  return labels[type].map(([label, buttonType, icon, isPrimary]) =>
    createButton(businessId, {
      label,
      type: buttonType,
      icon,
      isPrimary,
      isEnabled: true,
    }),
  );
}
