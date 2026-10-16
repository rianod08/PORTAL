"use client";
import { useState } from "react";
import Link from "next/link";
import {
  GlassButton,
  GlassCard,
  Icon,
  PublicShell,
  VenueHero,
} from "@/components/PortalUI";
import {
  venueBusiness,
  venueButtonHref,
  venueTheme,
  type VenueButton,
  type VenueView,
} from "@/lib/venue-contract";
import { mediaBackground, mutedTextForTheme } from "@/lib/utils";

export function VenueStorefront({ venue }: { venue: VenueView }) {
  const [details, setDetails] = useState(false);
  const theme = venueTheme(venue.document),
    business = venueBusiness(venue),
    dark = theme.mode === "dark";
  const buttons = venue.document.buttons
    .filter((b) => b.isEnabled && venueButtonHref(b, venue))
    .sort((a, b) => a.order - b.order);
  function href(b: VenueButton) {
    return b.type === "tour" && venue.preview
      ? `/v/${encodeURIComponent(venue.tourSlug!)}/tour`
      : venueButtonHref(b, venue)!;
  }
  const tour = buttons.find((b) => b.type === "tour"),
    primary = buttons.find((b) => b.isPrimary) ?? buttons[0];
  function action(b: VenueButton, large = false) {
    const url = href(b);
    return (
      <a
        key={b.id}
        href={url}
        target={b.type === "tour" ? undefined : "_blank"}
        rel={b.type === "tour" ? undefined : "noopener noreferrer"}
        className={`touch-target flex items-center justify-center gap-3 rounded-full px-5 py-4 text-sm font-bold shadow-lg backdrop-blur-xl ${large || b.isPrimary ? "bg-slate-950 text-white" : dark ? "glass-surface-dark text-white" : "glass-surface text-slate-950"}`}
      >
        <Icon name={b.icon} />
        {b.label}
      </a>
    );
  }
  return (
    <PublicShell theme={theme}>
      {venue.preview && (
        <div className="relative z-50 flex items-center justify-center gap-5 bg-slate-950 p-3 text-sm text-white">
          <span>Private draft preview</span>
          <Link className="underline" href="/app/shopfront">
            Back to editor
          </Link>
        </div>
      )}
      {!details ? (
        <main
          className="relative grid min-h-screen place-items-center overflow-hidden px-5 py-10"
          style={{
            ...mediaBackground(business.coverImageUrl),
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        >
          <div className="absolute inset-0 bg-gradient-to-b from-black/18 via-black/34 to-black/64" />
          <div
            className="absolute inset-x-10 top-16 h-40 rounded-full opacity-35 blur-3xl"
            style={{ background: theme.accentColor }}
          />
          <div className="relative z-10 w-full max-w-[390px]">
            <GlassCard
              dark={dark}
              className="p-5 text-center shadow-[0_28px_90px_rgba(15,23,42,.34)]"
            >
              {business.logoUrl && (
                <div
                  role="img"
                  aria-label={`${business.name} logo`}
                  className="mx-auto mb-4 h-20 w-20 rounded-[28px] border border-white/60 bg-white/34 bg-cover bg-center shadow-2xl"
                  style={mediaBackground(business.logoUrl)}
                />
              )}
              <p className="text-xs font-bold uppercase tracking-[0.18em] opacity-70">
                {business.type}
              </p>
              <h1 className="venue-title mt-2 text-4xl font-semibold">
                {business.name}
              </h1>
              <p
                className={`mx-auto mt-3 max-w-xs whitespace-pre-line leading-7 ${mutedTextForTheme(theme)}`}
              >
                {business.description}
              </p>
              <div className="mt-7 grid gap-3">
                {tour
                  ? action(tour, true)
                  : primary
                    ? action(primary, true)
                    : null}
                <GlassButton
                  icon="info"
                  onClick={() => setDetails(true)}
                  variant="ghost"
                >
                  Additional information
                </GlassButton>
              </div>
            </GlassCard>
          </div>
        </main>
      ) : (
        <div className="mx-auto min-h-screen max-w-6xl md:grid md:place-items-center md:px-5 md:py-10">
          <div className="w-full md:max-w-[430px] md:rounded-[44px] md:border md:border-white/50 md:bg-white/20 md:p-2 md:shadow-[0_34px_120px_rgba(15,23,42,.28)]">
            <main
              className={`relative overflow-hidden md:rounded-[36px] ${dark ? "bg-slate-950" : "bg-white/20"}`}
            >
              <VenueHero business={business} theme={theme}>
                <div className="mt-7 grid gap-3">
                  {primary && action(primary, true)}
                  {tour && primary?.id !== tour.id && action(tour)}
                </div>
              </VenueHero>
              <section className="relative z-20 -mt-8 grid gap-4 px-4 pb-28">
                <GlassCard dark={dark} className="grid grid-cols-2 gap-3 p-4">
                  {buttons.map((b) => action(b))}
                </GlassCard>
                <GlassCard dark={dark} className="grid gap-3 p-5">
                  <h2 className="text-xl font-bold">Visit {business.name}</h2>
                  {business.address && <p>{business.address}</p>}
                  {business.phone && (
                    <a href={`tel:${encodeURIComponent(business.phone)}`}>
                      {business.phone}
                    </a>
                  )}
                  {business.email && (
                    <a
                      className="break-all"
                      href={`mailto:${encodeURIComponent(business.email)}`}
                    >
                      {business.email}
                    </a>
                  )}
                  <h3 className="mt-2 font-bold">Opening hours</h3>
                  <dl className="grid gap-2">
                    {business.openingHours.map((h) => (
                      <div key={h.day} className="flex justify-between gap-4">
                        <dt>{h.day}</dt>
                        <dd>
                          {h.closed ? "Closed" : `${h.open} – ${h.close}`}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </GlassCard>
                <GlassButton onClick={() => setDetails(false)} variant="ghost">
                  Back to welcome
                </GlassButton>
              </section>
              <nav
                aria-label="Venue actions"
                className="safe-bottom fixed inset-x-0 bottom-0 z-40 mx-auto max-w-[430px] px-3 pt-3 md:absolute"
              >
                <div
                  className={`flex items-center justify-around gap-2 rounded-[28px] p-2 ${dark ? "glass-surface-dark" : "glass-surface"}`}
                >
                  {buttons
                    .filter((b) => ["booking", "tour", "menu"].includes(b.type))
                    .slice(0, 3)
                    .map((b) => (
                      <a
                        key={b.id}
                        className="flex min-h-14 flex-col items-center justify-center gap-1 px-2 text-xs font-bold"
                        href={href(b)}
                      >
                        <Icon name={b.icon} />
                        {b.label}
                      </a>
                    ))}
                  <button
                    className="min-h-14 px-2 text-xs font-bold"
                    onClick={() =>
                      void (async () => {
                        const url = location.origin + `/v/${venue.slug}`;
                        if (navigator.share)
                          await navigator.share({ title: business.name, url });
                        else await navigator.clipboard.writeText(url);
                      })().catch(() => undefined)
                    }
                  >
                    Share
                  </button>
                </div>
              </nav>
            </main>
          </div>
        </div>
      )}
    </PublicShell>
  );
}
