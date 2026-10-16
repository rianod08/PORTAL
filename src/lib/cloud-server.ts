import { cookies } from "next/headers";
export class ApiError extends Error { constructor(message: string, public status = 400) { super(message); } }
export function config() {
  const url = process.env.SUPABASE_URL, key = process.env.SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) throw new ApiError("Portal cloud is not configured. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY.", 503);
  return { url, key };
}
export async function supa(path: string, token?: string, init: RequestInit = {}) {
  const { url, key } = config();
  const response = await fetch(`${url}${path}`, { ...init, cache: "no-store", headers: { apikey: key, ...(token ? { Authorization: `Bearer ${token}` } : {}), "Content-Type": "application/json", ...init.headers } });
  const body = await response.text(); let data; try { data = body ? JSON.parse(body) : null; } catch { data = null; }
  if (!response.ok) throw new ApiError(data?.msg ?? data?.message ?? data?.error_description ?? "Cloud request failed.", response.status);
  return data;
}
export async function session(request: Request): Promise<{ token: string; user: { id: string; email: string } }> {
  const jar = await cookies();
  let token = request.headers.get("authorization")?.replace(/^Bearer /i, "") || jar.get("portal-access")?.value;
  if (!token) throw new ApiError("Please sign in.", 401);
  try { const user = await supa("/auth/v1/user", token); return { token, user }; }
  catch (e) {
    const expiredJwt = e instanceof ApiError && e.status === 403 && /token is expired/i.test(e.message);
    if (!(e instanceof ApiError) || (e.status !== 401 && !expiredJwt) || request.headers.has("authorization")) throw e;
    const refresh = jar.get("portal-refresh")?.value;
    if (!refresh) throw new ApiError("Please sign in again.", 401);
    const next = await supa("/auth/v1/token?grant_type=refresh_token", undefined, { method: "POST", body: JSON.stringify({ refresh_token: refresh }) });
    await setSession(next); token = next.access_token; return { token: token!, user: next.user };
  }
}
export async function setSession(data: { access_token: string; refresh_token: string }) {
  const loopbackHttp = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/?$/i.test(process.env.PORTAL_ORIGIN ?? "");
  const jar = await cookies(), options = { httpOnly: true, secure: process.env.NODE_ENV === "production" && !loopbackHttp, sameSite: "lax" as const, path: "/", maxAge: 60 * 60 * 24 * 30 };
  jar.set("portal-access", data.access_token, options); jar.set("portal-refresh", data.refresh_token, options);
}
export function guardOrigin(request: Request) {
  const origin = request.headers.get("origin");
  if (origin && origin !== new URL(request.url).origin && origin !== process.env.PORTAL_ORIGIN) throw new ApiError("Origin is not allowed.", 403);
}
export async function body(request: Request) {
  const raw = await request.text(); if (raw.length > 2_000_000) throw new ApiError("Request is too large.", 413);
  try { return JSON.parse(raw); } catch { throw new ApiError("Invalid JSON."); }
}
export function failure(error: unknown) { return Response.json({ error: error instanceof Error ? error.message : "Request failed." }, { status: error instanceof ApiError ? error.status : 500 }); }
export function id(value: string) { if (!/^[a-zA-Z0-9_-]{1,120}$/.test(value)) throw new ApiError("Invalid identifier."); return value; }
export async function ownedTour(tourId: string, token: string) {
  const rows = await supa(`/rest/v1/portal_tours?id=eq.${id(tourId)}&select=*`, token);
  if (!rows?.length) throw new ApiError("Tour not found.", 404); return rows[0];
}
