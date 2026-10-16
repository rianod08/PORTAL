"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  Trash2,
} from "lucide-react";
import {
  AnalyticsCard,
  DashboardShell,
  DonutChart,
  EditableField,
  EmptyState,
  GlassButton,
  GlassCard,
  HotspotEditor,
  Icon,
  ImageUploader,
  PhoneShopfrontPreview,
  QRCodePanel,
  Sparkline,
  ThemePicker,
  useToast,
} from "@/components/PortalUI";
import {
  applyTheme,
  clearAnalytics,
  clearBookingLeads,
  createBusiness,
  createButton,
  createDefaultButtonsForBusiness,
  createHotspot,
  createScene,
  createTour,
  deleteBookingLead,
  deleteButton,
  deleteHotspot,
  deleteScene,
  getActiveBusinessId,
  getAnalyticsSummary,
  getBookingLeads,
  getBusinessById,
  getButtonsForBusiness,
  getConversionRate,
  getCurrentTheme,
  getHotspotsForScene,
  getScenesForTour,
  getThemes,
  getTopButtons,
  getTopHotspots,
  getTourForBusiness,
  getTrafficSources,
  moveHotspot,
  publishBusiness,
  recordEvent,
  reorderButtons,
  reorderScenes,
  resetDemoData,
  setDefaultScene,
  toggleButton,
  toggleHotspot,
  unpublishBusiness,
  updateBookingLeadStatus,
  updateBusiness,
  updateButton,
  updateHotspot,
  updateScene,
  updateTour,
  validateSlug,
} from "@/lib/data";
import type {
  BookingLead,
  Business,
  BusinessType,
  Hotspot,
  ShopfrontButton,
  Theme,
  TourScene,
} from "@/lib/types";
import { makeGradient, mediaBackground, publicVenueUrl, slugify } from "@/lib/utils";
import { usePortalData } from "@/hooks/usePortalData";

const businessTypes: BusinessType[] = [
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

const buttonTypes: ShopfrontButton["type"][] = [
  "booking",
  "tour",
  "menu",
  "directions",
  "call",
  "email",
  "website",
  "instagram",
  "events",
  "offers",
  "custom",
];

function useOwnerData() {
  const store = usePortalData();
  const business = getBusinessById(store.activeBusinessId || getActiveBusinessId()) ?? store.businesses[0];
  const theme = business ? getCurrentTheme(business.id) : getThemes()[0];
  const buttons = business ? getButtonsForBusiness(business.id) : [];
  const tour = business ? getTourForBusiness(business.id) : null;
  const scenes = tour ? getScenesForTour(tour.id) : [];
  return { store, business, theme, buttons, tour, scenes };
}

function MissingBusiness() {
  return (
    <DashboardShell business={{
      id: "empty",
      slug: "empty",
      name: "Portal",
      type: "Other",
      description: "",
      address: "",
      phone: "",
      email: "",
      openingHours: [],
      themeId: "opal-glass",
      status: "draft",
      createdAt: "",
      updatedAt: "",
    }} title="No business">
      <EmptyState icon="building" title="No business yet" body="Create your first venue page in onboarding." action={<GlassButton href="/app/onboarding">Start onboarding</GlassButton>} />
    </DashboardShell>
  );
}

export function DashboardHomePage() {
  const { business, theme, buttons, scenes } = useOwnerData();
  if (!business) return <MissingBusiness />;
  const summary = getAnalyticsSummary(business.id);
  const traffic = getTrafficSources(business.id);
  const topHotspots = getTopHotspots(business.id);
  const fallbackScene = scenes[0]?.name ?? "No scenes yet";

  return (
    <DashboardShell
      business={business}
      title="Dashboard"
      action={
        <div className="flex gap-2">
          <GlassButton href={`/v/${business.slug}`} icon="eye">View Public Page</GlassButton>
          <GlassButton href="/app/publish" icon="sparkles" variant="primary">Publish</GlassButton>
        </div>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <AnalyticsCard label="Total Views" value={summary.totalVisits} summary={summary} accent={theme.accentColor} />
        <AnalyticsCard label="Tour Starts" value={summary.tourStarts} summary={summary} accent="#8b5cf6" />
        <AnalyticsCard label="Booking Clicks" value={summary.bookingOpens} summary={summary} accent="#10b981" />
        <AnalyticsCard label="Menu Clicks" value={summary.menuOpens} summary={summary} accent="#f59e0b" />
      </div>
      <div className="mt-5 grid gap-5 xl:grid-cols-[1fr_390px]">
        <div className="grid gap-5 lg:grid-cols-2">
          <GlassCard className="p-5">
            <h2 className="text-2xl font-black">Top Performing Hotspot</h2>
            <div className="mt-4 grid gap-3">
              {(topHotspots.length ? topHotspots : [{ label: "Reserve this table", clicks: 0 }]).map((item) => (
                <div key={item.label} className="flex items-center justify-between rounded-2xl bg-white/58 p-4">
                  <span className="font-bold">{item.label}</span>
                  <span className="rounded-full bg-slate-950 px-3 py-1 text-sm font-bold text-white">{item.clicks}</span>
                </div>
              ))}
            </div>
          </GlassCard>
          <GlassCard className="p-5">
            <h2 className="text-2xl font-black">Traffic Sources</h2>
            <div className="mt-4">
              {traffic.length ? <DonutChart data={traffic} /> : <EmptyState icon="bar-chart" title="No visits yet" body="Visits will appear when customers view your page." />}
            </div>
          </GlassCard>
          <GlassCard className="p-5 lg:col-span-2">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-2xl font-black">Most viewed scene</h2>
                <p className="mt-1 text-slate-600">{summary.mostViewedScene ?? fallbackScene}</p>
              </div>
              <div className="flex gap-2">
                <GlassButton href="/app/shopfront" icon="layers">Edit Shopfront</GlassButton>
                <GlassButton href="/app/tour" icon="route">Edit Tour</GlassButton>
              </div>
            </div>
          </GlassCard>
        </div>
        <GlassCard className="p-5">
          <h2 className="mb-4 text-2xl font-black">Preview Public Page</h2>
          <PhoneShopfrontPreview business={business} buttons={buttons} theme={theme} />
        </GlassCard>
      </div>
    </DashboardShell>
  );
}

export function OnboardingPage() {
  const router = useRouter();
  const { pushToast } = useToast();
  const [step, setStep] = useState(1);
  const [error, setError] = useState("");
  const [type, setType] = useState<BusinessType>("Restaurant / Cafe");
  const [details, setDetails] = useState({
    name: "",
    description: "",
    address: "",
    phone: "",
    email: "",
    website: "",
    instagram: "",
    bookingUrl: "",
    menuUrl: "",
  });
  const [logoUrl, setLogoUrl] = useState("");
  const [coverImageUrl, setCoverImageUrl] = useState("");
  const [images, setImages] = useState<string[]>([]);
  const [rooms, setRooms] = useState(["Main Room"]);
  const [themeId, setThemeId] = useState("opal-glass");

  function validateCurrent() {
    setError("");
    if (step === 2) {
      if (!details.name.trim() || !details.description.trim() || !details.address.trim() || !details.email.includes("@")) {
        setError("Add a name, description, address, and valid email before continuing.");
        return false;
      }
    }
    if (step === 3 && rooms.every((room) => !room.trim())) {
      setError("Add at least one room name.");
      return false;
    }
    return true;
  }

  function next() {
    if (validateCurrent()) setStep((value) => Math.min(5, value + 1));
  }

  function generate() {
    if (!validateCurrent()) return;
    const business = createBusiness({
      name: details.name,
      type,
      description: details.description,
      address: details.address,
      phone: details.phone,
      email: details.email,
      website: details.website,
      instagram: details.instagram,
      bookingUrl: details.bookingUrl,
      menuUrl: details.menuUrl,
      logoUrl: logoUrl || makeGradient("studio"),
      coverImageUrl: coverImageUrl || images[0] || makeGradient(type === "Gym / Fitness" ? "fitness" : type.includes("Venue") ? "venue" : type === "Salon / Beauty" ? "salon" : "cafe"),
      themeId,
      status: "draft",
    });
    createDefaultButtonsForBusiness(business.id, type);
    const tour = createTour(business.id, { title: `${business.name} Virtual Tour`, description: "Explore before you visit." });
    rooms.filter(Boolean).forEach((room, index) => {
      const scene = createScene(tour.id, {
        name: room,
        imageUrl: images[index] || coverImageUrl || makeGradient("studio"),
        order: index,
      });
      if (index === 0) updateTour(tour.id, { defaultSceneId: scene.id });
    });
    pushToast("Venue page created");
    router.push("/app");
  }

  return (
    <DashboardShell business={{
      id: "onboarding",
      slug: "onboarding",
      name: "New venue",
      type,
      description: "",
      address: "",
      phone: "",
      email: "",
      openingHours: [],
      themeId,
      status: "draft",
      createdAt: "",
      updatedAt: "",
    }} title="Setup your venue" eyebrow={`Step ${step} of 5`}>
      <GlassCard className="p-5">
        <div className="mb-5 grid grid-cols-5 gap-2">
          {[1, 2, 3, 4, 5].map((item) => (
            <div key={item} className={`h-2 rounded-full ${item <= step ? "bg-slate-950" : "bg-white/60"}`} />
          ))}
        </div>

        {step === 1 ? (
          <div>
            <h2 className="text-2xl font-black">Choose business type</h2>
            <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {businessTypes.map((item) => (
                <button key={item} className={`rounded-3xl p-4 text-left font-bold ${type === item ? "bg-slate-950 text-white" : "bg-white/58"}`} onClick={() => setType(item)} type="button">
                  {item}
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {step === 2 ? (
          <div className="grid gap-4">
            <h2 className="text-2xl font-black">Add business details</h2>
            {Object.entries(details).map(([key, value]) => (
              <EditableField
                key={key}
                label={key.replace(/([A-Z])/g, " $1").replace(/^./, (char) => char.toUpperCase())}
                value={value}
                textarea={key === "description"}
                onChange={(nextValue) => setDetails({ ...details, [key]: nextValue })}
              />
            ))}
          </div>
        ) : null}

        {step === 3 ? (
          <div className="grid gap-5">
            <h2 className="text-2xl font-black">Upload venue</h2>
            <div className="grid gap-4 md:grid-cols-3">
              <ImageUploader label="Upload logo" variant="logo" onUploaded={setLogoUrl} />
              <ImageUploader label="Upload cover image" onUploaded={setCoverImageUrl} />
              <ImageUploader label="Upload 360 or venue image" onUploaded={(url) => setImages((items) => [...items, url])} />
            </div>
            <div className="grid gap-3">
              <div className="flex items-center justify-between">
                <h3 className="font-black">Room names</h3>
                <GlassButton icon="plus" onClick={() => setRooms((items) => [...items, `Room ${items.length + 1}`])}>Add room</GlassButton>
              </div>
              {rooms.map((room, index) => (
                <div key={index} className="flex gap-2">
                  <input className="min-h-12 flex-1 rounded-2xl bg-white/70 px-4" value={room} onChange={(event) => setRooms((items) => items.map((item, itemIndex) => itemIndex === index ? event.target.value : item))} />
                  <button aria-label="Remove room" className="rounded-2xl bg-white/60 px-4" onClick={() => setRooms((items) => items.filter((_, itemIndex) => itemIndex !== index))} type="button">
                    <Trash2 size={18} />
                  </button>
                </div>
              ))}
              <div className="grid gap-2 sm:grid-cols-3">
                {images.map((image, index) => (
                  <div key={image} className="h-28 rounded-2xl bg-cover bg-center" style={mediaBackground(image)}>
                    <button className="m-2 rounded-full bg-white/80 px-3 py-1 text-xs font-bold" onClick={() => setImages((items) => items.filter((_, itemIndex) => itemIndex !== index))} type="button">Remove</button>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : null}

        {step === 4 ? (
          <div>
            <h2 className="mb-5 text-2xl font-black">Choose style</h2>
            <ThemePicker themes={getThemes()} selectedThemeId={themeId} onSelect={setThemeId} />
          </div>
        ) : null}

        {step === 5 ? (
          <div className="grid gap-5 lg:grid-cols-[1fr_380px]">
            <div>
              <h2 className="text-2xl font-black">Generate page</h2>
              <p className="mt-2 leading-7 text-slate-600">
                Portal will create your business profile, default shopfront buttons, and tour scenes from your uploads.
              </p>
              <div className="mt-5 grid gap-3">
                <div className="rounded-3xl bg-white/58 p-4 font-bold">{details.name || "Venue name"}</div>
                <div className="rounded-3xl bg-white/58 p-4">{rooms.length} scene(s), {images.length} uploaded image(s)</div>
              </div>
            </div>
            <GlassCard className="p-5">
              <Icon name="sparkles" size={28} />
              <h3 className="mt-3 text-xl font-black">Ready to publish later</h3>
              <p className="mt-2 text-slate-600">You can edit every button, hotspot, image, and style after creation.</p>
            </GlassCard>
          </div>
        ) : null}

        {error ? <p className="mt-5 rounded-2xl bg-red-100 px-4 py-3 font-semibold text-red-700">{error}</p> : null}
        <div className="mt-6 flex justify-between gap-3">
          <GlassButton disabled={step === 1} onClick={() => setStep((value) => Math.max(1, value - 1))}>Back</GlassButton>
          {step < 5 ? <GlassButton onClick={next} variant="primary">Continue</GlassButton> : <GlassButton icon="sparkles" onClick={generate} variant="primary">Create Business</GlassButton>}
        </div>
      </GlassCard>
    </DashboardShell>
  );
}

export function ShopfrontEditorPage() {
  const { business, theme, buttons } = useOwnerData();
  if (!business) return <MissingBusiness />;
  return (
    <DashboardShell business={business} title="Shopfront editor" action={<GlassButton href={`/v/${business.slug}`} icon="eye">Preview</GlassButton>}>
      <ShopfrontEditorContent key={business.updatedAt} business={business} buttons={buttons} theme={theme} />
    </DashboardShell>
  );
}

function ShopfrontEditorContent({ business, buttons, theme }: { business: Business; buttons: ShopfrontButton[]; theme: Theme }) {
  const { pushToast } = useToast();
  const [draft, setDraft] = useState(business);
  const [newButton, setNewButton] = useState({ label: "New Link", type: "custom" as ShopfrontButton["type"], icon: "link" });

  function save() {
    updateBusiness(business.id, draft);
    pushToast("Shopfront saved");
  }

  function moveButton(button: ShopfrontButton, direction: -1 | 1) {
    const ordered = [...buttons].sort((a, b) => a.order - b.order);
    const index = ordered.findIndex((item) => item.id === button.id);
    const swap = index + direction;
    if (swap < 0 || swap >= ordered.length) return;
    [ordered[index], ordered[swap]] = [ordered[swap], ordered[index]];
    reorderButtons(business.id, ordered.map((item) => item.id));
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[1fr_420px]">
      <div className="grid gap-5">
        <GlassCard className="grid gap-4 p-5">
          <h2 className="text-2xl font-black">Business details</h2>
          <EditableField label="Business name" value={draft.name} onChange={(name) => setDraft({ ...draft, name })} />
          <EditableField label="Description" textarea value={draft.description} onChange={(description) => setDraft({ ...draft, description })} />
          <div className="grid gap-4 md:grid-cols-2">
            <ImageUploader label="Replace logo" variant="logo" onUploaded={(logoUrl) => setDraft({ ...draft, logoUrl })} />
            <ImageUploader label="Replace cover image" onUploaded={(coverImageUrl) => setDraft({ ...draft, coverImageUrl })} />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <EditableField label="Address" value={draft.address} onChange={(address) => setDraft({ ...draft, address })} />
            <EditableField label="Phone" value={draft.phone} onChange={(phone) => setDraft({ ...draft, phone })} />
            <EditableField label="Email" value={draft.email} onChange={(email) => setDraft({ ...draft, email })} />
            <EditableField label="Website" value={draft.website ?? ""} onChange={(website) => setDraft({ ...draft, website })} />
            <EditableField label="Instagram" value={draft.instagram ?? ""} onChange={(instagram) => setDraft({ ...draft, instagram })} />
            <EditableField label="Booking URL" value={draft.bookingUrl ?? ""} onChange={(bookingUrl) => setDraft({ ...draft, bookingUrl })} />
            <EditableField label="Menu URL" value={draft.menuUrl ?? ""} onChange={(menuUrl) => setDraft({ ...draft, menuUrl })} />
          </div>
          <h3 className="text-xl font-black">Opening hours</h3>
          <div className="grid gap-2">
            {draft.openingHours.map((hour, index) => (
              <div key={hour.day} className="grid grid-cols-[70px_1fr_1fr_80px] gap-2">
                <div className="rounded-2xl bg-white/60 px-3 py-3 font-bold">{hour.day}</div>
                <input className="rounded-2xl bg-white/70 px-3" value={hour.open} onChange={(event) => setDraft({ ...draft, openingHours: draft.openingHours.map((item, itemIndex) => itemIndex === index ? { ...item, open: event.target.value } : item) })} />
                <input className="rounded-2xl bg-white/70 px-3" value={hour.close} onChange={(event) => setDraft({ ...draft, openingHours: draft.openingHours.map((item, itemIndex) => itemIndex === index ? { ...item, close: event.target.value } : item) })} />
                <label className="flex items-center gap-2 rounded-2xl bg-white/60 px-3 text-sm font-bold">
                  <input type="checkbox" checked={Boolean(hour.closed)} onChange={(event) => setDraft({ ...draft, openingHours: draft.openingHours.map((item, itemIndex) => itemIndex === index ? { ...item, closed: event.target.checked } : item) })} />
                  Closed
                </label>
              </div>
            ))}
          </div>
          <GlassButton icon="save" onClick={save} variant="primary">Save changes</GlassButton>
        </GlassCard>

        <GlassCard className="p-5">
          <h2 className="text-2xl font-black">Buttons</h2>
          <div className="mt-4 grid gap-3">
            {buttons.map((button) => (
              <div key={button.id} className="grid gap-2 rounded-3xl bg-white/58 p-3 lg:grid-cols-[1fr_150px_120px_190px]">
                <input className="rounded-2xl bg-white/70 px-3 py-2 font-bold" value={button.label} onChange={(event) => updateButton(button.id, { label: event.target.value })} />
                <select className="rounded-2xl bg-white/70 px-3" value={button.type} onChange={(event) => updateButton(button.id, { type: event.target.value as ShopfrontButton["type"], icon: event.target.value })}>
                  {buttonTypes.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
                <input className="rounded-2xl bg-white/70 px-3" value={button.icon} onChange={(event) => updateButton(button.id, { icon: event.target.value })} />
                <div className="flex flex-wrap gap-2">
                  <button aria-label="Move up" className="rounded-full bg-white/70 p-3" onClick={() => moveButton(button, -1)} type="button"><ArrowUp size={16} /></button>
                  <button aria-label="Move down" className="rounded-full bg-white/70 p-3" onClick={() => moveButton(button, 1)} type="button"><ArrowDown size={16} /></button>
                  <button className="rounded-full bg-white/70 px-3 text-xs font-bold" onClick={() => toggleButton(button.id)} type="button">{button.isEnabled ? "On" : "Off"}</button>
                  <button className="rounded-full bg-white/70 px-3 text-xs font-bold" onClick={() => updateButton(button.id, { isPrimary: true })} type="button">{button.isPrimary ? "Primary" : "Make primary"}</button>
                  <button aria-label="Delete button" className="rounded-full bg-red-100 p-3 text-red-700" onClick={() => deleteButton(button.id)} type="button"><Trash2 size={16} /></button>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-4 grid gap-3 rounded-3xl bg-white/38 p-3 md:grid-cols-[1fr_160px_140px_auto]">
            <input className="rounded-2xl bg-white/70 px-3 py-3" value={newButton.label} onChange={(event) => setNewButton({ ...newButton, label: event.target.value })} />
            <select className="rounded-2xl bg-white/70 px-3" value={newButton.type} onChange={(event) => setNewButton({ ...newButton, type: event.target.value as ShopfrontButton["type"] })}>
              {buttonTypes.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
            <input className="rounded-2xl bg-white/70 px-3" value={newButton.icon} onChange={(event) => setNewButton({ ...newButton, icon: event.target.value })} />
            <GlassButton icon="plus" onClick={() => createButton(business.id, newButton)}>Add</GlassButton>
          </div>
        </GlassCard>
      </div>
      <div className="xl:sticky xl:top-5 xl:self-start">
        <PhoneShopfrontPreview business={draft} buttons={buttons} theme={theme} />
      </div>
    </div>
  );
}

export function TourEditorPage() {
  const { business, theme, tour, scenes } = useOwnerData();
  if (!business) return <MissingBusiness />;
  if (!tour) {
    return (
      <DashboardShell business={business} title="Tour editor">
        <EmptyState icon="route" title="No tour yet" body="Create a tour to start adding rooms and hotspots." action={<GlassButton onClick={() => createTour(business.id, { title: `${business.name} Virtual Tour` })}>Create tour</GlassButton>} />
      </DashboardShell>
    );
  }
  return (
    <DashboardShell business={business} title="Tour editor" action={<GlassButton href={`/v/${business.slug}/tour`} icon="eye">Preview as customer</GlassButton>}>
      <TourEditorContent tourId={tour.id} scenes={scenes} theme={theme} defaultSceneId={tour.defaultSceneId} />
    </DashboardShell>
  );
}

function TourEditorContent({ tourId, scenes, theme, defaultSceneId }: { tourId: string; scenes: TourScene[]; theme: Theme; defaultSceneId?: string }) {
  const { pushToast } = useToast();
  const [activeSceneId, setActiveSceneId] = useState(defaultSceneId ?? scenes[0]?.id);
  const activeScene = scenes.find((scene) => scene.id === activeSceneId) ?? scenes[0];
  const hotspots = activeScene ? getHotspotsForScene(activeScene.id) : [];
  const [selected, setSelected] = useState<Hotspot | null>(null);

  function moveScene(scene: TourScene, direction: -1 | 1) {
    const ordered = [...scenes].sort((a, b) => a.order - b.order);
    const index = ordered.findIndex((item) => item.id === scene.id);
    const swap = index + direction;
    if (swap < 0 || swap >= ordered.length) return;
    [ordered[index], ordered[swap]] = [ordered[swap], ordered[index]];
    reorderScenes(tourId, ordered.map((item) => item.id));
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[330px_1fr]">
      <GlassCard className="p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-black">Scenes</h2>
          <GlassButton icon="plus" onClick={() => {
            const scene = createScene(tourId, { name: `Room ${scenes.length + 1}`, imageUrl: makeGradient("studio") });
            setActiveSceneId(scene.id);
          }}>Add</GlassButton>
        </div>
        <div className="mt-4 grid gap-3">
          {scenes.map((scene) => (
            <div
              key={scene.id}
              className={`rounded-3xl p-3 text-left ${scene.id === activeScene?.id ? "bg-slate-950 text-white" : "bg-white/58"}`}
              onClick={() => setActiveSceneId(scene.id)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") setActiveSceneId(scene.id);
              }}
              role="button"
              tabIndex={0}
            >
              <div className="h-24 rounded-2xl bg-cover bg-center" style={mediaBackground(scene.thumbnailUrl ?? scene.imageUrl)} />
              <input className="mt-2 w-full rounded-2xl bg-white/20 px-3 py-2 font-bold" value={scene.name} onChange={(event) => updateScene(scene.id, { name: event.target.value })} onClick={(event) => event.stopPropagation()} />
              <div className="mt-2 flex flex-wrap gap-2">
                <button aria-label="Scene up" className="rounded-full bg-white/20 p-2" onClick={(event) => { event.stopPropagation(); moveScene(scene, -1); }} type="button"><ArrowUp size={15} /></button>
                <button aria-label="Scene down" className="rounded-full bg-white/20 p-2" onClick={(event) => { event.stopPropagation(); moveScene(scene, 1); }} type="button"><ArrowDown size={15} /></button>
                <button className="rounded-full bg-white/20 px-3 text-xs font-bold" onClick={(event) => { event.stopPropagation(); setDefaultScene(tourId, scene.id); }} type="button">{defaultSceneId === scene.id ? "Default" : "Set default"}</button>
                <button aria-label="Delete scene" className="rounded-full bg-red-100 p-2 text-red-700" onClick={(event) => { event.stopPropagation(); deleteScene(scene.id); }} type="button"><Trash2 size={15} /></button>
              </div>
            </div>
          ))}
        </div>
      </GlassCard>

      {activeScene ? (
        <div className="grid gap-5">
          <GlassCard className="p-5">
            <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <h2 className="text-2xl font-black">{activeScene.name}</h2>
                <p className="text-slate-600">Tap to add a hotspot, drag to move it.</p>
              </div>
              <ImageUploader label="Replace scene image" onUploaded={(imageUrl) => updateScene(activeScene.id, { imageUrl, thumbnailUrl: imageUrl })} />
            </div>
            <HotspotEditor
              scene={activeScene}
              hotspots={hotspots}
              theme={theme}
              onAdd={(x, y) => {
                const hotspot = createHotspot(activeScene.id, { label: "New hotspot", type: "info", x, y, icon: "info", description: "Add helpful details." });
                setSelected(hotspot);
              }}
              onMove={(hotspotId, x, y) => moveHotspot(hotspotId, x, y)}
              onSelect={setSelected}
            />
          </GlassCard>
          {selected ? (
            <GlassCard className="grid gap-4 p-5">
              <div className="flex items-center justify-between">
                <h2 className="text-2xl font-black">Edit hotspot</h2>
                <button aria-label="Delete hotspot" className="rounded-full bg-red-100 p-3 text-red-700" onClick={() => { deleteHotspot(selected.id); setSelected(null); }} type="button"><Trash2 size={18} /></button>
              </div>
              <EditableField label="Label" value={selected.label} onChange={(label) => setSelected(updateHotspot(selected.id, { label }))} />
              <label className="grid gap-1.5 text-sm font-semibold text-slate-700">
                Type
                <select className="min-h-12 rounded-2xl bg-white/70 px-4" value={selected.type} onChange={(event) => setSelected(updateHotspot(selected.id, { type: event.target.value as Hotspot["type"] }))}>
                  {["booking", "menu", "info", "image", "video", "contact", "product", "scene", "external"].map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              <EditableField label="Icon" value={selected.icon} onChange={(icon) => setSelected(updateHotspot(selected.id, { icon }))} />
              <EditableField label="Description" textarea value={selected.description ?? ""} onChange={(description) => setSelected(updateHotspot(selected.id, { description }))} />
              {selected.type === "scene" ? (
                <label className="grid gap-1.5 text-sm font-semibold text-slate-700">
                  Target room
                  <select className="min-h-12 rounded-2xl bg-white/70 px-4" value={selected.targetSceneId ?? ""} onChange={(event) => setSelected(updateHotspot(selected.id, { targetSceneId: event.target.value }))}>
                    <option value="">Choose room</option>
                    {scenes.filter((scene) => scene.id !== activeScene.id).map((scene) => <option key={scene.id} value={scene.id}>{scene.name}</option>)}
                  </select>
                </label>
              ) : null}
              {selected.type === "external" ? <EditableField label="Target URL" value={selected.targetUrl ?? ""} onChange={(targetUrl) => setSelected(updateHotspot(selected.id, { targetUrl }))} /> : null}
              <div className="flex gap-2">
                <GlassButton onClick={() => setSelected(toggleHotspot(selected.id))}>{selected.isEnabled ? "Disable" : "Enable"}</GlassButton>
                <GlassButton icon="save" onClick={() => pushToast("Tour saved")} variant="primary">Save</GlassButton>
              </div>
            </GlassCard>
          ) : null}
        </div>
      ) : (
        <EmptyState icon="route" title="No scenes" body="Add your first scene to start building a customer tour." />
      )}
    </div>
  );
}

export function StyleEditorPage() {
  const { business, theme, buttons } = useOwnerData();
  if (!business) return <MissingBusiness />;
  return (
    <DashboardShell business={business} title="Style editor">
      <StyleEditorContent business={business} theme={theme} buttons={buttons} />
    </DashboardShell>
  );
}

function StyleEditorContent({ business, theme, buttons }: { business: Business; theme: Theme; buttons: ShopfrontButton[] }) {
  const { pushToast } = useToast();
  const [accentColor, setAccentColor] = useState(theme.accentColor);
  const [glassIntensity, setGlassIntensity] = useState(theme.glassIntensity);
  const [buttonStyle, setButtonStyle] = useState(theme.buttonStyle);
  const [hotspotStyle, setHotspotStyle] = useState(theme.hotspotStyle);

  function save() {
    updateBusiness(business.id, {
      themeOverrides: { accentColor, glassIntensity, buttonStyle, hotspotStyle },
    });
    pushToast("Style saved");
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[1fr_430px]">
      <GlassCard className="grid gap-5 p-5">
        <h2 className="text-2xl font-black">Theme presets</h2>
        <ThemePicker themes={getThemes()} selectedThemeId={business.themeId} onSelect={(themeId) => { applyTheme(business.id, themeId); pushToast("Theme applied"); }} />
        <h2 className="text-2xl font-black">Fine tune</h2>
        <label className="grid gap-2 font-semibold">Accent color <input type="color" value={accentColor} onChange={(event) => setAccentColor(event.target.value)} className="h-14 w-28 rounded-2xl bg-white/70 p-2" /></label>
        <label className="grid gap-2 font-semibold">Glass intensity <input type="range" min={30} max={100} value={glassIntensity} onChange={(event) => setGlassIntensity(Number(event.target.value))} /></label>
        <EditableField label="Button style" value={buttonStyle} onChange={setButtonStyle} />
        <EditableField label="Hotspot style" value={hotspotStyle} onChange={setHotspotStyle} />
        <GlassButton icon="save" onClick={save} variant="primary">Save changes</GlassButton>
      </GlassCard>
      <div className="xl:sticky xl:top-5 xl:self-start">
        <PhoneShopfrontPreview business={{ ...business, themeOverrides: { accentColor, glassIntensity, buttonStyle, hotspotStyle } }} buttons={buttons} theme={{ ...theme, accentColor, glassIntensity, buttonStyle, hotspotStyle }} />
      </div>
    </div>
  );
}

export function PublishPage() {
  const { business } = useOwnerData();
  if (!business) return <MissingBusiness />;
  return (
    <DashboardShell business={business} title="Publish and share" action={<GlassButton href={`/v/${business.slug}`} icon="eye">View public page</GlassButton>}>
      <PublishContent business={business} />
    </DashboardShell>
  );
}

function PublishContent({ business }: { business: Business }) {
  const { pushToast } = useToast();
  const url = publicVenueUrl(business);
  const embed = `<iframe src="${url}" width="390" height="760" title="${business.name}"></iframe>`;
  function toggle() {
    if (business.status === "published") {
      unpublishBusiness(business.id);
      pushToast("Venue unpublished");
    } else {
      publishBusiness(business.id);
      pushToast("Your venue page is ready");
    }
  }
  return (
    <div className="grid gap-5 lg:grid-cols-[420px_1fr]">
      <QRCodePanel business={business} />
      <GlassCard className="p-5">
        <h2 className="publish-success-title text-3xl font-semibold">Your venue page is ready</h2>
        <p className="url-preview mt-2 break-all rounded-3xl bg-white/58 p-4 text-sm font-medium">{url}</p>
        <div className="mt-5 flex flex-wrap gap-3">
          <GlassButton icon="copy" onClick={async () => { await navigator.clipboard.writeText(url); pushToast("Public URL copied"); }}>Copy link</GlassButton>
          <GlassButton icon="share" onClick={() => { recordEvent({ businessId: business.id, eventType: "share_click", targetLabel: "WhatsApp", source: "publish" }); window.open(`https://wa.me/?text=${encodeURIComponent(url)}`, "_blank", "noopener,noreferrer"); }}>WhatsApp</GlassButton>
          <GlassButton icon="mail" onClick={() => window.location.href = `mailto:?subject=${encodeURIComponent(business.name)}&body=${encodeURIComponent(url)}`}>Email</GlassButton>
          <GlassButton icon="copy" onClick={async () => { await navigator.clipboard.writeText(embed); pushToast("Embed code copied"); }}>Copy embed code</GlassButton>
          <GlassButton icon="sparkles" onClick={() => pushToast("Instagram sharing placeholder ready")}>Instagram</GlassButton>
          <GlassButton icon={business.status === "published" ? "eye" : "sparkles"} onClick={toggle} variant="primary">{business.status === "published" ? "Unpublish" : "Publish"}</GlassButton>
        </div>
      </GlassCard>
    </div>
  );
}

export function AnalyticsPage() {
  const { business, theme, scenes } = useOwnerData();
  if (!business) return <MissingBusiness />;
  const summary = getAnalyticsSummary(business.id);
  const topButtons = getTopButtons(business.id);
  const topHotspots = getTopHotspots(business.id);
  const traffic = getTrafficSources(business.id);
  return (
    <DashboardShell business={business} title="Analytics">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <AnalyticsCard label="Total visits" value={summary.totalVisits} summary={summary} accent={theme.accentColor} />
        <AnalyticsCard label="Tour starts" value={summary.tourStarts} summary={summary} accent="#8b5cf6" />
        <AnalyticsCard label="Booking submissions" value={summary.bookingSubmissions} summary={summary} accent="#10b981" />
        <AnalyticsCard label="Conversion rate" value={`${Math.round(getConversionRate(business.id))}%`} summary={summary} accent="#f59e0b" />
      </div>
      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        <GlassCard className="p-5">
          <h2 className="text-2xl font-black">Clicks</h2>
          {[
            ["Menu opens", summary.menuOpens],
            ["Directions clicks", summary.directionsClicks],
            ["Call clicks", summary.callClicks],
            ["Hotspot clicks", summary.hotspotClicks],
            ["Booking opens", summary.bookingOpens],
          ].map(([label, value]) => (
            <div key={label as string} className="mt-3 flex justify-between rounded-2xl bg-white/58 p-3 font-bold"><span>{label}</span><span>{value as number}</span></div>
          ))}
        </GlassCard>
        <GlassCard className="p-5">
          <h2 className="text-2xl font-black">Top buttons</h2>
          {(topButtons.length ? topButtons : [{ label: "No button clicks yet", clicks: 0 }]).map((item) => (
            <div key={item.label} className="mt-3 flex justify-between rounded-2xl bg-white/58 p-3 font-bold"><span>{item.label}</span><span>{item.clicks}</span></div>
          ))}
        </GlassCard>
        <GlassCard className="p-5">
          <h2 className="text-2xl font-black">Top hotspots</h2>
          {(topHotspots.length ? topHotspots : [{ label: "No hotspot clicks yet", clicks: 0 }]).map((item) => (
            <div key={item.label} className="mt-3 flex justify-between rounded-2xl bg-white/58 p-3 font-bold"><span>{item.label}</span><span>{item.clicks}</span></div>
          ))}
        </GlassCard>
        <GlassCard className="p-5 lg:col-span-2">
          <h2 className="text-2xl font-black">Traffic sources</h2>
          <div className="mt-4">{traffic.length ? <DonutChart data={traffic} /> : <EmptyState icon="bar-chart" title="No analytics yet" body="Customer actions will appear here." />}</div>
        </GlassCard>
        <GlassCard className="p-5">
          <h2 className="text-2xl font-black">Most viewed scene</h2>
          <p className="mt-4 rounded-3xl bg-white/58 p-4 text-xl font-black">{summary.mostViewedScene ?? scenes[0]?.name ?? "No scenes yet"}</p>
          <div className="mt-4"><Sparkline values={summary.sparkline} color={theme.accentColor} /></div>
        </GlassCard>
      </div>
    </DashboardShell>
  );
}

export function BookingsPage() {
  const { business } = useOwnerData();
  if (!business) return <MissingBusiness />;
  const leads = getBookingLeads(business.id);
  return (
    <DashboardShell business={business} title="Bookings and enquiries">
      <GlassCard className="p-5">
        {leads.length === 0 ? (
          <EmptyState icon="calendar" title="No bookings yet" body="Requests from your public page and tour will appear here." />
        ) : (
          <div className="grid gap-3">
            {leads.map((lead) => (
              <div key={lead.id} className="grid gap-3 rounded-3xl bg-white/58 p-4 lg:grid-cols-[1fr_180px_100px]">
                <div>
                  <div className="text-lg font-black">{lead.customerName}</div>
                  <div className="text-sm text-slate-600">{lead.customerEmail} {lead.customerPhone ? `- ${lead.customerPhone}` : ""}</div>
                  <div className="mt-2 text-sm">{lead.requestedDate || "No date"} {lead.requestedTime || ""} {lead.partySize ? `- ${lead.partySize} people` : ""}</div>
                  <p className="mt-2 text-slate-700">{lead.message}</p>
                  <span className="mt-2 inline-flex rounded-full bg-slate-950 px-3 py-1 text-xs font-bold text-white">{lead.source}</span>
                </div>
                <select className="h-12 rounded-2xl bg-white/70 px-3 font-bold" value={lead.status} onChange={(event) => updateBookingLeadStatus(lead.id, event.target.value as BookingLead["status"])}>
                  {["new", "contacted", "confirmed", "cancelled"].map((status) => <option key={status} value={status}>{status}</option>)}
                </select>
                <button aria-label="Delete booking" className="h-12 rounded-2xl bg-red-100 text-red-700" onClick={() => deleteBookingLead(lead.id)} type="button"><Trash2 className="mx-auto" size={18} /></button>
              </div>
            ))}
          </div>
        )}
      </GlassCard>
    </DashboardShell>
  );
}

export function SettingsPage() {
  const { business } = useOwnerData();
  if (!business) return <MissingBusiness />;
  return (
    <DashboardShell business={business} title="Settings">
      <SettingsContent business={business} />
    </DashboardShell>
  );
}

function SettingsContent({ business }: { business: Business }) {
  const { pushToast } = useToast();
  const [draft, setDraft] = useState(business);
  const [slugError, setSlugError] = useState("");

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = slugify(draft.slug);
    const result = validateSlug(normalized, business.id);
    if (!result.valid) {
      setSlugError(result.error ?? "Invalid slug");
      return;
    }
    updateBusiness(business.id, { ...draft, slug: normalized });
    setSlugError("");
    pushToast("Settings saved");
  }

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_360px]">
      <GlassCard className="p-5">
        <form className="grid gap-4" onSubmit={save}>
          <EditableField label="Business name" value={draft.name} onChange={(name) => setDraft({ ...draft, name })} />
          <EditableField label="Public slug" inputClassName="slug-preview" value={draft.slug} onChange={(slug) => setDraft({ ...draft, slug: slugify(slug) })} />
          {slugError ? <p className="rounded-2xl bg-red-100 px-4 py-3 font-semibold text-red-700">{slugError}</p> : null}
          <EditableField label="Description" textarea value={draft.description} onChange={(description) => setDraft({ ...draft, description })} />
          <EditableField label="Address" value={draft.address} onChange={(address) => setDraft({ ...draft, address })} />
          <EditableField label="Phone" value={draft.phone} onChange={(phone) => setDraft({ ...draft, phone })} />
          <EditableField label="Email" value={draft.email} onChange={(email) => setDraft({ ...draft, email })} />
          <EditableField label="Default booking URL" inputClassName="url-preview" value={draft.bookingUrl ?? ""} onChange={(bookingUrl) => setDraft({ ...draft, bookingUrl })} />
          <EditableField label="Menu URL" inputClassName="url-preview" value={draft.menuUrl ?? ""} onChange={(menuUrl) => setDraft({ ...draft, menuUrl })} />
          <GlassButton icon="save" type="submit" variant="primary">Save settings</GlassButton>
        </form>
      </GlassCard>
      <GlassCard className="grid gap-3 p-5 self-start">
        <h2 className="text-2xl font-black">Data controls</h2>
        <GlassButton onClick={() => { resetDemoData(); pushToast("Demo data reset"); }}>Reset demo data</GlassButton>
        <GlassButton onClick={() => { clearAnalytics(business.id); pushToast("Analytics cleared"); }}>Clear analytics</GlassButton>
        <GlassButton onClick={() => { clearBookingLeads(business.id); pushToast("Booking leads cleared"); }}>Clear booking leads</GlassButton>
      </GlassCard>
    </div>
  );
}
