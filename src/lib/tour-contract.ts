/** Shared Portal v1 contract. Angles are degrees; yaw 0 is panorama centre. */
export type View = { yaw: number; pitch: number; fov: number };
export type PointKind = "scene" | "info" | "image" | "video" | "external" | "booking" | "menu" | "contact" | "product";
export type TourPoint = { id: string; label: string; kind: PointKind; yaw: number; pitch: number; description: string; url?: string; assetPath?: string; mediaUrl?: string; targetSceneId?: string; enabled: boolean };
export type Scene = { id: string; name: string; assetPath: string; imageUrl?: string; initialView: View; points: TourPoint[] };
export type TourBranding = { accentColor: string; colorScheme: "light" | "dark"; logoAssetPath?: string; logoUrl?: string };
export type TourDocument = { version: 1; title: string; description: string; defaultSceneId: string; scenes: Scene[]; branding?: TourBranding; mapPositions?: Record<string,{x:number;y:number}>; handledCaptureIds?: string[] };
export type CloudTour = { id: string; owner_id: string; title: string; slug: string; document: TourDocument; revision: number; published_revision: number | null; updated_at: string };
export const DEFAULT_VIEW: View = { yaw: 0, pitch: 0, fov: 80 };
export function emptyTour(title: string): TourDocument { return { version: 1, title, description: "", defaultSceneId: "", scenes: [] }; }
export function percentToAngles(x: number, y: number) { return { yaw: x * 3.6 - 180, pitch: 90 - y * 1.8 }; }
export function normalizeYaw(yaw: number) { return ((yaw + 180) % 360 + 360) % 360 - 180; }
export function safeUrl(url: string, contact = false) {
  try { return (contact ? ["https:", "http:", "mailto:", "tel:"] : ["https:", "http:"]).includes(new URL(url).protocol); } catch { return false; }
}
export function validateDocument(value: unknown, publish = false): string[] {
  const d = value as TourDocument;
  if (!d || d.version !== 1 || typeof d.title !== "string" || !d.title.trim() || d.title.length > 160 || typeof d.description !== "string" || typeof d.defaultSceneId!=="string" || !Array.isArray(d.scenes) || d.scenes.length > 100) return ["Invalid tour document."];
  const errors: string[] = [], ids = new Set<string>(), pointIds = new Set<string>();
  if (d.branding !== undefined && (!d.branding || !/^#[a-fA-F0-9]{6}$/.test(d.branding.accentColor) || !["light","dark"].includes(d.branding.colorScheme) || (d.branding.logoAssetPath !== undefined && typeof d.branding.logoAssetPath !== "string"))) errors.push("Invalid tour branding.");
  if (d.mapPositions !== undefined && (!d.mapPositions || Array.isArray(d.mapPositions) || typeof d.mapPositions !== "object" || Object.values(d.mapPositions).some(p=>!p||!Number.isFinite(p.x)||!Number.isFinite(p.y)||p.x<0||p.x>1||p.y<0||p.y>1))) errors.push("Invalid room map positions.");
  if (d.handledCaptureIds !== undefined && (!Array.isArray(d.handledCaptureIds)||d.handledCaptureIds.length>2000||d.handledCaptureIds.some(id=>typeof id!=="string"))) errors.push("Invalid capture history.");
  for (const s of d.scenes) {
    if (!s || typeof s.id !== "string" || ids.has(s.id) || typeof s.name !== "string" || !s.name.trim() || typeof s.assetPath !== "string" || !Array.isArray(s.points) || s.points.length > 100) return ["Invalid or duplicate scene."];
    ids.add(s.id);
    const v = s.initialView;
    if (!v || !Number.isFinite(v.yaw) || !Number.isFinite(v.pitch) || Math.abs(v.pitch) > 89 || !Number.isFinite(v.fov) || v.fov < 30 || v.fov > 110) errors.push(`Invalid starting view: ${s.name}`);
    if (publish && !s.assetPath) errors.push(`Missing panorama: ${s.name}`);
    for (const p of s.points) {
      if (!p || typeof p.id !== "string" || pointIds.has(p.id) || typeof p.label !== "string" || !p.label.trim() || typeof p.description !== "string" || typeof p.enabled !== "boolean" || !["scene","info","image","video","external","booking","menu","contact","product"].includes(p.kind) || !Number.isFinite(p.yaw) || !Number.isFinite(p.pitch) || Math.abs(p.pitch) > 90) return ["Invalid hotspot."];
      pointIds.add(p.id);
      if (p.assetPath !== undefined && (typeof p.assetPath !== "string" || !["image","video"].includes(p.kind))) errors.push(`Invalid media: ${p.label}`);
      if (p.url && !safeUrl(p.url, p.kind === "contact")) errors.push(`Unsafe link: ${p.label}`);
      if (publish && p.enabled && ["image","video","external","booking","menu","contact"].includes(p.kind) && !p.url && !p.assetPath) errors.push(`Missing link: ${p.label}`);
    }
  }
  if (d.scenes.length && !ids.has(d.defaultSceneId)) errors.push("Choose a starting room.");
  if (publish && !d.scenes.length) errors.push("Add at least one room.");
  for (const s of d.scenes) for (const p of s.points) if (p.enabled && p.kind === "scene" && (!p.targetSceneId || !ids.has(p.targetSceneId))) errors.push(`Broken room link: ${p.label}`);
  return errors;
}
export function documentAssets(d: TourDocument): string[] {
  return [...new Set([d.branding?.logoAssetPath,...d.scenes.flatMap(s=>[s.assetPath,...s.points.map(p=>p.assetPath)])].filter((p):p is string=>!!p))];
}
export function unreachableScenes(d: TourDocument): Scene[] {
  const reached = new Set<string>(), pending = [d.defaultSceneId];
  while (pending.length) { const id = pending.pop()!; if (reached.has(id)) continue; reached.add(id); const s = d.scenes.find(s => s.id === id); s?.points.filter(p => p.enabled && p.kind === "scene").forEach(p => pending.push(p.targetSceneId ?? "")); }
  return d.scenes.filter(s => !reached.has(s.id));
}
export function removeScene(d: TourDocument, id: string): TourDocument {
  const scenes = d.scenes.filter(s => s.id !== id).map(s => ({ ...s, points: s.points.filter(p => p.targetSceneId !== id) }));
  const mapPositions={...d.mapPositions};delete mapPositions[id];
  return { ...d, scenes, mapPositions, handledCaptureIds:[...new Set([...(d.handledCaptureIds??[]),id])], defaultSceneId: d.defaultSceneId === id ? scenes[0]?.id ?? "" : d.defaultSceneId };
}
