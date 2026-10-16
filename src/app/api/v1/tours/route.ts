import { body, failure, guardOrigin, session, supa, ApiError } from "@/lib/cloud-server";
import { emptyTour } from "@/lib/tour-contract";
export async function GET(request: Request) { try { const { token } = await session(request); return Response.json(await supa("/rest/v1/portal_tours?select=*&order=updated_at.desc", token)); } catch(e) { return failure(e); } }
export async function POST(request: Request) {
  try { guardOrigin(request); const { token, user } = await session(request), input = await body(request);
    if (typeof input.title !== "string" || !input.title.trim() || input.title.length > 160) throw new ApiError("Enter a tour title.");
    const tourId = crypto.randomUUID(); const rows = await supa("/rest/v1/portal_tours", token, { method: "POST", headers: { Prefer: "return=representation" }, body: JSON.stringify({ id: tourId, owner_id: user.id, title: input.title.trim(), slug: `tour-${tourId.slice(0,8)}`, document: emptyTour(input.title.trim()) }) }); return Response.json(rows[0], { status: 201 });
  } catch(e) { return failure(e); }
}
