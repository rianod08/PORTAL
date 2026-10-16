"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  ArrowRight,
  BadgeCheck,
  Calendar,
  Menu as MenuIcon,
  MoreHorizontal,
  Star,
} from "lucide-react";
import {
  BookingModal,
  BottomNav,
  ContactDrawer,
  EmptyState,
  GlassButton,
  GlassCard,
  Icon,
  LiquidBackground,
  MenuBottomSheet,
  MoreDrawer,
  PhoneShopfrontPreview,
  PublicShell,
  ShopfrontButtonGrid,
  ToastProvider,
  TourViewer,
  useToast,
  VenueHero,
} from "@/components/PortalUI";
import {
  getBusinessBySlug,
  getBusinesses,
  getButtonsForBusiness,
  getCurrentTheme,
  getScenesForTour,
  getTourForBusiness,
  openBookingModal,
  recordEvent,
} from "@/lib/data";
import {
  handleBookNow,
  handleCall,
  handleCustomButton,
  handleDirections,
  handleEmail,
  handleEnterTour,
  handleInstagram,
  handleOpenMenu,
  handleShareVenue,
  handleWebsite,
} from "@/lib/publicActions";
import type { Business, ShopfrontButton } from "@/lib/types";
import {
  cn,
  mapUrl,
  mediaBackground,
  mutedTextForTheme,
  readableTextForTheme,
} from "@/lib/utils";
import { usePortalData } from "@/hooks/usePortalData";

function usePublicBusiness() {
  usePortalData();
  const params = useParams<{ slug: string }>();
  const slug = typeof params?.slug === "string" ? params.slug : "";
  const business = slug ? getBusinessBySlug(slug) : null;
  const theme = business ? getCurrentTheme(business.id) : null;
  const buttons = business ? getButtonsForBusiness(business.id) : [];
  const tour = business ? getTourForBusiness(business.id) : null;
  const scenes = tour ? getScenesForTour(tour.id) : [];
  return { business, theme, buttons, tour, scenes };
}

export function MarketingPage() {
  usePortalData();
  const bloom = getBusinessBySlug("bloom-cafe") ?? getBusinesses()[0];
  const theme = getCurrentTheme(bloom.id);
  const buttons = getButtonsForBusiness(bloom.id);

  const how = [
    ["Scan your venue", "Upload venue photos or 360 room images."],
    ["Customize your shopfront", "Add your real description, cover image, and brand links."],
    ["Add booking buttons", "Let customers book, call, view menus, and get directions."],
    ["Publish your tour link", "Share one mobile venue link or QR code anywhere."],
  ];
  const features = [
    "Mobile-first venue page",
    "360° tour builder",
    "Interactive hotspots",
    "Booking and enquiry buttons",
    "Menus and offers",
    "QR codes",
    "Analytics",
    "Themes and styles",
  ];
  const useCases = [
    "Restaurants & cafes",
    "Gyms & fitness studios",
    "Salons & clinics",
    "Event venues",
    "Showrooms",
    "Creative studios",
  ];

  return (
    <ToastProvider>
      <LiquidBackground>
        <header className="mx-auto flex max-w-7xl items-center justify-between px-5 py-5">
          <Link className="flex items-center gap-2 text-lg font-black" href="/">
            <span className="grid h-10 w-10 place-items-center rounded-2xl bg-slate-950 text-white">
              <Icon name="sparkles" />
            </span>
            Portal
          </Link>
          <nav className="hidden items-center gap-3 md:flex">
            <GlassButton href="/demo" variant="ghost">Demo</GlassButton>
            <GlassButton href="/login" variant="primary">Login</GlassButton>
          </nav>
        </header>

        <section className="mx-auto grid max-w-7xl items-center gap-10 px-5 pb-16 pt-8 lg:grid-cols-[1.02fr_.98fr] lg:pb-24">
          <div>
            <div className="mb-5 inline-flex rounded-full border border-white/60 bg-white/46 px-4 py-2 text-sm font-bold text-slate-700 backdrop-blur-xl">
              Scan, customize, publish
            </div>
            <h1 className="hero-title max-w-3xl text-5xl font-semibold text-slate-950 sm:text-7xl">
              Turn your venue into an interactive 360° tour.
            </h1>
            <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-700">
              Let customers explore your space, view facilities, and book instantly - all from one mobile-friendly link.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <GlassButton href="/app/onboarding" icon="sparkles" variant="primary">Start building</GlassButton>
              <GlassButton href="/demo/v/bloom-cafe" icon="eye">View demo venue</GlassButton>
            </div>
          </div>
          <div className="relative">
            <PhoneShopfrontPreview business={bloom} buttons={buttons} theme={theme} />
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 py-10">
          <div className="grid gap-4 md:grid-cols-4">
            {how.map(([title, body], index) => (
              <GlassCard key={title} className="p-5">
                <div className="mb-4 grid h-11 w-11 place-items-center rounded-2xl bg-slate-950 text-white">{index + 1}</div>
                <h2 className="text-xl font-bold">{title}</h2>
                <p className="mt-2 leading-7 text-slate-600">{body}</p>
              </GlassCard>
            ))}
          </div>
        </section>

        <section className="mx-auto grid max-w-7xl gap-5 px-5 py-10 lg:grid-cols-[.85fr_1.15fr]">
          <div>
            <p className="text-sm font-bold uppercase tracking-[0.18em] text-slate-500">Product preview</p>
            <h2 className="marketing-display mt-2 text-4xl font-semibold">Everything a venue needs in one mobile link.</h2>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {[
              ["Shopfront", "A public venue page with booking, menu, directions, and offers.", "layers"],
              ["360° tour", "Swipe-friendly room viewer with scene navigation.", "route"],
              ["Booking hotspot", "Customers can enquire from inside any room.", "calendar"],
              ["Analytics", "Track views, tour starts, clicks, and conversions.", "bar-chart"],
            ].map(([title, body, icon]) => (
              <GlassCard key={title} className="p-5">
                <Icon name={icon} size={24} />
                <h3 className="mt-4 text-xl font-bold">{title}</h3>
                <p className="mt-2 leading-7 text-slate-600">{body}</p>
              </GlassCard>
            ))}
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 py-10">
          <div className="grid gap-5 lg:grid-cols-2">
            <GlassCard className="p-6">
              <h2 className="marketing-display text-3xl font-semibold">Built for real businesses</h2>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                {useCases.map((item) => (
                  <div key={item} className="flex items-center gap-3 rounded-2xl bg-white/52 p-3 font-bold">
                    <BadgeCheck size={18} />
                    {item}
                  </div>
                ))}
              </div>
            </GlassCard>
            <GlassCard className="p-6">
              <h2 className="marketing-display text-3xl font-semibold">Features</h2>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                {features.map((item) => (
                  <div key={item} className="flex items-center gap-3 rounded-2xl bg-white/52 p-3 font-bold">
                    <Icon name="check" />
                    {item}
                  </div>
                ))}
              </div>
            </GlassCard>
          </div>
        </section>

        <section className="mx-auto max-w-7xl px-5 py-10">
          <div className="mb-6">
            <p className="text-sm font-bold uppercase tracking-[0.18em] text-slate-500">Pricing preview</p>
            <h2 className="marketing-display mt-2 text-4xl font-semibold">Start simple, grow when you need more.</h2>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            {[
              ["Starter", ["1 venue", "Shopfront page", "360 tour", "Booking buttons"]],
              ["Pro", ["Advanced styling", "Analytics", "Custom domain", "More hotspots"]],
              ["Business", ["Multiple venues", "Team access", "White-label", "Priority support"]],
            ].map(([plan, items]) => (
              <GlassCard key={plan as string} className={cn("p-6", plan === "Pro" && "bg-slate-950/88 text-white")}>
                <h3 className="text-2xl font-black">{plan as string}</h3>
                <div className="mt-5 grid gap-3">
                  {(items as string[]).map((item) => (
                    <div key={item} className="flex items-center gap-2">
                      <Icon name="check" />
                      <span className="font-semibold">{item}</span>
                    </div>
                  ))}
                </div>
              </GlassCard>
            ))}
          </div>
        </section>

        <section className="mx-auto max-w-5xl px-5 py-16 text-center">
          <GlassCard className="p-8 sm:p-12">
            <h2 className="marketing-display text-4xl font-semibold">Create your interactive venue page today.</h2>
            <p className="mx-auto mt-4 max-w-2xl text-lg leading-8 text-slate-600">
              Turn your real venue into an interactive 360° tour with bookings, menus, and shareable links.
            </p>
            <div className="mt-7 flex justify-center">
              <GlassButton href="/app/onboarding" icon="sparkles" variant="primary">Start building</GlassButton>
            </div>
          </GlassCard>
        </section>
        <BottomNav />
      </LiquidBackground>
    </ToastProvider>
  );
}

export function DemoGalleryPage() {
  usePortalData();
  const businesses = getBusinesses();
  return (
    <ToastProvider>
      <LiquidBackground className="pb-24">
        <header className="mx-auto flex max-w-7xl items-center justify-between px-5 py-5">
          <Link className="font-black" href="/">Portal</Link>
          <GlassButton href="/app/onboarding" icon="sparkles" variant="primary">Build yours</GlassButton>
        </header>
        <section className="mx-auto max-w-7xl px-5 py-10">
          <p className="text-sm font-bold uppercase tracking-[0.18em] text-slate-500">Demo gallery</p>
          <h1 className="marketing-display mt-2 text-5xl font-semibold">Explore sample venue pages.</h1>
          <div className="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
            {businesses.map((business) => {
              const theme = getCurrentTheme(business.id);
              return (
                <GlassCard key={business.id} className="overflow-hidden p-3">
                  <div className="h-64 rounded-[26px] bg-cover bg-center" style={mediaBackground(business.coverImageUrl)} />
                  <div className="p-3">
                    <p className="text-sm font-bold text-slate-500">{business.type}</p>
                    <h2 className="venue-title mt-1 text-2xl font-semibold">{business.name}</h2>
                    <p className="mt-2 line-clamp-3 leading-7 text-slate-600">{business.description}</p>
                    <div className="mt-5 grid grid-cols-2 gap-2">
                      <GlassButton href={`/demo/v/${business.slug}`} icon="eye" variant="primary">Page</GlassButton>
                      <GlassButton href={`/demo/v/${business.slug}/tour`} icon="route">Tour</GlassButton>
                    </div>
                    <div className="mt-3 h-2 rounded-full" style={{ background: theme.accentColor }} />
                  </div>
                </GlassCard>
              );
            })}
          </div>
        </section>
        <BottomNav />
      </LiquidBackground>
    </ToastProvider>
  );
}

function OffersModal({
  business,
  open,
  onClose,
}: {
  business: Business;
  open: boolean;
  onClose: () => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end bg-slate-950/30 p-3 backdrop-blur-sm md:items-center md:justify-center">
      <div className="glass-surface w-full max-w-md rounded-[32px] p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-2xl font-black">Events and offers</h2>
          <button aria-label="Close offers" className="rounded-full bg-white/60 p-3" onClick={onClose} type="button">
            <MoreHorizontal size={18} />
          </button>
        </div>
        <div className="grid gap-3">
          {[
            "Private viewing slots this week",
            "Weekend package available",
            `${business.type} seasonal offer`,
          ].map((offer) => (
            <div key={offer} className="rounded-3xl bg-white/58 p-4">
              <div className="font-bold">{offer}</div>
              <div className="mt-1 text-sm text-slate-600">Tap Book Now to enquire.</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export function PublicVenuePage() {
  const router = useRouter();
  const { pushToast } = useToast();
  const { business, theme, buttons } = usePublicBusiness();
  const [menuOpen, setMenuOpen] = useState(false);
  const [contactOpen, setContactOpen] = useState(false);
  const [offersOpen, setOffersOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const businessId = business?.id;

  useEffect(() => {
    if (businessId) {
      recordEvent({ businessId, eventType: "page_view", source: document.referrer ? "referral" : "direct" });
    }
  }, [businessId]);

  if (!business || !theme) {
    return (
      <PublicShell theme={{ id: "opal-glass", name: "Opal", description: "", mode: "light", accentColor: "#49bdf2", backgroundStyle: "linear-gradient(#eef7fb,#fff)", buttonStyle: "", hotspotStyle: "", glassIntensity: 80 }}>
        <div className="grid min-h-screen place-items-center p-6">
          <EmptyState icon="building" title="Venue not found" body="This public link does not match a published venue." action={<GlassButton href="/demo">View demos</GlassButton>} />
        </div>
      </PublicShell>
    );
  }

  const primary = buttons.find((button) => button.isPrimary && button.isEnabled) ?? buttons.find((button) => button.isEnabled);
  const secondary = buttons.find((button) => button.type === "tour" && button.isEnabled);
  const context = {
    business,
    source: "shopfront",
    onMenu: () => setMenuOpen(true),
    onOffers: () => setOffersOpen(true),
    onMore: () => setMoreOpen(true),
    navigate: (url: string) => router.push(url.replace(/^\/v\//, "/demo/v/")),
    toast: pushToast,
  };

  function runButton(button: ShopfrontButton) {
    recordEvent({
      businessId: context.business.id,
      eventType: "button_click",
      targetId: button.id,
      targetLabel: button.label,
      source: "shopfront",
    });
    if (button.type === "booking") handleBookNow({ ...context, targetId: button.id });
    else if (button.type === "tour") handleEnterTour({ ...context, targetId: button.id });
    else if (button.type === "menu") handleOpenMenu({ ...context, targetId: button.id });
    else if (button.type === "directions") handleDirections({ ...context, targetId: button.id });
    else if (button.type === "call") handleCall({ ...context, targetId: button.id });
    else if (button.type === "email") handleEmail({ ...context, targetId: button.id });
    else if (button.type === "website") handleWebsite({ ...context, targetId: button.id });
    else if (button.type === "instagram") handleInstagram({ ...context, targetId: button.id });
    else if (button.type === "events" || button.type === "offers") setOffersOpen(true);
    else handleCustomButton(button, { ...context, targetId: button.id });
  }

  const stickyActions = [
    { label: "Book", Component: Calendar, action: () => (primary ? runButton(primary) : handleBookNow(context)) },
    { label: "Tour", Component: ArrowRight, action: () => router.push(`/demo/v/${business.slug}/tour`) },
    { label: "Menu", Component: MenuIcon, action: () => setMenuOpen(true) },
    { label: "More", Component: MoreHorizontal, action: () => setMoreOpen(true) },
  ];

  function enterTourFromEntry() {
    recordEvent({
      businessId: context.business.id,
      eventType: "button_click",
      targetId: secondary?.id,
      targetLabel: "Enter 360° tour",
      source: "entry",
    });
    handleEnterTour({ ...context, targetId: secondary?.id, source: "entry" });
  }

  if (!showDetails) {
    return (
      <PublicShell theme={theme}>
        <main
          className="relative grid min-h-screen place-items-center overflow-hidden px-5 py-10"
          style={{ ...mediaBackground(business.coverImageUrl), backgroundSize: "cover", backgroundPosition: "center" }}
        >
          <div className="absolute inset-0 bg-gradient-to-b from-black/18 via-black/34 to-black/64" />
          <div className="absolute inset-x-10 top-16 h-40 rounded-full opacity-35 blur-3xl" style={{ background: theme.accentColor }} />
          <div className="relative z-10 w-full max-w-[390px]">
            <GlassCard dark={theme.mode === "dark"} className="p-5 text-center shadow-[0_28px_90px_rgba(15,23,42,.34)]">
              <div className="mx-auto mb-4 flex h-20 w-20 items-center justify-center overflow-hidden rounded-[28px] border border-white/60 bg-white/34 shadow-2xl backdrop-blur-2xl">
                <div className="h-full w-full" style={{ ...mediaBackground(business.logoUrl), backgroundSize: "cover", backgroundPosition: "center" }} />
              </div>
              <p className="text-xs font-bold uppercase tracking-[0.18em] opacity-70">{business.type}</p>
              <h1 className="venue-title mt-2 text-4xl font-semibold">{business.name}</h1>
              <p className={cn("mx-auto mt-3 max-w-xs leading-7", mutedTextForTheme(theme))}>{business.description}</p>
              <div className="mt-7 grid gap-3">
                <GlassButton icon="route" onClick={enterTourFromEntry} variant="primary">
                  Enter 360° tour
                </GlassButton>
                <GlassButton icon="info" onClick={() => setShowDetails(true)} variant="ghost">
                  Additional information
                </GlassButton>
              </div>
            </GlassCard>
          </div>
        </main>
      </PublicShell>
    );
  }

  return (
    <PublicShell theme={theme}>
      <div className="mx-auto min-h-screen max-w-6xl md:grid md:place-items-center md:px-5 md:py-10">
        <div className="w-full md:max-w-[430px] md:rounded-[44px] md:border md:border-white/50 md:bg-white/20 md:p-2 md:shadow-[0_34px_120px_rgba(15,23,42,.28)]">
          <div className={cn("relative overflow-hidden md:rounded-[36px]", theme.mode === "dark" ? "bg-slate-950" : "bg-white/20")}>
            <VenueHero business={business} theme={theme}>
              <div className="mt-7 flex gap-3">
                {primary ? (
                  <GlassButton onClick={() => runButton(primary)} icon={primary.icon} variant="primary">
                    {primary.label}
                  </GlassButton>
                ) : null}
                {secondary ? (
                  <GlassButton onClick={() => runButton(secondary)} icon="route" variant="ghost">
                    360° Tour
                  </GlassButton>
                ) : null}
              </div>
            </VenueHero>
            <section className="relative z-20 -mt-16 px-4 pb-28">
              <GlassCard dark={theme.mode === "dark"} className="mb-4 p-4">
                <div className="grid grid-cols-3 gap-2 text-center">
                  {[
                    ["4.9", "Reviews"],
                    ["Open", "Today"],
                    ["2 min", "Explore"],
                  ].map(([value, label]) => (
                    <div key={label} className="rounded-2xl bg-white/16 p-3">
                      <div className="text-xl font-black">{value}</div>
                      <div className="text-xs opacity-70">{label}</div>
                    </div>
                  ))}
                </div>
              </GlassCard>
              <ShopfrontButtonGrid buttons={buttons.slice(0, 6)} dark={theme.mode === "dark"} onButton={runButton} />
              <GlassCard dark={theme.mode === "dark"} className="mt-4 p-4">
                <div className="flex gap-1 text-amber-300">
                  {Array.from({ length: 5 }, (_, index) => <Star key={index} fill="currentColor" size={18} />)}
                </div>
                <p className={cn("mt-3 leading-7", mutedTextForTheme(theme))}>
                  The tour made it easy to choose the right space before booking.
                </p>
              </GlassCard>
            </section>
            <div className="safe-bottom fixed bottom-0 left-0 right-0 z-40 mx-auto max-w-[430px] px-3 pt-3 md:absolute">
              <div className={cn("grid grid-cols-4 gap-2 rounded-[28px] p-2", theme.mode === "dark" ? "glass-surface-dark" : "glass-surface")}>
                {stickyActions.map(({ label, Component, action }) => (
                  <button
                    key={label}
                    aria-label={label}
                    className={cn("flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl text-[11px] font-bold", readableTextForTheme(theme))}
                    onClick={action}
                    type="button"
                  >
                    <Component size={18} />
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
      <BookingModal business={business} />
      <MenuBottomSheet business={business} open={menuOpen} onClose={() => setMenuOpen(false)} />
      <ContactDrawer business={business} open={contactOpen} onClose={() => setContactOpen(false)} />
      <OffersModal business={business} open={offersOpen} onClose={() => setOffersOpen(false)} />
      <MoreDrawer business={business} buttons={buttons} open={moreOpen} onClose={() => setMoreOpen(false)} onButton={runButton} />
      <button
        aria-label="Share venue"
        className="fixed right-4 top-4 z-40 grid h-12 w-12 place-items-center rounded-full bg-white/34 text-white shadow-xl backdrop-blur-2xl"
        onClick={() => void handleShareVenue(context)}
        type="button"
      >
        <Icon name="share" />
      </button>
      <a className="sr-only" href={mapUrl(business.address)} target="_blank" rel="noreferrer">Directions</a>
    </PublicShell>
  );
}

export function PublicTourPage() {
  const router = useRouter();
  const { business, theme, tour, scenes } = usePublicBusiness();
  const [menuOpen, setMenuOpen] = useState(false);
  const [contactOpen, setContactOpen] = useState(false);
  const businessId = business?.id;

  useEffect(() => {
    if (businessId) {
      recordEvent({ businessId, eventType: "tour_start", targetLabel: "Direct tour view", source: "tour" });
    }
  }, [businessId]);

  if (!business || !theme) {
    return (
      <LiquidBackground>
        <div className="grid min-h-screen place-items-center p-6">
          <EmptyState icon="route" title="Tour not found" body="This venue tour is not available." action={<GlassButton href="/demo">View demos</GlassButton>} />
        </div>
      </LiquidBackground>
    );
  }

  if (!tour || scenes.length === 0) {
    return (
      <PublicShell theme={theme}>
        <div className="grid min-h-screen place-items-center p-6">
          <EmptyState icon="route" title="No tour scenes yet" body="The business owner has not added rooms to this tour." action={<GlassButton href={`/demo/v/${business.slug}`}>Back to shopfront</GlassButton>} />
        </div>
      </PublicShell>
    );
  }

  return (
    <PublicShell theme={theme}>
      <TourViewer
        business={business}
        tour={tour}
        scenes={scenes}
        theme={theme}
        onBack={() => router.push(`/demo/v/${business.slug}`)}
        onBook={(source, targetId) => openBookingModal(source, targetId)}
        onMenu={() => setMenuOpen(true)}
        onContact={() => setContactOpen(true)}
      />
      <BookingModal business={business} defaultSource="tour" />
      <MenuBottomSheet business={business} open={menuOpen} onClose={() => setMenuOpen(false)} />
      <ContactDrawer business={business} open={contactOpen} onClose={() => setContactOpen(false)} />
    </PublicShell>
  );
}
