"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { QRCodeCanvas } from "qrcode.react";
import {
  ArrowLeft,
  BadgePercent,
  BarChart3,
  BookOpen,
  Building2,
  Calendar,
  Camera,
  Check,
  ChevronRight,
  CircleDollarSign,
  Clipboard,
  Clock,
  Copy,
  DoorOpen,
  Download,
  Eye,
  Globe,
  Home,
  ImagePlus,
  Info,
  Layers3,
  LayoutDashboard,
  Link as LinkIcon,
  List,
  Mail,
  Map,
  MapPin,
  Menu,
  MoreHorizontal,
  MousePointer2,
  Palette,
  Phone,
  QrCode,
  RefreshCw,
  Route,
  Save,
  Settings,
  Share2,
  ShoppingBag,
  Sparkles,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import {
  type ComponentType,
  type CSSProperties,
  type FormEvent,
  type InputHTMLAttributes,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  type SVGProps,
  type TextareaHTMLAttributes,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  getHotspotsForScene,
  recordEvent,
  submitBookingLead,
} from "@/lib/data";
import { uploadLogo, uploadVenueImage, validateImage } from "@/lib/media";
import {
  handleCall,
  handleDirections,
  handleOpenMenu,
} from "@/lib/publicActions";
import type {
  AnalyticsSummary,
  BookingLead,
  Business,
  Hotspot,
  ShopfrontButton,
  Theme,
  Tour,
  TourScene,
} from "@/lib/types";
import {
  clamp,
  cn,
  hapticTap,
  mapUrl,
  mediaBackground,
  publicVenueUrl,
} from "@/lib/utils";

type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;

type PanoramaView = {
  yaw: number;
  pitch: number;
  fov: number;
  aspect: number;
};

type HotspotScreenPosition = {
  left: number;
  top: number;
  visible: boolean;
  scale: number;
};

function projectHotspotToView(hotspot: Hotspot, view: PanoramaView) {
  const hotspotYaw = (hotspot.x / 100) * Math.PI * 2 - Math.PI;
  const hotspotPitch = ((50 - hotspot.y) / 50) * (Math.PI / 2);

  const target = {
    x: Math.sin(hotspotYaw) * Math.cos(hotspotPitch),
    y: Math.sin(hotspotPitch),
    z: Math.cos(hotspotYaw) * Math.cos(hotspotPitch),
  };
  const forward = {
    x: Math.sin(view.yaw) * Math.cos(view.pitch),
    y: Math.sin(view.pitch),
    z: Math.cos(view.yaw) * Math.cos(view.pitch),
  };
  const right = {
    x: Math.cos(view.yaw),
    y: 0,
    z: -Math.sin(view.yaw),
  };
  const up = {
    x: -Math.sin(view.pitch) * Math.sin(view.yaw),
    y: Math.cos(view.pitch),
    z: -Math.sin(view.pitch) * Math.cos(view.yaw),
  };

  const cameraX = target.x * right.x + target.y * right.y + target.z * right.z;
  const cameraY = target.x * up.x + target.y * up.y + target.z * up.z;
  const cameraZ = target.x * forward.x + target.y * forward.y + target.z * forward.z;

  if (cameraZ <= 0.05) return { left: hotspot.x, top: hotspot.y, visible: false, scale: 0.7 };

  const tanVertical = Math.tan((view.fov * Math.PI) / 360);
  const clipX = cameraX / (cameraZ * tanVertical * view.aspect);
  const clipY = cameraY / (cameraZ * tanVertical);
  const left = ((clipX + 1) / 2) * 100;
  const top = ((1 - clipY) / 2) * 100;
  const visible = left > -10 && left < 110 && top > -10 && top < 110;

  return {
    left,
    top,
    visible,
    scale: clamp(0.82 + cameraZ * 0.22, 0.78, 1.04),
  };
}

const iconMap: Record<string, IconComponent> = {
  "badge-percent": BadgePercent,
  "bar-chart": BarChart3,
  "book-open": BookOpen,
  "building": Building2,
  calendar: Calendar,
  camera: Camera,
  check: Check,
  clock: Clock,
  copy: Copy,
  "door-open": DoorOpen,
  download: Download,
  email: Mail,
  eye: Eye,
  globe: Globe,
  home: Home,
  image: ImagePlus,
  info: Info,
  instagram: Sparkles,
  layers: Layers3,
  link: LinkIcon,
  list: List,
  mail: Mail,
  map: Map,
  "map-pin": MapPin,
  menu: Menu,
  phone: Phone,
  route: Route,
  save: Save,
  settings: Settings,
  share: Share2,
  "shopping-bag": ShoppingBag,
  sparkles: Sparkles,
  tour: Route,
  upload: Upload,
  website: Globe,
};

export function Icon({
  name,
  className,
  size = 18,
}: {
  name?: string;
  className?: string;
  size?: number;
}) {
  const Component = iconMap[name ?? "sparkles"] ?? Sparkles;
  return <Component aria-hidden="true" className={className} height={size} width={size} />;
}

type ToastContextValue = {
  pushToast: (message: string) => void;
};

const ToastContext = createContext<ToastContextValue>({ pushToast: () => undefined });

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Array<{ id: string; message: string }>>([]);

  const pushToast = useCallback((message: string) => {
    const id = `${Date.now()}-${Math.random()}`;
    setToasts((items) => [...items, { id, message }]);
    window.setTimeout(() => {
      setToasts((items) => items.filter((item) => item.id !== id));
    }, 2600);
  }, []);

  return (
    <ToastContext.Provider value={{ pushToast }}>
      {children}
      <div className="fixed bottom-24 left-0 right-0 z-[90] mx-auto flex w-full max-w-sm flex-col gap-2 px-4 md:bottom-6">
        {toasts.map((toast) => (
          <motion.div
            key={toast.id}
            initial={{ opacity: 0, y: 18, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 18, scale: 0.96 }}
            className="glass-surface liquid-highlight rounded-full px-5 py-3 text-center text-sm font-semibold text-slate-900"
          >
            {toast.message}
          </motion.div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

export function GlassCard({
  children,
  className,
  dark,
  style,
}: {
  children: ReactNode;
  className?: string;
  dark?: boolean;
  style?: CSSProperties;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: "spring", stiffness: 220, damping: 26 }}
      style={style}
      className={cn(
        dark ? "glass-surface-dark text-white" : "glass-surface text-slate-950",
        "liquid-highlight rounded-[28px]",
        className,
      )}
    >
      {children}
    </motion.div>
  );
}

export function GlassButton({
  children,
  className,
  disabled,
  href,
  icon,
  onClick,
  type = "button",
  variant = "secondary",
  ariaLabel,
}: {
  children: ReactNode;
  className?: string;
  disabled?: boolean;
  href?: string;
  icon?: string;
  onClick?: () => void;
  type?: "button" | "submit" | "reset";
  variant?: "primary" | "secondary" | "ghost" | "dark";
  ariaLabel?: string;
}) {
  const base = cn(
    "touch-target relative inline-flex items-center justify-center gap-2 rounded-full px-5 py-3 text-sm font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-white/80",
    variant === "primary" &&
      "bg-slate-950 text-white shadow-[0_16px_42px_rgba(15,23,42,.25)] hover:bg-slate-800",
    variant === "secondary" &&
      "glass-surface text-slate-950 hover:bg-white/65",
    variant === "ghost" &&
      "bg-white/18 text-current ring-1 ring-white/28 backdrop-blur-xl hover:bg-white/26",
    variant === "dark" &&
      "glass-surface-dark text-white hover:bg-white/12",
    disabled && "opacity-45",
    className,
  );
  const content = (
    <>
      {icon ? <Icon name={icon} size={18} /> : null}
      <span>{children}</span>
    </>
  );

  if (href) {
    return (
      <Link
        aria-label={ariaLabel}
        className={base}
        href={href}
        onClick={() => {
          hapticTap();
          onClick?.();
        }}
      >
        {content}
      </Link>
    );
  }

  return (
    <motion.button
      aria-label={ariaLabel}
      className={base}
      disabled={disabled}
      onClick={() => {
        hapticTap();
        onClick?.();
      }}
      type={type}
      whileTap={{ scale: 0.965 }}
      transition={{ type: "spring", stiffness: 420, damping: 22 }}
    >
      {content}
    </motion.button>
  );
}

export function LiquidBackground({
  children,
  theme,
  className,
}: {
  children: ReactNode;
  theme?: Theme;
  className?: string;
}) {
  const background = theme?.backgroundStyle ?? "linear-gradient(145deg,#eef7fb,#f7fbff 48%,#e8f2ff)";
  return (
    <main
      className={cn("relative min-h-screen overflow-hidden", theme?.mode === "dark" ? "text-white" : "text-slate-950", className)}
      style={{ background }}
    >
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,.45),transparent_42%,rgba(45,126,255,.12))]" />
      <div className="pointer-events-none absolute inset-0 backdrop-blur-[1px]" />
      <div className="relative z-10">{children}</div>
    </main>
  );
}

export function PhoneFrame({
  children,
  className,
  dark,
}: {
  children: ReactNode;
  className?: string;
  dark?: boolean;
}) {
  return (
    <div
      className={cn(
        "mx-auto w-full max-w-[390px] rounded-[44px] border p-2 shadow-[0_28px_90px_rgba(15,23,42,.26)]",
        dark ? "border-white/18 bg-slate-950/70" : "border-white/70 bg-white/45",
        className,
      )}
    >
      <div className="relative overflow-hidden rounded-[36px] bg-black/5">
        <div className="absolute left-1/2 top-3 z-30 h-5 w-28 -translate-x-1/2 rounded-full bg-black/50" />
        {children}
      </div>
    </div>
  );
}

export function BottomNav() {
  const pathname = usePathname();
  const items = [
    { href: "/", label: "Home", icon: "home" },
    { href: "/demo", label: "Demo", icon: "route" },
    { href: "/app", label: "Dashboard", icon: "bar-chart" },
    { href: "/login", label: "Login", icon: "settings" },
  ];
  return (
    <nav className="fixed bottom-4 left-1/2 z-40 flex w-[calc(100%-24px)] max-w-sm -translate-x-1/2 items-center justify-between rounded-full border border-white/60 bg-white/50 p-1.5 shadow-2xl backdrop-blur-2xl md:hidden">
      {items.map((item) => {
        const active = pathname === item.href;
        return (
          <Link
            key={item.href}
            aria-label={item.label}
            className={cn(
              "flex h-12 flex-1 items-center justify-center rounded-full text-xs font-semibold transition",
              active ? "bg-slate-950 text-white" : "text-slate-700",
            )}
            href={item.href}
          >
            <Icon name={item.icon} />
          </Link>
        );
      })}
    </nav>
  );
}

export function MobileActionBar({
  actions,
  dark,
}: {
  actions: Array<{ label: string; icon: string; onClick: () => void }>;
  dark?: boolean;
}) {
  const [hidden, setHidden] = useState(false);
  const [menuHeight, setMenuHeight] = useState(72);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const menu = menuRef.current;
    if (!menu) return;
    const updateHeight = () => setMenuHeight(menu.offsetHeight);
    updateHeight();
    const observer = new ResizeObserver(updateHeight);
    observer.observe(menu);
    return () => observer.disconnect();
  }, []);

  function settleMenu(offsetY: number, velocityY: number) {
    if (offsetY > 24 || velocityY > 180) setHidden(true);
    if (offsetY < -18 || velocityY < -160) setHidden(false);
  }

  return (
    <div
      className={cn(
        "safe-bottom fixed bottom-0 left-0 right-0 z-40 mx-auto flex max-w-md justify-center px-3 pt-5 md:absolute",
        dark ? "text-white" : "text-slate-950",
      )}
    >
      <div className="relative w-full">
        <motion.div
          ref={menuRef}
          animate={{ y: hidden ? menuHeight + 16 : 0 }}
          className={cn("relative grid w-full grid-cols-5 gap-1.5 rounded-[28px] p-2", dark ? "glass-surface-dark" : "glass-surface")}
          drag="y"
          dragConstraints={{ top: 0, bottom: 0 }}
          dragElastic={0.08}
          initial={false}
          onDragEnd={(_, info) => settleMenu(info.offset.y, info.velocity.y)}
          transition={{ type: "spring", stiffness: 420, damping: 34, mass: 0.8 }}
        >
          <motion.button
            aria-label={hidden ? "Show bottom menu" : "Hide bottom menu"}
            className={cn(
              "absolute left-1/2 top-0 z-10 grid h-7 w-10 -translate-x-1/2 -translate-y-6 place-items-center rounded-t-2xl border border-b-0 shadow-xl backdrop-blur-2xl",
              dark ? "border-white/18 bg-slate-950/70" : "border-white/60 bg-white/72",
            )}
            onClick={() => setHidden((current) => !current)}
            type="button"
            whileTap={{ scale: 0.96 }}
          >
            <ChevronRight className={cn("transition-transform", hidden ? "-rotate-90" : "rotate-90")} size={16} />
          </motion.button>
          {actions.map((action) => (
            <motion.button
              key={action.label}
              aria-label={action.label}
              className="flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl text-[11px] font-semibold"
              onClick={() => {
                hapticTap();
                action.onClick();
              }}
              tabIndex={hidden ? -1 : 0}
              whileTap={{ scale: 0.94 }}
            >
              <Icon name={action.icon} size={18} />
              <span>{action.label}</span>
            </motion.button>
          ))}
        </motion.div>
      </div>
    </div>
  );
}

export function VenueHero({
  business,
  theme,
  children,
}: {
  business: Business;
  theme: Theme;
  children?: ReactNode;
}) {
  return (
    <section
      className="relative flex min-h-[720px] flex-col justify-end overflow-hidden rounded-b-[36px] px-5 pb-24 pt-20 md:min-h-[760px] md:rounded-[36px]"
      style={{ ...mediaBackground(business.coverImageUrl), backgroundSize: "cover", backgroundPosition: "center" }}
    >
      <div className="absolute inset-0 bg-gradient-to-b from-black/14 via-black/12 to-black/62" />
      <div className="relative z-10">
        <div className="mb-4 flex h-16 w-16 items-center justify-center overflow-hidden rounded-3xl border border-white/50 bg-white/35 shadow-2xl backdrop-blur-2xl">
          <div className="h-full w-full" style={{ ...mediaBackground(business.logoUrl), backgroundSize: "cover" }} />
        </div>
        <p className="mb-2 text-sm font-semibold uppercase tracking-[0.18em] text-white/78">{business.type}</p>
        <h1 className="venue-title max-w-sm text-4xl font-semibold text-white">{business.name}</h1>
        <p className="mt-3 max-w-sm text-base leading-7 text-white/82">{business.description}</p>
        {children}
      </div>
      <div
        className="absolute inset-x-8 bottom-8 h-28 rounded-full opacity-35 blur-3xl"
        style={{ background: theme.accentColor }}
      />
    </section>
  );
}

export function ShopfrontButtonGrid({
  buttons,
  onButton,
  dark,
}: {
  buttons: ShopfrontButton[];
  onButton: (button: ShopfrontButton) => void;
  dark?: boolean;
}) {
  return (
    <div className="grid grid-cols-2 gap-3">
      {buttons
        .filter((button) => button.isEnabled)
        .map((button) => (
          <motion.button
            key={button.id}
            className={cn(
              "touch-target rounded-[24px] p-4 text-left shadow-lg transition",
              button.isPrimary
                ? "bg-slate-950 text-white"
                : dark
                  ? "glass-surface-dark text-white"
                  : "glass-surface text-slate-950",
            )}
            onClick={() => onButton(button)}
            whileTap={{ scale: 0.965 }}
          >
            <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-2xl bg-white/25">
              <Icon name={button.icon} size={20} />
            </div>
            <div className="text-sm font-bold">{button.label}</div>
          </motion.button>
        ))}
    </div>
  );
}

export function BookingModal({
  business,
  defaultSource = "shopfront",
}: {
  business: Business;
  defaultSource?: BookingLead["source"];
}) {
  const { pushToast } = useToast();
  const [open, setOpen] = useState(false);
  const [source, setSource] = useState<BookingLead["source"]>(defaultSource);
  const [targetId, setTargetId] = useState<string | undefined>();
  const firstFieldRef = useRef<HTMLInputElement>(null);
  const [form, setForm] = useState({
    customerName: "",
    customerEmail: "",
    customerPhone: "",
    requestedDate: "",
    requestedTime: "",
    partySize: "2",
    message: "",
  });
  const [error, setError] = useState("");

  useEffect(() => {
    const handle = (event: Event) => {
      const detail = (event as CustomEvent<{ source?: BookingLead["source"]; targetId?: string }>).detail;
      setSource(detail?.source ?? defaultSource);
      setTargetId(detail?.targetId);
      setError("");
      setOpen(true);
    };
    window.addEventListener("portal:open-booking", handle);
    return () => window.removeEventListener("portal:open-booking", handle);
  }, [defaultSource]);

  useEffect(() => {
    if (!open) return;
    firstFieldRef.current?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [open]);

  if (!open) return null;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    try {
      submitBookingLead({
        businessId: business.id,
        source,
        customerName: form.customerName,
        customerEmail: form.customerEmail,
        customerPhone: form.customerPhone,
        requestedDate: form.requestedDate,
        requestedTime: form.requestedTime,
        partySize: Number(form.partySize) || undefined,
        message: form.message,
      });
      recordEvent({
        businessId: business.id,
        eventType: "booking_submit",
        targetId,
        targetLabel: "Booking form",
        source,
      });
      pushToast("Booking request saved");
      setOpen(false);
      setForm({
        customerName: "",
        customerEmail: "",
        customerPhone: "",
        requestedDate: "",
        requestedTime: "",
        partySize: "2",
        message: "",
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Please check the form.");
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/38 p-3 backdrop-blur-sm md:items-center">
      <motion.form
        initial={{ y: 80, opacity: 0, scale: 0.98 }}
        animate={{ y: 0, opacity: 1, scale: 1 }}
        className="glass-surface liquid-highlight max-h-[92vh] w-full max-w-md overflow-y-auto rounded-[32px] p-5 text-slate-950"
        onSubmit={submit}
      >
        <div className="mb-5 flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">Booking request</p>
            <h2 className="text-2xl font-bold">Book {business.name}</h2>
          </div>
          <button aria-label="Close booking form" className="rounded-full bg-white/60 p-3" onClick={() => setOpen(false)} type="button">
            <X size={18} />
          </button>
        </div>
        <div className="grid gap-3">
          <label className="grid gap-1 text-sm font-semibold">
            Name
            <input
              ref={firstFieldRef}
              className="rounded-2xl border border-white/70 bg-white/70 px-4 py-3 outline-none focus:ring-2 focus:ring-sky-300"
              value={form.customerName}
              onChange={(event) => setForm({ ...form, customerName: event.target.value })}
              required
            />
          </label>
          <label className="grid gap-1 text-sm font-semibold">
            Email
            <input
              className="rounded-2xl border border-white/70 bg-white/70 px-4 py-3 outline-none focus:ring-2 focus:ring-sky-300"
              type="email"
              value={form.customerEmail}
              onChange={(event) => setForm({ ...form, customerEmail: event.target.value })}
              required
            />
          </label>
          <label className="grid gap-1 text-sm font-semibold">
            Phone
            <input
              className="rounded-2xl border border-white/70 bg-white/70 px-4 py-3 outline-none focus:ring-2 focus:ring-sky-300"
              value={form.customerPhone}
              onChange={(event) => setForm({ ...form, customerPhone: event.target.value })}
            />
          </label>
          <div className="grid grid-cols-3 gap-3">
            <label className="grid gap-1 text-sm font-semibold">
              Date
              <input
                className="rounded-2xl border border-white/70 bg-white/70 px-3 py-3 outline-none focus:ring-2 focus:ring-sky-300"
                type="date"
                value={form.requestedDate}
                onChange={(event) => setForm({ ...form, requestedDate: event.target.value })}
              />
            </label>
            <label className="grid gap-1 text-sm font-semibold">
              Time
              <input
                className="rounded-2xl border border-white/70 bg-white/70 px-3 py-3 outline-none focus:ring-2 focus:ring-sky-300"
                type="time"
                value={form.requestedTime}
                onChange={(event) => setForm({ ...form, requestedTime: event.target.value })}
              />
            </label>
            <label className="grid gap-1 text-sm font-semibold">
              Party
              <input
                className="rounded-2xl border border-white/70 bg-white/70 px-3 py-3 outline-none focus:ring-2 focus:ring-sky-300"
                min={1}
                type="number"
                value={form.partySize}
                onChange={(event) => setForm({ ...form, partySize: event.target.value })}
              />
            </label>
          </div>
          <label className="grid gap-1 text-sm font-semibold">
            Message
            <textarea
              className="min-h-24 rounded-2xl border border-white/70 bg-white/70 px-4 py-3 outline-none focus:ring-2 focus:ring-sky-300"
              value={form.message}
              onChange={(event) => setForm({ ...form, message: event.target.value })}
            />
          </label>
          {error ? <p className="rounded-2xl bg-red-100 px-4 py-3 text-sm font-semibold text-red-700">{error}</p> : null}
          <GlassButton icon="calendar" type="submit" variant="primary">
            Send request
          </GlassButton>
        </div>
      </motion.form>
    </div>
  );
}

export function MenuBottomSheet({
  business,
  open,
  onClose,
}: {
  business: Business;
  open: boolean;
  onClose: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [onClose, open]);

  if (!open) return null;

  const rows =
    business.type === "Gym / Fitness"
      ? ["Foundations class", "Strength club", "Mobility reset", "Membership plans"]
      : business.type === "Salon / Beauty"
        ? ["Cut and finish", "Glow facial", "Brows and lashes", "Event styling"]
        : business.type.includes("Venue")
          ? ["Private hire", "Ceremony package", "Dinner package", "Supplier list"]
          : ["House brunch", "Roasted coffee", "Garden supper", "Private events"];

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/32 p-3 backdrop-blur-sm md:items-center">
      <motion.div
        initial={{ y: 90, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="glass-surface max-h-[86vh] w-full max-w-md overflow-hidden rounded-[32px] p-5 text-slate-950"
      >
        <div className="mb-4 flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">Menu and offers</p>
            <h2 className="text-2xl font-bold">{business.name}</h2>
          </div>
          <button aria-label="Close menu" className="rounded-full bg-white/60 p-3" onClick={onClose} type="button">
            <X size={18} />
          </button>
        </div>
        <div className="grid gap-3">
          {rows.map((row, index) => (
            <div key={row} className="flex items-center justify-between rounded-3xl bg-white/58 p-4">
              <div>
                <div className="font-bold">{row}</div>
                <div className="text-sm text-slate-600">Popular with venue visitors</div>
              </div>
              <span className="rounded-full bg-slate-950 px-3 py-1 text-xs font-bold text-white">
                {index === 0 ? "Featured" : "View"}
              </span>
            </div>
          ))}
          {business.menuUrl ? (
            <GlassButton icon="globe" onClick={() => window.open(business.menuUrl, "_blank", "noopener,noreferrer")} variant="primary">
              Open full menu
            </GlassButton>
          ) : null}
        </div>
      </motion.div>
    </div>
  );
}

export function ContactDrawer({
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
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/32 p-3 backdrop-blur-sm md:items-center">
      <motion.div
        initial={{ y: 80, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="glass-surface w-full max-w-md rounded-[32px] p-5 text-slate-950"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-2xl font-bold">Contact {business.name}</h2>
          <button aria-label="Close contact drawer" className="rounded-full bg-white/60 p-3" onClick={onClose} type="button">
            <X size={18} />
          </button>
        </div>
        <div className="grid gap-3">
          <GlassButton icon="phone" onClick={() => (window.location.href = `tel:${business.phone}`)} variant="primary">
            Call {business.phone}
          </GlassButton>
          <GlassButton icon="mail" onClick={() => (window.location.href = `mailto:${business.email}`)}>
            Email {business.email}
          </GlassButton>
          {business.website ? (
            <GlassButton icon="globe" onClick={() => window.open(business.website, "_blank", "noopener,noreferrer")}>
              Website
            </GlassButton>
          ) : null}
        </div>
      </motion.div>
    </div>
  );
}

export function SceneCarousel({
  scenes,
  activeSceneId,
  onSelect,
  open,
  onClose,
  dark,
}: {
  scenes: TourScene[];
  activeSceneId?: string;
  onSelect: (sceneId: string) => void;
  open: boolean;
  onClose: () => void;
  dark?: boolean;
}) {
  const activeScene = scenes.find((scene) => scene.id === activeSceneId) ?? scenes[0];

  useEffect(() => {
    if (!open) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [onClose, open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end bg-black/26 px-3 pb-24 backdrop-blur-[2px] md:absolute md:pb-28">
      <motion.div
        initial={{ y: 80, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: 80, opacity: 0 }}
        className={cn(
          "mx-auto w-full max-w-xl rounded-[30px] border p-3 shadow-2xl backdrop-blur-2xl",
          dark ? "border-white/18 bg-slate-950/62 text-white" : "border-white/54 bg-white/62 text-slate-950",
        )}
      >
        <button
          aria-label="Close room selector"
          className="flex w-full items-center justify-between gap-3 rounded-[24px] px-2 py-2 text-left"
          onClick={onClose}
          type="button"
        >
          <span className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-full bg-white/18">
              <Layers3 size={18} />
            </span>
            <span>
              <span className="block text-[11px] font-bold uppercase tracking-[0.16em] opacity-60">Room</span>
              <span className="block text-sm font-bold">{activeScene?.name ?? "Choose room"}</span>
            </span>
          </span>
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-white/18">
            <X size={18} />
          </span>
        </button>
        <div className="portal-scrollbar grid max-h-[42vh] gap-2 overflow-y-auto px-1 pb-1 pt-2">
          {scenes.map((scene) => (
            <button
              key={scene.id}
              className={cn(
                "flex min-h-20 items-center gap-3 rounded-[22px] border p-2 text-left transition",
                scene.id === activeSceneId
                  ? "border-white bg-white/76 text-slate-950 shadow-lg"
                  : dark
                    ? "border-white/12 bg-white/8 text-white"
                    : "border-white/48 bg-white/38 text-slate-900",
              )}
              onClick={() => {
                onSelect(scene.id);
                onClose();
              }}
              type="button"
            >
              <span className="h-16 w-20 shrink-0 rounded-2xl bg-cover bg-center" style={mediaBackground(scene.thumbnailUrl ?? scene.imageUrl)} />
              <span className="min-w-0">
                <span className="block truncate text-sm font-bold">{scene.name}</span>
                <span className="mt-1 block text-xs opacity-65">{scene.id === activeSceneId ? "Current room" : "Tap to enter"}</span>
              </span>
            </button>
          ))}
        </div>
      </motion.div>
    </div>
  );
}

export function HotspotMarker({
  hotspot,
  theme,
  onClick,
  style,
  hidden,
}: {
  hotspot: Hotspot;
  theme: Theme;
  onClick: () => void;
  style?: CSSProperties;
  hidden?: boolean;
}) {
  return (
    <motion.button
      aria-label={hotspot.label}
      aria-hidden={hidden}
      data-hotspot-id={hotspot.id}
      tabIndex={hidden ? -1 : 0}
      className={cn(
        "pointer-events-auto absolute z-20 flex -translate-x-1/2 -translate-y-1/2 items-center gap-2 rounded-full px-2 py-2 text-xs font-bold shadow-2xl",
        hidden && "pointer-events-none opacity-0",
      )}
      style={{
        left: `${hotspot.x}%`,
        top: `${hotspot.y}%`,
        background: theme.mode === "dark" ? "rgba(15,23,42,.72)" : "rgba(255,255,255,.72)",
        color: theme.mode === "dark" ? "white" : "#0f172a",
        border: `1px solid ${theme.accentColor}66`,
        backdropFilter: "blur(18px)",
        ...style,
      }}
      onClick={onClick}
      whileHover={{ scale: 1.04 }}
      whileTap={{ scale: 0.93 }}
    >
      <span className="grid h-9 w-9 place-items-center rounded-full" style={{ background: theme.accentColor, color: theme.mode === "dark" ? "#061426" : "white" }}>
        <Icon name={hotspot.icon} size={17} />
      </span>
      <span className="hidden max-w-32 truncate pr-2 sm:inline">{hotspot.label}</span>
    </motion.button>
  );
}

function PanoramaSphere({
  imageUrl,
  hotspots,
  onFirstDrag,
  onViewChange,
  onHotspotPositionsChange,
}: {
  imageUrl: string;
  hotspots: Hotspot[];
  onFirstDrag?: () => void;
  onViewChange: (view: PanoramaView) => void;
  onHotspotPositionsChange: (positions: Record<string, HotspotScreenPosition>) => void;
}) {
  const mountRef = useRef<HTMLDivElement>(null);
  const hotspotsRef = useRef(hotspots);
  const fallbackStyle = {
    ...mediaBackground(imageUrl),
    backgroundSize: imageUrl.startsWith("gradient:") ? "180% 110%" : "cover",
    backgroundPosition: "center",
  };

  useEffect(() => {
    hotspotsRef.current = hotspots;
  }, [hotspots]);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount || imageUrl.startsWith("gradient:")) return undefined;

    let disposed = false;
    let renderer: import("three").WebGLRenderer | null = null;
    let animationId = 0;
    let isDragging = false;
    let previousX = 0;
    let previousY = 0;
    let yaw = 0;
    let pitch = 0;
    let fov = 82;
    let hasDragged = false;

    const cleanupHandlers: Array<() => void> = [];

    async function setup() {
      const THREE = await import("three");
      if (disposed || !mountRef.current) return;

      const scene = new THREE.Scene();
      const camera = new THREE.PerspectiveCamera(
        fov,
        mountRef.current.clientWidth / Math.max(mountRef.current.clientHeight, 1),
        0.1,
        1100,
      );
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 3);
      renderer.setPixelRatio(pixelRatio);
      renderer.setSize(mountRef.current.clientWidth, mountRef.current.clientHeight);
      renderer.domElement.className = "h-full w-full";
      mountRef.current.dataset.pixelRatio = pixelRatio.toFixed(2);
      mountRef.current.appendChild(renderer.domElement);

      function emitView() {
        if (!mountRef.current) return;
        updateCamera();
        mountRef.current.dataset.yaw = yaw.toFixed(4);
        mountRef.current.dataset.pitch = pitch.toFixed(4);
        mountRef.current.dataset.fov = fov.toFixed(2);
        onViewChange({ yaw, pitch, fov, aspect: camera.aspect });
        emitHotspotPositions();
      }

      const geometry = new THREE.SphereGeometry(500, 128, 96);
      geometry.scale(-1, 1, 1);
      const texture = await new THREE.TextureLoader().loadAsync(imageUrl);
      texture.colorSpace = THREE.SRGBColorSpace;
      texture.generateMipmaps = true;
      texture.minFilter = THREE.LinearMipmapLinearFilter;
      texture.magFilter = THREE.LinearFilter;
      texture.anisotropy = renderer.capabilities.getMaxAnisotropy();
      if (mountRef.current) {
        mountRef.current.dataset.textureWidth = String(texture.image?.width ?? "");
        mountRef.current.dataset.textureHeight = String(texture.image?.height ?? "");
        mountRef.current.dataset.anisotropy = String(texture.anisotropy);
      }
      const material = new THREE.MeshBasicMaterial({ map: texture });
      const sphere = new THREE.Mesh(geometry, material);
      scene.add(sphere);

      function updateCamera() {
        pitch = Math.max(-1.35, Math.min(1.35, pitch));
        const target = new THREE.Vector3(
          Math.sin(yaw) * Math.cos(pitch),
          Math.sin(pitch),
          Math.cos(yaw) * Math.cos(pitch),
        );
        camera.fov = fov;
        camera.updateProjectionMatrix();
        camera.lookAt(target);
      }

      function emitHotspotPositions() {
        camera.updateMatrixWorld(true);
        const forward = new THREE.Vector3(
          Math.sin(yaw) * Math.cos(pitch),
          Math.sin(pitch),
          Math.cos(yaw) * Math.cos(pitch),
        ).normalize();
        const nextPositions: Record<string, HotspotScreenPosition> = {};

        for (const hotspot of hotspotsRef.current) {
          const hotspotYaw = (hotspot.x / 100) * Math.PI * 2 - Math.PI;
          const hotspotPitch = ((50 - hotspot.y) / 50) * (Math.PI / 2);
          const point = new THREE.Vector3(
            Math.sin(hotspotYaw) * Math.cos(hotspotPitch),
            Math.sin(hotspotPitch),
            Math.cos(hotspotYaw) * Math.cos(hotspotPitch),
          );
          const facing = point.dot(forward);
          const projected = point.clone().multiplyScalar(500).project(camera);
          const left = ((projected.x + 1) / 2) * 100;
          const top = ((1 - projected.y) / 2) * 100;
          const visible = facing > 0.02 && left > -10 && left < 110 && top > -10 && top < 110;

          nextPositions[hotspot.id] = {
            left,
            top,
            visible,
            scale: clamp(0.82 + facing * 0.22, 0.78, 1.04),
          };
        }

        onHotspotPositionsChange(nextPositions);
      }

      function render() {
        if (!renderer) return;
        updateCamera();
        renderer.render(scene, camera);
        animationId = window.requestAnimationFrame(render);
      }

      function resize() {
        if (!renderer || !mountRef.current) return;
        const width = mountRef.current.clientWidth;
        const height = Math.max(mountRef.current.clientHeight, 1);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
        renderer.setSize(width, height);
        emitView();
      }

      function startDrag(clientX: number, clientY: number) {
        isDragging = true;
        previousX = clientX;
        previousY = clientY;
      }

      function moveDrag(clientX: number, clientY: number) {
        if (!isDragging) return;
        const dx = clientX - previousX;
        const dy = clientY - previousY;
        previousX = clientX;
        previousY = clientY;
        yaw += dx * 0.0042;
        pitch += dy * 0.0032;
        pitch = Math.max(-1.35, Math.min(1.35, pitch));
        if (!hasDragged && (Math.abs(dx) > 1 || Math.abs(dy) > 1)) {
          hasDragged = true;
          onFirstDrag?.();
        }
        emitView();
      }

      function endDrag() {
        isDragging = false;
      }

      function pointerDown(event: PointerEvent) {
        startDrag(event.clientX, event.clientY);
        renderer?.domElement.setPointerCapture(event.pointerId);
      }

      function pointerMove(event: PointerEvent) {
        moveDrag(event.clientX, event.clientY);
      }

      function pointerUp(event: PointerEvent) {
        endDrag();
        try {
          renderer?.domElement.releasePointerCapture(event.pointerId);
        } catch {
          // The pointer may already be released by the browser.
        }
      }

      function mouseDown(event: MouseEvent) {
        startDrag(event.clientX, event.clientY);
      }

      function mouseMove(event: MouseEvent) {
        moveDrag(event.clientX, event.clientY);
      }

      function touchStart(event: TouchEvent) {
        const touch = event.touches[0];
        if (!touch) return;
        startDrag(touch.clientX, touch.clientY);
      }

      function touchMove(event: TouchEvent) {
        const touch = event.touches[0];
        if (!touch) return;
        event.preventDefault();
        moveDrag(touch.clientX, touch.clientY);
      }

      function wheel(event: WheelEvent) {
        event.preventDefault();
        fov = Math.max(55, Math.min(98, fov + event.deltaY * 0.035));
        emitView();
      }

      window.addEventListener("resize", resize);
      renderer.domElement.addEventListener("pointerdown", pointerDown);
      renderer.domElement.addEventListener("pointermove", pointerMove);
      renderer.domElement.addEventListener("pointerup", pointerUp);
      renderer.domElement.addEventListener("pointercancel", pointerUp);
      renderer.domElement.addEventListener("mousedown", mouseDown);
      window.addEventListener("mousemove", mouseMove);
      window.addEventListener("mouseup", endDrag);
      renderer.domElement.addEventListener("touchstart", touchStart, { passive: true });
      renderer.domElement.addEventListener("touchmove", touchMove, { passive: false });
      window.addEventListener("touchend", endDrag);
      renderer.domElement.addEventListener("wheel", wheel, { passive: false });

      cleanupHandlers.push(
        () => window.removeEventListener("resize", resize),
        () => renderer?.domElement.removeEventListener("pointerdown", pointerDown),
        () => renderer?.domElement.removeEventListener("pointermove", pointerMove),
        () => renderer?.domElement.removeEventListener("pointerup", pointerUp),
        () => renderer?.domElement.removeEventListener("pointercancel", pointerUp),
        () => renderer?.domElement.removeEventListener("mousedown", mouseDown),
        () => window.removeEventListener("mousemove", mouseMove),
        () => window.removeEventListener("mouseup", endDrag),
        () => renderer?.domElement.removeEventListener("touchstart", touchStart),
        () => renderer?.domElement.removeEventListener("touchmove", touchMove),
        () => window.removeEventListener("touchend", endDrag),
        () => renderer?.domElement.removeEventListener("wheel", wheel),
      );

      emitView();
      render();
    }

    void setup();

    return () => {
      disposed = true;
      cleanupHandlers.forEach((cleanup) => cleanup());
      window.cancelAnimationFrame(animationId);
      renderer?.dispose();
      mount.replaceChildren();
    };
  }, [imageUrl, onFirstDrag, onHotspotPositionsChange, onViewChange]);

  if (imageUrl.startsWith("gradient:")) {
    return (
      <div
        ref={mountRef}
        className="absolute inset-0 cursor-grab touch-none bg-cover bg-center active:cursor-grabbing"
        style={fallbackStyle}
      />
    );
  }

  return (
    <div
      ref={mountRef}
      aria-label="360 panorama viewer"
      className="absolute inset-0 cursor-grab touch-none overflow-hidden bg-black active:cursor-grabbing"
      role="img"
    />
  );
}

export function TourViewer({
  business,
  tour,
  scenes,
  theme,
  onBack,
  onBook,
  onMenu,
  onContact,
}: {
  business: Business;
  tour: Tour;
  scenes: TourScene[];
  theme: Theme;
  onBack: () => void;
  onBook: (source: BookingLead["source"], targetId?: string) => void;
  onMenu: () => void;
  onContact: () => void;
}) {
  const [activeSceneId, setActiveSceneId] = useState(tour.defaultSceneId ?? scenes[0]?.id);
  const [panoramaView, setPanoramaView] = useState<PanoramaView>({ yaw: 0, pitch: 0, fov: 82, aspect: 1 });
  const [hotspotPositions, setHotspotPositions] = useState<Record<string, HotspotScreenPosition>>({});
  const [infoHotspot, setInfoHotspot] = useState<Hotspot | null>(null);
  const [roomMenuOpen, setRoomMenuOpen] = useState(false);
  const [showDragHint, setShowDragHint] = useState(true);
  const activeScene = scenes.find((scene) => scene.id === activeSceneId) ?? scenes[0];
  const hotspots = activeScene ? getHotspotsForScene(activeScene.id).filter((hotspot) => hotspot.isEnabled) : [];
  const activeSceneEventId = activeScene?.id;
  const activeSceneEventName = activeScene?.name;
  const handleViewChange = useCallback((view: PanoramaView) => {
    setPanoramaView(view);
  }, []);
  const handleHotspotPositionsChange = useCallback((positions: Record<string, HotspotScreenPosition>) => {
    setHotspotPositions(positions);
  }, []);
  const handleFirstPanoramaDrag = useCallback(() => {
    setShowDragHint(false);
  }, []);

  useEffect(() => {
    if (activeSceneEventId && activeSceneEventName) {
      recordEvent({
        businessId: business.id,
        eventType: "button_click",
        targetId: activeSceneEventId,
        targetLabel: activeSceneEventName,
        source: `scene:${activeSceneEventName}`,
      });
    }
  }, [activeSceneEventId, activeSceneEventName, business.id]);

  if (!activeScene) {
    return (
      <div className="grid min-h-screen place-items-center p-6 text-center">
        <EmptyState icon="route" title="No scenes yet" body="Add a room in the tour editor to publish a customer tour." />
      </div>
    );
  }

  function hotspotAction(hotspot: Hotspot) {
    recordEvent({
      businessId: business.id,
      eventType: "hotspot_click",
      targetId: hotspot.id,
      targetLabel: hotspot.label,
      source: `scene:${activeScene.name}`,
    });
    if (hotspot.type === "booking") {
      recordEvent({
        businessId: business.id,
        eventType: "booking_open",
        targetId: hotspot.id,
        targetLabel: hotspot.label,
        source: "hotspot",
      });
      onBook("hotspot", hotspot.id);
    } else if (hotspot.type === "menu") {
      recordEvent({
        businessId: business.id,
        eventType: "menu_open",
        targetId: hotspot.id,
        targetLabel: hotspot.label,
        source: "hotspot",
      });
      onMenu();
    } else if (hotspot.type === "contact") {
      onContact();
    } else if (hotspot.type === "scene" && hotspot.targetSceneId) {
      setActiveSceneId(hotspot.targetSceneId);
    } else if (hotspot.type === "external" && hotspot.targetUrl) {
      window.open(hotspot.targetUrl, "_blank", "noopener,noreferrer");
    } else {
      setInfoHotspot(hotspot);
    }
  }

  return (
    <div className="relative min-h-screen overflow-hidden text-white" style={{ background: theme.backgroundStyle }}>
      <PanoramaSphere
        hotspots={hotspots}
        imageUrl={activeScene.imageUrl}
        onFirstDrag={handleFirstPanoramaDrag}
        onHotspotPositionsChange={handleHotspotPositionsChange}
        onViewChange={handleViewChange}
      />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-black/38 via-black/8 to-black/60" />
      <div className="pointer-events-none absolute inset-0 z-10">
        {hotspots.map((hotspot) => {
          const position = hotspotPositions[hotspot.id] ?? projectHotspotToView(hotspot, panoramaView);
          return (
            <HotspotMarker
              key={hotspot.id}
              hidden={!position.visible}
              hotspot={hotspot}
              theme={theme}
              onClick={() => hotspotAction(hotspot)}
              style={{
                left: `${position.left}%`,
                top: `${position.top}%`,
                transform: `translate(-50%, -50%) scale(${position.scale})`,
              }}
            />
          );
        })}
      </div>
      <div className="safe-bottom pointer-events-none relative z-20 min-h-screen px-4 pb-44 pt-4">
        <div className="pointer-events-auto flex items-center justify-between gap-3">
          <button aria-label="Back to shopfront" className="glass-surface-dark grid h-12 w-12 place-items-center rounded-full" onClick={onBack} type="button">
            <ArrowLeft size={19} />
          </button>
          <button aria-label="Share tour" className="glass-surface-dark grid h-12 w-12 place-items-center rounded-full" onClick={() => navigator.clipboard.writeText(publicVenueUrl(business))} type="button">
            <Share2 size={19} />
          </button>
        </div>
        {showDragHint ? (
          <motion.div
            animate={{ opacity: 1, y: 0 }}
            className="mt-4 inline-flex rounded-full bg-black/28 px-4 py-2 text-xs font-semibold backdrop-blur-xl"
            exit={{ opacity: 0, y: -6 }}
            initial={{ opacity: 0, y: -4 }}
          >
            Drag to look around
          </motion.div>
        ) : null}
      </div>
      <SceneCarousel
        dark={theme.mode === "dark"}
        scenes={scenes}
        activeSceneId={activeScene.id}
        onClose={() => setRoomMenuOpen(false)}
        onSelect={setActiveSceneId}
        open={roomMenuOpen}
      />
      <MobileActionBar
        dark
        actions={[
          { label: "Info", icon: "info", onClick: () => setInfoHotspot({ id: "scene-info", sceneId: activeScene.id, label: activeScene.name, type: "info", x: 0, y: 0, icon: "info", description: activeScene.description, isEnabled: true }) },
          { label: "Menu", icon: "menu", onClick: onMenu },
          { label: "Book", icon: "calendar", onClick: () => onBook("tour") },
          { label: "Map", icon: "map-pin", onClick: () => window.open(mapUrl(business.address), "_blank", "noopener,noreferrer") },
          { label: "Room", icon: "layers", onClick: () => setRoomMenuOpen(true) },
        ]}
      />
      {infoHotspot ? (
        <div className="fixed inset-0 z-50 flex items-end bg-black/30 p-3 backdrop-blur-sm md:items-center md:justify-center">
          <motion.div
            initial={{ y: 60, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            className="glass-surface w-full max-w-md rounded-[30px] p-5 text-slate-950"
          >
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-2xl font-bold">{infoHotspot.label}</h2>
              <button aria-label="Close information" className="rounded-full bg-white/60 p-3" onClick={() => setInfoHotspot(null)} type="button">
                <X size={18} />
              </button>
            </div>
            <p className="leading-7 text-slate-700">{infoHotspot.description ?? activeScene.description}</p>
          </motion.div>
        </div>
      ) : null}
    </div>
  );
}

export function HotspotEditor({
  scene,
  hotspots,
  theme,
  onAdd,
  onMove,
  onSelect,
}: {
  scene: TourScene;
  hotspots: Hotspot[];
  theme: Theme;
  onAdd: (x: number, y: number) => void;
  onMove: (hotspotId: string, x: number, y: number) => void;
  onSelect: (hotspot: Hotspot) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState<string | null>(null);

  function point(event: ReactPointerEvent<HTMLDivElement>) {
    const rect = ref.current?.getBoundingClientRect();
    if (!rect) return { x: 50, y: 50 };
    return {
      x: clamp(((event.clientX - rect.left) / rect.width) * 100, 5, 95),
      y: clamp(((event.clientY - rect.top) / rect.height) * 100, 8, 92),
    };
  }

  return (
    <div
      ref={ref}
      className="relative aspect-[4/5] overflow-hidden rounded-[30px] bg-cover bg-center shadow-2xl md:aspect-video"
      style={mediaBackground(scene.imageUrl)}
      onPointerDown={(event) => {
        if (event.target === event.currentTarget) {
          const next = point(event);
          onAdd(next.x, next.y);
        }
      }}
      onPointerMove={(event) => {
        if (!dragging) return;
        const next = point(event);
        onMove(dragging, next.x, next.y);
      }}
      onPointerUp={() => setDragging(null)}
    >
      <div className="absolute inset-0 bg-black/10" />
      {hotspots.map((hotspot) => (
        <button
          key={hotspot.id}
          aria-label={`Edit ${hotspot.label}`}
          className="absolute z-10 grid h-12 w-12 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border border-white/70 shadow-2xl backdrop-blur-xl"
          style={{ left: `${hotspot.x}%`, top: `${hotspot.y}%`, background: theme.accentColor, color: "white" }}
          onClick={(event) => {
            event.stopPropagation();
            onSelect(hotspot);
          }}
          onPointerDown={(event) => {
            event.stopPropagation();
            setDragging(hotspot.id);
          }}
          type="button"
        >
          <Icon name={hotspot.icon} size={18} />
        </button>
      ))}
    </div>
  );
}

export function ThemePicker({
  themes,
  selectedThemeId,
  onSelect,
}: {
  themes: Theme[];
  selectedThemeId: string;
  onSelect: (themeId: string) => void;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {themes.map((theme) => (
        <button
          key={theme.id}
          className={cn(
            "rounded-[26px] border p-4 text-left shadow-lg transition",
            selectedThemeId === theme.id ? "border-slate-950 bg-white/72" : "border-white/54 bg-white/42 backdrop-blur-xl",
          )}
          onClick={() => onSelect(theme.id)}
          type="button"
        >
          <div className="mb-3 h-24 rounded-[22px]" style={{ background: theme.backgroundStyle }} />
          <div className="flex items-center justify-between">
            <div>
              <div className="font-bold">{theme.name}</div>
              <p className="mt-1 text-sm text-slate-600">{theme.description}</p>
            </div>
            {selectedThemeId === theme.id ? <Check className="shrink-0" size={20} /> : null}
          </div>
        </button>
      ))}
    </div>
  );
}

export function Sparkline({ values, color = "#49bdf2" }: { values: number[]; color?: string }) {
  const max = Math.max(...values, 1);
  const points = values
    .map((value, index) => {
      const x = (index / Math.max(values.length - 1, 1)) * 100;
      const y = 34 - (value / max) * 30;
      return `${x},${y}`;
    })
    .join(" ");
  return (
    <svg aria-hidden="true" className="h-10 w-full overflow-visible" viewBox="0 0 100 36">
      <polyline fill="none" points={points} stroke={color} strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />
    </svg>
  );
}

export function AnalyticsCard({
  label,
  value,
  summary,
  accent,
}: {
  label: string;
  value: string | number;
  summary: AnalyticsSummary;
  accent: string;
}) {
  return (
    <GlassCard className="p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-semibold text-slate-500">{label}</p>
          <div className="mt-2 text-3xl font-bold">{value}</div>
        </div>
        <span className="grid h-11 w-11 place-items-center rounded-2xl text-white" style={{ background: accent }}>
          <BarChart3 size={20} />
        </span>
      </div>
      <div className="mt-4">
        <Sparkline color={accent} values={summary.sparkline} />
      </div>
    </GlassCard>
  );
}

export function DonutChart({
  data,
  colors = ["#49bdf2", "#8b5cf6", "#34d399", "#f59e0b"],
}: {
  data: Array<{ source: string; percentage: number }>;
  colors?: string[];
}) {
  const slices = data.reduce<Array<{ source: string; percentage: number; offset: number }>>((items, slice) => {
    const previous = items.reduce((sum, item) => sum + item.percentage, 0);
    return [...items, { ...slice, offset: 25 - previous }];
  }, []);
  return (
    <div className="flex items-center gap-5">
      <svg className="h-28 w-28 -rotate-90" viewBox="0 0 42 42">
        <circle cx="21" cy="21" fill="none" r="15.915" stroke="rgba(15,23,42,.1)" strokeWidth="6" />
        {slices.map((slice, index) => {
          const dash = `${slice.percentage} ${100 - slice.percentage}`;
          return (
            <circle
              key={slice.source}
              cx="21"
              cy="21"
              fill="none"
              r="15.915"
              stroke={colors[index % colors.length]}
              strokeDasharray={dash}
              strokeDashoffset={slice.offset}
              strokeLinecap="round"
              strokeWidth="6"
            />
          );
        })}
      </svg>
      <div className="grid gap-2 text-sm">
        {data.map((slice, index) => (
          <div key={slice.source} className="flex items-center gap-2">
            <span className="h-3 w-3 rounded-full" style={{ background: colors[index % colors.length] }} />
            <span className="font-semibold capitalize">{slice.source}</span>
            <span className="text-slate-500">{Math.round(slice.percentage)}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function QRCodePanel({ business }: { business: Business }) {
  const { pushToast } = useToast();
  const url = publicVenueUrl(business);
  function download() {
    const canvas = document.getElementById("venue-qr") as HTMLCanvasElement | null;
    if (!canvas) return;
    const link = document.createElement("a");
    link.download = `${business.slug}-qr.png`;
    link.href = canvas.toDataURL("image/png");
    link.click();
  }
  return (
    <GlassCard className="p-5 text-center">
      <div className="mx-auto grid h-52 w-52 place-items-center rounded-[30px] bg-white">
        <QRCodeCanvas id="venue-qr" value={url} size={168} bgColor="#ffffff" fgColor="#0f172a" includeMargin />
      </div>
      <div className="url-preview mt-4 break-all text-sm font-medium text-slate-600">{url}</div>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <GlassButton
          icon="copy"
          onClick={async () => {
            await navigator.clipboard.writeText(url);
            pushToast("Public link copied");
          }}
        >
          Copy
        </GlassButton>
        <GlassButton icon="download" onClick={download}>
          QR
        </GlassButton>
      </div>
    </GlassCard>
  );
}

export function DashboardSidebar({ business, production = false }: { business: Business; production?: boolean }) {
  const pathname = usePathname();
  const nav = [
    ["/app", "Dashboard", LayoutDashboard],
    ["/app/shopfront", "Shopfront", Layers3],
    ["/app/tour", "Tour", Route],
    ["/app/style", "Style", Palette],
    ["/app/publish", "Publish", QrCode],
    ["/app/analytics", "Analytics", BarChart3],
    ["/app/bookings", "Bookings", Clipboard],
    ["/app/settings", "Settings", Settings],
  ] as const;
  return (
    <aside className="sticky top-5 hidden h-[calc(100vh-40px)] w-72 shrink-0 rounded-[34px] bg-slate-950/82 p-4 text-white shadow-2xl backdrop-blur-2xl lg:block">
      <Link className="mb-6 flex items-center gap-3 rounded-[26px] bg-white/10 p-3" href="/app">
        <div className="grid h-12 w-12 place-items-center rounded-2xl bg-white/16">
          <Sparkles size={22} />
        </div>
        <div>
          <div className="text-sm font-bold">Portal</div>
          <div className="text-xs text-white/60">{business.name}</div>
        </div>
      </Link>
      <nav className="grid gap-1.5">
        {nav.filter(([href]) => !production || !["/app/analytics", "/app/bookings"].includes(href)).map(([href, label, Component]) => {
          const active = pathname === href;
          return (
            <Link
              key={href}
              className={cn(
                "flex min-h-12 items-center gap-3 rounded-2xl px-4 text-sm font-semibold transition",
                active ? "bg-white text-slate-950" : "text-white/72 hover:bg-white/10 hover:text-white",
              )}
              href={href}
            >
              <Component size={18} />
              {label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}

export function DashboardMobileNav() {
  const pathname = usePathname();
  const nav = [
    ["/app", "Home", LayoutDashboard],
    ["/app/shopfront", "Page", Layers3],
    ["/app/tour", "Tour", Route],
    ["/app/publish", "Share", QrCode],
    ["/app/settings", "More", MoreHorizontal],
  ] as const;
  return (
    <nav className="safe-bottom fixed bottom-0 left-0 right-0 z-40 bg-transparent px-3 pt-2 lg:hidden">
      <div className="glass-surface mx-auto grid max-w-md grid-cols-5 rounded-[28px] p-1.5">
        {nav.map(([href, label, Component]) => {
          const active = pathname === href;
          return (
            <Link
              key={href}
              className={cn(
                "flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl text-[11px] font-bold",
                active ? "bg-slate-950 text-white" : "text-slate-600",
              )}
              href={href}
            >
              <Component size={18} />
              {label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

export function ImageUploader({
  label,
  onUploaded,
  variant = "venue",
}: {
  label: string;
  onUploaded: (url: string) => void;
  variant?: "venue" | "logo";
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handle(file?: File) {
    if (!file) return;
    const validation = validateImage(file);
    if (!validation.valid) {
      setError(validation.error ?? "Unsupported file type.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const url = variant === "logo" ? await uploadLogo(file) : await uploadVenueImage(file);
      onUploaded(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Image upload failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <label className="block rounded-[24px] border border-dashed border-slate-300 bg-white/46 p-4 text-center">
      <input
        className="sr-only"
        type="file"
        accept="image/*"
        onChange={(event) => void handle(event.target.files?.[0])}
      />
      <div className="mx-auto mb-2 grid h-12 w-12 place-items-center rounded-2xl bg-white/70">
        {busy ? <RefreshCw className="animate-spin" size={20} /> : <Upload size={20} />}
      </div>
      <div className="font-bold">{label}</div>
      <div className="text-sm text-slate-500">JPG, PNG, or WebP under 8 MB</div>
      {error ? <div className="mt-2 text-sm font-semibold text-red-600">{error}</div> : null}
    </label>
  );
}

type EditableFieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  textarea?: boolean;
  inputClassName?: string;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "value" | "onChange">;

export function EditableField({ label, value, onChange, textarea, inputClassName = "", ...props }: EditableFieldProps) {
  const fieldClassName = `rounded-2xl border border-white/70 bg-white/70 px-4 py-3 text-slate-950 outline-none focus:ring-2 focus:ring-sky-300 ${inputClassName}`;
  return (
    <label className="grid gap-1.5 text-sm font-semibold text-slate-700">
      {label}
      {textarea ? (
        <textarea
          className={`min-h-28 ${fieldClassName}`}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          {...(props as TextareaHTMLAttributes<HTMLTextAreaElement>)}
        />
      ) : (
        <input
          className={`min-h-12 ${fieldClassName}`}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          {...(props as InputHTMLAttributes<HTMLInputElement>)}
        />
      )}
    </label>
  );
}

export function EmptyState({
  icon,
  title,
  body,
  action,
}: {
  icon: string;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <GlassCard className="p-8 text-center">
      <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-3xl bg-white/70">
        <Icon name={icon} size={24} />
      </div>
      <h2 className="marketing-display text-2xl font-semibold">{title}</h2>
      <p className="mx-auto mt-2 max-w-sm leading-7 text-slate-600">{body}</p>
      {action ? <div className="mt-5">{action}</div> : null}
    </GlassCard>
  );
}

export function MoreDrawer({
  business,
  buttons,
  open,
  onClose,
  onButton,
}: {
  business: Business;
  buttons: ShopfrontButton[];
  open: boolean;
  onClose: () => void;
  onButton: (button: ShopfrontButton) => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end bg-slate-950/30 p-3 backdrop-blur-sm md:items-center md:justify-center">
      <motion.div
        initial={{ y: 90, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="glass-surface w-full max-w-md rounded-[32px] p-5 text-slate-950"
      >
        <div className="mb-4 flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">All links</p>
            <h2 className="venue-title text-2xl font-semibold">{business.name}</h2>
          </div>
          <button aria-label="Close more menu" className="rounded-full bg-white/60 p-3" onClick={onClose} type="button">
            <X size={18} />
          </button>
        </div>
        <div className="grid gap-2">
          {buttons
            .filter((button) => button.isEnabled)
            .map((button) => (
              <button
                key={button.id}
                className="flex min-h-14 items-center justify-between rounded-2xl bg-white/58 px-4 text-left font-bold"
                onClick={() => onButton(button)}
                type="button"
              >
                <span className="flex items-center gap-3">
                  <Icon name={button.icon} />
                  {button.label}
                </span>
                <ChevronRight size={18} />
              </button>
            ))}
        </div>
      </motion.div>
    </div>
  );
}

export function DashboardShell({
  business,
  children,
  title,
  eyebrow,
  action,
  production = false,
}: {
  business: Business;
  children: ReactNode;
  title: string;
  eyebrow?: string;
  action?: ReactNode;
  production?: boolean;
}) {
  return (
    <ToastProvider>
      <LiquidBackground className="pb-28 lg:pb-0">
        <div className="mx-auto flex w-full max-w-7xl gap-5 p-3 sm:p-5">
          <DashboardSidebar business={business} production={production} />
          <section className="min-w-0 flex-1">
            <header className="mb-5 flex flex-col gap-3 rounded-[32px] bg-white/34 p-4 backdrop-blur-xl sm:flex-row sm:items-center sm:justify-between lg:bg-transparent lg:p-0">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">{eyebrow ?? business.name}</p>
                <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">{title}</h1>
              </div>
              {action}
            </header>
            {children}
          </section>
        </div>
        <DashboardMobileNav />
      </LiquidBackground>
    </ToastProvider>
  );
}

export function PublicShell({ children, theme }: { children: ReactNode; theme: Theme }) {
  return (
    <ToastProvider>
      <LiquidBackground theme={theme}>{children}</LiquidBackground>
    </ToastProvider>
  );
}

export function PhoneShopfrontPreview({
  business,
  theme,
  buttons,
}: {
  business: Business;
  theme: Theme;
  buttons: ShopfrontButton[];
}) {
  const primary = buttons.find((button) => button.isPrimary && button.isEnabled) ?? buttons[0];
  return (
    <PhoneFrame dark={theme.mode === "dark"}>
      <div className="min-h-[660px] p-3" style={{ background: theme.backgroundStyle }}>
        <div
          className="relative flex h-[370px] flex-col justify-end overflow-hidden rounded-[30px] bg-cover bg-center p-5 text-white"
          style={mediaBackground(business.coverImageUrl)}
        >
          <div className="absolute inset-0 bg-gradient-to-b from-black/10 to-black/62" />
          <div className="relative z-10">
            <div className="mb-3 h-14 w-14 rounded-2xl bg-white/35 backdrop-blur-xl" style={mediaBackground(business.logoUrl)} />
            <h3 className="venue-title text-3xl font-semibold">{business.name}</h3>
            <p className="mt-2 line-clamp-3 text-sm text-white/80">{business.description}</p>
          </div>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2">
          {buttons.slice(0, 4).map((button) => (
            <div
              key={button.id}
              className={cn(
                "rounded-2xl p-3 text-sm font-bold",
                button.id === primary?.id ? "bg-slate-950 text-white" : "bg-white/48 text-slate-950 backdrop-blur-xl",
              )}
            >
              <Icon name={button.icon} />
              <div className="mt-2">{button.label}</div>
            </div>
          ))}
        </div>
      </div>
    </PhoneFrame>
  );
}

export function PublicButtonHandler({
  business,
  onMenu,
  onOffers,
  onMore,
}: {
  business: Business;
  onMenu: () => void;
  onOffers: () => void;
  onMore: () => void;
}) {
  const router = useRouter();
  return { business, onMenu, onOffers, onMore, navigate: router.push };
}

export { handleCall, handleDirections, handleOpenMenu, CircleDollarSign, MousePointer2, Trash2 };
