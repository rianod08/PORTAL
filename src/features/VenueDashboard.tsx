"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/cloud-client";
import {
  BUSINESS_TYPES,
  VENUE_BUTTON_TYPES,
  venueBusiness,
  venueTheme,
  type VenueButton,
  type VenueView,
} from "@/lib/venue-contract";
import { slugify } from "@/lib/utils";
import {
  DashboardShell,
  EditableField,
  GlassButton,
  GlassCard,
  PhoneShopfrontPreview,
  QRCodePanel,
  ThemePicker,
} from "@/components/PortalUI";
import { THEMES } from "@/lib/themes";
import { useVenue } from "./VenueProvider";
import CloudTourEditor from "./CloudTourEditor";
import { VenueStorefront } from "./VenueStorefront";

export function VenueTourEditor() {
  const { mainTourId } = useVenue();
  return (
    <CloudTourEditor
      key={mainTourId || "all"}
      initialTourId={mainTourId || undefined}
    />
  );
}
export function VenueDashboard({
  screen,
}: {
  screen:
    | "home"
    | "onboarding"
    | "shopfront"
    | "style"
    | "publish"
    | "settings";
}) {
  const c = useVenue(),
    router = useRouter();
  const [images, setImages] = useState<{
      venueId?: string;
      logoPath?: string;
      coverPath?: string;
      logoUrl?: string;
      coverImageUrl?: string;
    }>({}),
    [uploading, setUploading] = useState(false),
    [uploadError, setUploadError] = useState("");
  const [imageAttempt, setImageAttempt] = useState(0);
  useEffect(() => {
    let alive = true;
    if (c.venue)
      api<VenueView>(`venues/${c.venue.id}/preview`)
        .then((v) => {
          if (alive) {
            setImages({
              venueId: v.id,
              logoPath: v.document.logoAssetPath,
              coverPath: v.document.coverAssetPath,
              logoUrl: v.logoUrl,
              coverImageUrl: v.coverImageUrl,
            });
            setUploadError("");
          }
        })
        .catch((e) => {
          if (alive) setUploadError(e.message);
        });
    return () => {
      alive = false;
    };
  }, [c.venue, imageAttempt]);
  const view: VenueView = {
    id: c.venue?.id ?? "new",
    slug: c.slug,
    document: c.doc,
    revision: c.venue?.revision ?? 0,
    tourSlug:
      c.tours.find((t) => t.id === c.mainTourId && t.published_revision)
        ?.slug ?? null,
    preview: true,
    logoUrl:
      images.venueId === c.venue?.id && images.logoPath === c.doc.logoAssetPath
        ? images.logoUrl
        : undefined,
    coverImageUrl:
      images.venueId === c.venue?.id &&
      images.coverPath === c.doc.coverAssetPath
        ? images.coverImageUrl
        : undefined,
  };
  const business = venueBusiness(view),
    theme = venueTheme(c.doc),
    buttons = c.doc.buttons
      .filter((b) => b.isEnabled)
      .map((b) => ({ ...b, businessId: business.id }));
  const title = {
    home: "Dashboard",
    onboarding: "Create your venue",
    shopfront: "Shopfront",
    style: "Style",
    publish: "Publish & share",
    settings: "Settings",
  }[screen];
  function field(key: keyof typeof c.doc, label: string, textarea = false) {
    return (
      <EditableField
        key={key}
        label={label}
        value={String(c.doc[key] ?? "")}
        textarea={textarea}
        onChange={(value) => c.setDoc({ ...c.doc, [key]: value })}
      />
    );
  }
  async function saveAndNavigate(path: string) {
    const saved = await c.save();
    if (saved) router.push(path);
  }
  async function upload(file: File, key: "logoAssetPath" | "coverAssetPath") {
    setUploadError("");
    if (
      !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
      file.size > 10 * 1024 * 1024 ||
      !file.size
    ) {
      setUploadError("Choose a JPEG, PNG or WebP image up to 10 MB.");
      return;
    }
    setUploading(true);
    try {
      const saved = await c.save();
      if (!saved) return;
      const signed = await api<{ path: string; uploadUrl: string }>(
        `venues/${saved.id}/media`,
        "POST",
        { mimeType: file.type, size: file.size },
      );
      const response = await fetch(signed.uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": file.type },
        body: file,
      });
      if (!response.ok)
        throw new Error(
          "Image upload failed. Your existing image is preserved; try again.",
        );
      c.setDoc({ ...saved.document, [key]: signed.path });
      // Saving the draft makes the uploaded image available to private previews.
      setUploadError("Image uploaded. Save changes to update the preview.");
    } catch (e) {
      setUploadError((e as Error).message);
    } finally {
      setUploading(false);
    }
  }
  function updateButton(id: string, patch: Partial<VenueButton>) {
    c.setDoc({
      ...c.doc,
      buttons: c.doc.buttons.map((b) => (b.id === id ? { ...b, ...patch } : b)),
    });
  }
  function moveButton(id: string, offset: number) {
    const next = [...c.doc.buttons].sort((a, b) => a.order - b.order);
    const i = next.findIndex((b) => b.id === id),
      j = i + offset;
    if (j < 0 || j >= next.length) return;
    [next[i], next[j]] = [next[j], next[i]];
    c.setDoc({ ...c.doc, buttons: next.map((b, order) => ({ ...b, order })) });
  }
  function exportDraft() {
    const url = URL.createObjectURL(
      new Blob(
        [
          JSON.stringify(
            { slug: c.slug, mainTourId: c.mainTourId, document: c.doc },
            null,
            2,
          ),
        ],
        { type: "application/json" },
      ),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "portal-storefront-draft.json";
    a.click();
    URL.revokeObjectURL(url);
  }
  const preview = (
    <PhoneShopfrontPreview
      business={business}
      theme={theme}
      buttons={buttons}
    />
  );
  return (
    <DashboardShell
      production
      business={business}
      title={title}
      action={
        <div className="flex flex-wrap gap-2">
          <GlassButton
            disabled={c.busy || uploading || c.conflict}
            onClick={() => void c.save()}
          >
            {c.busy ? "Saving…" : "Save changes"}
          </GlassButton>
          {c.venue && (
            <GlassButton
              disabled={c.busy || uploading || c.conflict}
              onClick={() => void saveAndNavigate("/app/preview")}
              icon="eye"
            >
              Save & preview
            </GlassButton>
          )}
        </div>
      }
    >
      <GlassCard className="mb-5 flex flex-wrap items-center gap-3 p-4">
        <label className="text-sm font-bold">
          Venue{" "}
          <select
            className="ml-2 rounded-xl bg-white/70 p-2"
            aria-label="Select venue"
            value={c.venue?.id ?? ""}
            disabled={c.busy || uploading}
            onChange={(e) => c.select(e.target.value)}
          >
            <option value="" disabled>
              New venue
            </option>
            {c.venues.map((v) => (
              <option key={v.id} value={v.id}>
                {v.document.name}
              </option>
            ))}
          </select>
        </label>
        <GlassButton
          disabled={c.busy || uploading}
          onClick={c.startNew}
          icon="plus"
        >
          New venue
        </GlassButton>
        <span role="status" className="text-sm text-slate-600">
          {c.busy
            ? c.status
            : c.dirty
              ? "Unsaved changes"
              : c.status || "Saved draft"}
        </span>
      </GlassCard>
      {c.error && (
        <GlassCard className="mb-5 p-4">
          <p role="alert">{c.error}</p>
          <div className="mt-3 flex gap-2">
            <GlassButton onClick={exportDraft}>Export my edits</GlassButton>
            {c.conflict ? (
              <GlassButton onClick={() => void c.reload()}>
                Reload latest draft
              </GlassButton>
            ) : (
              <GlassButton disabled={c.busy} onClick={() => void c.save()}>
                Retry saving
              </GlassButton>
            )}
          </div>
        </GlassCard>
      )}
      {uploadError && (
        <p role="status" className="mb-4">
          {uploadError}
          <button
            className="ml-3 underline"
            onClick={() => setImageAttempt((n) => n + 1)}
          >
            Reload images
          </button>
        </p>
      )}
      <fieldset
        disabled={c.busy || uploading}
        className="min-w-0 disabled:opacity-70"
      >
        {(screen === "onboarding" ||
          screen === "shopfront" ||
          screen === "settings") && (
          <div className="grid gap-6 xl:grid-cols-[1fr_390px]">
            <div className="grid gap-5">
              <GlassCard className="grid gap-4 p-5">
                <h2 className="text-xl font-bold">Venue details</h2>
                {field("name", "Venue name")}
                <label className="grid gap-2 text-sm font-semibold">
                  Business type
                  <select
                    className="rounded-2xl bg-white/70 p-3"
                    value={c.doc.type}
                    onChange={(e) =>
                      c.setDoc({
                        ...c.doc,
                        type: e.target.value as typeof c.doc.type,
                      })
                    }
                  >
                    {BUSINESS_TYPES.map((t) => (
                      <option key={t}>{t}</option>
                    ))}
                  </select>
                </label>
                <EditableField
                  label="Public link"
                  value={c.slug}
                  disabled={!!c.venue?.published_document}
                  onChange={c.setSlug}
                  placeholder="your-venue"
                />
                {!c.venue && (
                  <GlassButton onClick={() => c.setSlug(slugify(c.doc.name))}>
                    Use venue name for link
                  </GlassButton>
                )}
                {field("description", "Description", true)}
                {field("address", "Address")}
                {field("phone", "Phone")}
                {field("email", "Contact email")}
              </GlassCard>
              <GlassCard className="grid gap-4 p-5">
                <h2 className="text-xl font-bold">Your main tour</h2>
                <p className="text-sm text-slate-600">
                  Choose a tour captured or edited using this account. Publish
                  it in the tour editor before visitors can enter.
                </p>
                <select
                  aria-label="Main tour"
                  className="rounded-2xl bg-white/70 p-3"
                  value={c.mainTourId}
                  onChange={(e) => c.setMainTourId(e.target.value)}
                >
                  <option value="">No tour selected</option>
                  {c.tours.map((t) => (
                    <option value={t.id} key={t.id}>
                      {t.title} · {t.published_revision ? "Published" : "Draft"}
                    </option>
                  ))}
                </select>
                <div className="flex gap-2">
                  <GlassButton onClick={() => void c.refreshTours()}>
                    Refresh tours
                  </GlassButton>
                  <GlassButton
                    disabled={c.conflict}
                    onClick={() => void saveAndNavigate("/app/tour")}
                  >
                    Save & edit tour
                  </GlassButton>
                </div>
              </GlassCard>
              <GlassCard className="grid gap-4 p-5">
                <h2 className="text-xl font-bold">Images</h2>
                <p className="text-sm text-slate-600">
                  Save venue details before uploading. JPEG, PNG or WebP, up to
                  10 MB.
                </p>
                {(
                  [
                    ["logoAssetPath", "Logo"],
                    ["coverAssetPath", "Cover image"],
                  ] as const
                ).map(([key, label]) => (
                  <label key={key} className="grid gap-2 text-sm font-semibold">
                    {label}
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      disabled={c.conflict}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) void upload(file, key);
                        e.target.value = "";
                      }}
                    />
                    {c.doc[key] && (
                      <button
                        type="button"
                        className="text-left underline"
                        onClick={() => c.setDoc({ ...c.doc, [key]: undefined })}
                      >
                        Remove {label.toLowerCase()}
                      </button>
                    )}
                  </label>
                ))}
              </GlassCard>
              <GlassCard className="grid gap-4 p-5">
                <h2 className="text-xl font-bold">Links</h2>
                {field("bookingUrl", "Booking link")}
                {field("menuUrl", "Menu link")}
                {field("website", "Website")}
                {field("instagram", "Instagram link")}
              </GlassCard>
              <GlassCard className="grid gap-3 p-5">
                <h2 className="text-xl font-bold">Opening hours</h2>
                {c.doc.openingHours.map((h, i) => (
                  <div
                    className="flex flex-wrap items-center gap-3"
                    key={h.day}
                  >
                    <span className="w-10 font-bold">{h.day}</span>
                    <label className="flex items-center gap-1">
                      <input
                        type="checkbox"
                        checked={h.closed}
                        onChange={(e) =>
                          c.setDoc({
                            ...c.doc,
                            openingHours: c.doc.openingHours.map((row, j) =>
                              j === i
                                ? { ...row, closed: e.target.checked }
                                : row,
                            ),
                          })
                        }
                      />
                      Closed
                    </label>
                    {(["open", "close"] as const).map((k) => (
                      <input
                        key={k}
                        aria-label={`${h.day} ${k}`}
                        type="time"
                        className="rounded-xl bg-white/70 p-2"
                        disabled={h.closed}
                        value={h[k]}
                        onChange={(e) =>
                          c.setDoc({
                            ...c.doc,
                            openingHours: c.doc.openingHours.map((row, j) =>
                              j === i ? { ...row, [k]: e.target.value } : row,
                            ),
                          })
                        }
                      />
                    ))}
                  </div>
                ))}
              </GlassCard>
              <GlassCard className="grid gap-4 p-5">
                <h2 className="text-xl font-bold">Storefront buttons</h2>
                {[...c.doc.buttons]
                  .sort((a, b) => a.order - b.order)
                  .map((b) => (
                    <div
                      key={b.id}
                      className="grid gap-3 rounded-2xl bg-white/40 p-4"
                    >
                      <EditableField
                        label="Button label"
                        value={b.label}
                        onChange={(label) => updateButton(b.id, { label })}
                      />
                      <select
                        aria-label={`${b.label} action`}
                        className="rounded-xl bg-white/70 p-2"
                        value={b.type}
                        onChange={(e) =>
                          updateButton(b.id, {
                            type: e.target.value as VenueButton["type"],
                          })
                        }
                      >
                        {VENUE_BUTTON_TYPES.map((t) => (
                          <option key={t}>{t}</option>
                        ))}
                      </select>
                      {[
                        "booking",
                        "menu",
                        "website",
                        "instagram",
                        "custom",
                      ].includes(b.type) && (
                        <EditableField
                          label="Override link (optional except for custom)"
                          value={b.url ?? ""}
                          onChange={(url) => updateButton(b.id, { url })}
                        />
                      )}
                      <div className="flex flex-wrap gap-3">
                        <label>
                          <input
                            type="checkbox"
                            checked={b.isEnabled}
                            onChange={(e) =>
                              updateButton(b.id, {
                                isEnabled: e.target.checked,
                              })
                            }
                          />{" "}
                          Enabled
                        </label>
                        <label>
                          <input
                            type="checkbox"
                            checked={b.isPrimary}
                            onChange={(e) =>
                              c.setDoc({
                                ...c.doc,
                                buttons: c.doc.buttons.map((row) => ({
                                  ...row,
                                  isPrimary:
                                    row.id === b.id && e.target.checked,
                                })),
                              })
                            }
                          />{" "}
                          Primary
                        </label>
                        <button
                          aria-label={`Move ${b.label} up`}
                          onClick={() => moveButton(b.id, -1)}
                        >
                          ↑
                        </button>
                        <button
                          aria-label={`Move ${b.label} down`}
                          onClick={() => moveButton(b.id, 1)}
                        >
                          ↓
                        </button>
                        <button
                          className="text-red-700"
                          onClick={() =>
                            c.setDoc({
                              ...c.doc,
                              buttons: c.doc.buttons.filter(
                                (row) => row.id !== b.id,
                              ),
                            })
                          }
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  ))}
                <GlassButton
                  disabled={c.doc.buttons.length >= 20}
                  onClick={() =>
                    c.setDoc({
                      ...c.doc,
                      buttons: [
                        ...c.doc.buttons,
                        {
                          id: crypto.randomUUID(),
                          label: "New link",
                          type: "custom",
                          icon: "external-link",
                          order: c.doc.buttons.length,
                          isEnabled: false,
                          isPrimary: false,
                        },
                      ],
                    })
                  }
                >
                  Add button
                </GlassButton>
              </GlassCard>
              <GlassButton
                variant="primary"
                disabled={c.conflict}
                onClick={() =>
                  void saveAndNavigate(
                    screen === "onboarding" ? "/app" : "/app/shopfront",
                  )
                }
              >
                Save venue
              </GlassButton>
            </div>
            <div className="hidden xl:block">{preview}</div>
          </div>
        )}
        {screen === "style" && (
          <div className="grid gap-6 xl:grid-cols-[1fr_390px]">
            <GlassCard className="grid content-start gap-5 p-5">
              <ThemePicker
                themes={THEMES}
                selectedThemeId={c.doc.themeId}
                onSelect={(themeId) => c.setDoc({ ...c.doc, themeId })}
              />
              <label className="flex items-center gap-3 font-bold">
                Accent colour
                <input
                  type="color"
                  value={theme.accentColor}
                  onChange={(e) =>
                    c.setDoc({
                      ...c.doc,
                      themeOverrides: { accentColor: e.target.value },
                    })
                  }
                />
              </label>
              <GlassButton
                onClick={() =>
                  c.setDoc({ ...c.doc, themeOverrides: undefined })
                }
              >
                Use theme accent
              </GlassButton>
            </GlassCard>
            {preview}
          </div>
        )}
        {screen === "home" && (
          <div className="grid gap-5 lg:grid-cols-2">
            <GlassCard className="grid content-start gap-4 p-6">
              <h2 className="text-2xl font-bold">{c.doc.name}</h2>
              <p>
                {c.venue?.published_revision
                  ? `Storefront revision ${c.venue.published_revision} is published.`
                  : "Your storefront is a private draft."}
              </p>
              <p>
                {c.mainTourId
                  ? `Main tour: ${c.tours.find((t) => t.id === c.mainTourId)?.title ?? "Selected tour"}`
                  : "Select a tour to connect your Android captures."}
              </p>
              <GlassButton href="/app/shopfront">Edit shopfront</GlassButton>
              <GlassButton href="/app/tour">Edit tour</GlassButton>
              <GlassButton href="/app/style">Choose your style</GlassButton>
              <GlassButton href="/app/publish" variant="primary">
                Publish & share
              </GlassButton>
            </GlassCard>
            {preview}
          </div>
        )}
        {screen === "publish" && (
          <div className="grid gap-5 lg:grid-cols-2">
            <GlassCard className="grid content-start gap-4 p-6">
              <h2 className="text-2xl font-bold">Publish your storefront</h2>
              <p>
                Saving updates your private draft. Publishing releases the saved
                venue details, images, buttons and selected tour to visitors.
              </p>
              <p>
                Tours are published separately in the Android app or tour
                editor. Your storefront opens the latest published version of
                its selected tour.
              </p>
              {!view.tourSlug && (
                <p>
                  The selected tour is not published yet. The storefront can go
                  live, with its tour entry hidden until the tour is published.
                </p>
              )}
              <GlassButton
                disabled={c.conflict}
                variant="primary"
                onClick={() => void c.publish()}
              >
                Publish storefront
              </GlassButton>
              {c.venue?.published_revision && (
                <>
                  <GlassButton onClick={() => void c.publish(true)}>
                    Unpublish storefront
                  </GlassButton>
                  <Link className="underline" href={`/v/${c.venue.slug}`}>
                    Open public storefront ↗
                  </Link>
                  <p>
                    Public revision {c.venue.published_revision} · saved draft{" "}
                    {c.venue.revision}
                  </p>
                </>
              )}
            </GlassCard>
            {c.venue?.published_revision ? (
              <QRCodePanel business={business} />
            ) : (
              <GlassCard className="p-6">
                Your share link and QR code appear after you publish.
              </GlassCard>
            )}
          </div>
        )}
      </fieldset>
      {screen === "settings" && (
        <GlassCard className="mt-5 p-5">
          <GlassButton
            onClick={() =>
              void api("auth", "POST", { action: "logout" }).then(() =>
                location.assign("/login"),
              )
            }
          >
            Sign out
          </GlassButton>
        </GlassCard>
      )}
    </DashboardShell>
  );
}

export function VenuePreview() {
  const { venue } = useVenue();
  const [view, setView] = useState<VenueView | null>(null),
    [error, setError] = useState(""),
    [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let alive = true;
    if (venue)
      api<VenueView>(`venues/${venue.id}/preview`)
        .then((v) => {
          if (alive) {
            setView(v);
            setError("");
          }
        })
        .catch((e) => {
          if (alive) setError(e.message);
        });
    return () => {
      alive = false;
    };
  }, [venue, attempt]);
  if (!view)
    return (
      <div className="p-10">
        <p role="status">{error || "Opening private preview…"}</p>
        {error && (
          <GlassButton onClick={() => setAttempt((n) => n + 1)}>
            Retry
          </GlassButton>
        )}
        <Link href="/app/shopfront">Back to editor</Link>
      </div>
    );
  return <VenueStorefront venue={view} />;
}
