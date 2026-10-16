"use client";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import { api, CloudError } from "@/lib/cloud-client";
import type { CloudTour } from "@/lib/tour-contract";
import {
  emptyVenue,
  validateVenue,
  type Venue,
  type VenueDocument,
} from "@/lib/venue-contract";
import {
  GlassButton,
  GlassCard,
  LiquidBackground,
} from "@/components/PortalUI";

type VenueContextValue = {
  venues: Venue[];
  venue: Venue | null;
  tours: CloudTour[];
  doc: VenueDocument;
  slug: string;
  mainTourId: string;
  setDoc: (doc: VenueDocument) => void;
  setSlug: (slug: string) => void;
  setMainTourId: (id: string) => void;
  select: (id: string) => void;
  save: () => Promise<Venue | null>;
  publish: (unpublish?: boolean) => Promise<void>;
  reload: () => Promise<void>;
  refreshTours: () => Promise<void>;
  startNew: () => void;
  dirty: boolean;
  busy: boolean;
  error: string;
  status: string;
  conflict: boolean;
};
const VenueContext = createContext<VenueContextValue | null>(null);
export function useVenue() {
  const value = useContext(VenueContext);
  if (!value) throw new Error("VenueProvider is required.");
  return value;
}
export function VenueProvider({ children }: { children: ReactNode }) {
  const router = useRouter(),
    pathname = usePathname();
  const [venues, setVenues] = useState<Venue[]>([]),
    [tours, setTours] = useState<CloudTour[]>([]),
    [venue, setVenue] = useState<Venue | null>(null);
  const [doc, setDoc] = useState(emptyVenue),
    [slug, setSlug] = useState(""),
    [mainTourId, setMainTourId] = useState("");
  const [ready, setReady] = useState(false),
    [loadError, setLoadError] = useState(""),
    [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [status, setStatus] = useState(""),
    [conflict, setConflict] = useState(false);
  const working = useRef(false);
  const dirty = venue
    ? JSON.stringify(doc) !== JSON.stringify(venue.document) ||
      slug !== venue.slug ||
      mainTourId !== (venue.main_tour_id ?? "")
    : !!doc.name || !!slug;
  const open = useCallback((v: Venue | null) => {
    setVenue(v);
    setDoc(v?.document ?? emptyVenue());
    setSlug(v?.slug ?? "");
    setMainTourId(v?.main_tour_id ?? "");
    setError("");
    setStatus("");
    setConflict(false);
    if (v) localStorage.setItem("portal:active-venue", v.id);
  }, []);
  useEffect(() => {
    let alive = true;
    async function load() {
      try {
        await api("auth");
        const [rows, accountTours] = await Promise.all([
          api<Venue[]>("venues"),
          api<CloudTour[]>("tours"),
        ]);
        if (!alive) return;
        setVenues(rows);
        setTours(accountTours);
        open(
          location.pathname === "/app/onboarding"
            ? null
            : (rows.find(
                (v) => v.id === localStorage.getItem("portal:active-venue"),
              ) ??
                rows[0] ??
                null),
        );
        setReady(true);
        setLoadError("");
      } catch (e) {
        if (!alive) return;
        if (e instanceof CloudError && e.status === 401)
          router.replace("/login");
        else setLoadError((e as Error).message);
      }
    }
    void load();
    return () => {
      alive = false;
    };
  }, [attempt, open, router]);
  useEffect(() => {
    if (
      ready &&
      !venue &&
      [
        "/app",
        "/app/shopfront",
        "/app/style",
        "/app/publish",
        "/app/settings",
        "/app/preview",
      ].includes(pathname)
    )
      router.replace("/app/onboarding");
  }, [ready, venue, pathname, router]);
  useEffect(() => {
    const stop = (e: BeforeUnloadEvent) => {
      if (dirty) e.preventDefault();
    };
    window.addEventListener("beforeunload", stop);
    return () => window.removeEventListener("beforeunload", stop);
  }, [dirty]);
  function remember(v: Venue) {
    setVenues((rows) => [v, ...rows.filter((row) => row.id !== v.id)]);
    open(v);
  }
  async function save(): Promise<Venue | null> {
    if (working.current || conflict) return null;
    if (venue && !dirty) return venue;
    const errors = validateVenue(doc);
    if (errors.length) {
      setError(errors.join(" "));
      return null;
    }
    working.current = true;
    setBusy(true);
    setError("");
    setStatus("Saving…");
    try {
      const next = await api<Venue>(
        venue ? `venues/${venue.id}` : "venues",
        venue ? "PUT" : "POST",
        {
          revision: venue?.revision ?? 0,
          document: doc,
          slug,
          mainTourId: mainTourId || null,
        },
      );
      remember(next);
      setStatus("All changes saved");
      return next;
    } catch (e) {
      setError((e as Error).message);
      setStatus("Changes not saved");
      if (
        e instanceof CloudError &&
        e.status === 409 &&
        /changed on another device/i.test(e.message)
      )
        setConflict(true);
      return null;
    } finally {
      working.current = false;
      setBusy(false);
    }
  }
  async function publish(unpublish = false) {
    if (working.current) return;
    const saved = unpublish ? venue : await save();
    if (!saved) return;
    working.current = true;
    setBusy(true);
    setError("");
    try {
      const next = await api<Venue>(`venues/${saved.id}/publish`, "POST", {
        revision: saved.revision,
        action: unpublish ? "unpublish" : "publish",
      });
      if (unpublish) {
        setVenue(next);
        setVenues((rows) =>
          rows.map((row) => (row.id === next.id ? next : row)),
        );
      } else remember(next);
      setStatus(unpublish ? "Storefront unpublished" : "Storefront published");
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof CloudError && e.status === 409) setConflict(true);
    } finally {
      working.current = false;
      setBusy(false);
    }
  }
  async function reload() {
    if (!venue || working.current) return;
    if (
      dirty &&
      !window.confirm(
        "Discard your unsaved changes and load the latest saved draft? Export them first if you need a copy.",
      )
    )
      return;
    setBusy(true);
    try {
      remember(await api<Venue>(`venues/${venue.id}`));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function refreshTours() {
    try {
      setTours(await api<CloudTour[]>("tours"));
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function select(id: string) {
    if (
      busy ||
      (dirty &&
        !window.confirm(
          "Discard unsaved storefront changes and switch venues?",
        ))
    )
      return;
    open(venues.find((v) => v.id === id) ?? null);
  }
  function startNew() {
    if (
      busy ||
      (dirty &&
        !window.confirm(
          "Discard unsaved storefront changes and create another venue?",
        ))
    )
      return;
    open(null);
    router.push("/app/onboarding");
  }
  if (!ready)
    return (
      <LiquidBackground>
        <GlassCard className="mx-auto mt-20 max-w-lg p-8">
          <p role={loadError ? "alert" : "status"}>
            {loadError || "Opening your account…"}
          </p>
          {loadError && (
            <GlassButton onClick={() => setAttempt((a) => a + 1)}>
              Retry
            </GlassButton>
          )}
        </GlassCard>
      </LiquidBackground>
    );
  return (
    <VenueContext.Provider
      value={{
        venues,
        venue,
        tours,
        doc,
        slug,
        mainTourId,
        setDoc,
        setSlug,
        setMainTourId,
        select,
        save,
        publish,
        reload,
        refreshTours,
        startNew,
        dirty,
        busy,
        error,
        status,
        conflict,
      }}
    >
      {children}
    </VenueContext.Provider>
  );
}
