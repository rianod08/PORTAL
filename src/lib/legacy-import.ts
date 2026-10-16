import { percentToAngles, DEFAULT_VIEW, TourDocument, TourPoint } from "./tour-contract";

type LegacyStore = {
  activeBusinessId: string;
  businesses: { id: string; name: string }[];
  tours: { id: string; businessId: string; title: string; description?: string; defaultSceneId?: string }[];
  scenes: { id: string; tourId: string; name: string; imageUrl: string; order: number }[];
  hotspots: { id: string; sceneId: string; label: string; type: string; x: number; y: number; description?: string; targetUrl?: string; targetSceneId?: string; isEnabled: boolean }[];
};
export function legacyTours(raw: string | null): { title: string; sceneCount: number; source: LegacyStore; tourId: string }[] {
  if (!raw) return [];
  try {
    const source = JSON.parse(raw) as LegacyStore;
    if (!Array.isArray(source.tours) || !Array.isArray(source.scenes) || !Array.isArray(source.businesses) || !Array.isArray(source.hotspots)) return [];
    return source.tours.filter(t => source.businesses.some(b => b.id === t.businessId)).map(t => ({ title: t.title, sceneCount: source.scenes.filter(s => s.tourId === t.id && !s.imageUrl.startsWith("gradient:")).length, source, tourId: t.id }));
  } catch { return []; }
}
const types: Record<string,TourPoint["kind"]> = { scene:"scene",info:"info",image:"image",video:"video",external:"external",booking:"booking",menu:"menu",contact:"contact",product:"product" };
export function legacyDocument(source: LegacyStore, tourId: string, assets: Record<string,string>): TourDocument {
  const tour = source.tours.find(t => t.id === tourId);
  if (!tour) throw new Error("The old tour could not be found.");
  const scenes = source.scenes.filter(s => s.tourId === tourId && assets[s.id]).sort((a,b)=>a.order-b.order);
  const ids = new Set(scenes.map(s=>s.id));
  return {
    version:1,title:tour.title,description:tour.description??"",defaultSceneId:tour.defaultSceneId && ids.has(tour.defaultSceneId) ? tour.defaultSceneId : scenes[0]?.id??"",
    scenes:scenes.map(s=>({id:s.id,name:s.name,assetPath:assets[s.id],initialView:{...DEFAULT_VIEW},points:source.hotspots.filter(p=>p.sceneId===s.id).map(p=>({
      id:p.id,label:p.label,kind:types[p.type]??"info",...percentToAngles(p.x,p.y),description:p.description??"",url:p.targetUrl,targetSceneId:p.targetSceneId,enabled:p.isEnabled && (p.type!=="scene" || ids.has(p.targetSceneId??"")),
    }))})),
  };
}
