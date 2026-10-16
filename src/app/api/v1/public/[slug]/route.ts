import { config, failure, supa, ApiError } from "@/lib/cloud-server";
import type { Scene } from "@/lib/tour-contract";
import { documentAssets, validateDocument } from "@/lib/tour-contract";
export async function GET(_request: Request, context: { params: Promise<{ slug: string }> }) {
  try { const { slug } = await context.params; const tour = await supa("/rest/v1/rpc/portal_public_tour", undefined, { method: "POST", body: JSON.stringify({ p_slug: slug }) });
    if (!tour) throw new ApiError("This tour is not published.", 404);
    if (validateDocument(tour.document, true).length) throw new ApiError("This tour needs to be repaired by its owner.", 422);
    for(const s of tour.document.scenes as Scene[])s.points=s.points.filter(p=>p.enabled);
    const urls = Object.fromEntries(await Promise.all(documentAssets(tour.document).map(async path=>{const signed=await supa(`/storage/v1/object/sign/portal-media/${path}`,undefined,{method:"POST",body:JSON.stringify({expiresIn:3600})});return [path,`${config().url}/storage/v1${signed.signedURL}`];})));
    for(const s of tour.document.scenes as Scene[]){s.imageUrl=urls[s.assetPath];for(const p of s.points)if(p.assetPath)p.mediaUrl=urls[p.assetPath];}
    if(tour.document.branding?.logoAssetPath)tour.document.branding.logoUrl=urls[tour.document.branding.logoAssetPath];
    return Response.json(tour, { headers: { "Cache-Control": "no-store" } });
  } catch(e) { return failure(e); }
}
