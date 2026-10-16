import { cookies } from "next/headers";
import { body, failure, guardOrigin, session, setSession, supa, ApiError } from "@/lib/cloud-server";
export async function GET(request: Request) { try { return Response.json((await session(request)).user); } catch(e) { return failure(e); } }
export async function POST(request: Request) {
  try {
    guardOrigin(request); const input = await body(request);
    if (input.action === "logout") { const jar = await cookies(); const token = jar.get("portal-access")?.value; if (token) await supa("/auth/v1/logout", token, { method: "POST" }).catch(() => null); jar.delete("portal-access"); jar.delete("portal-refresh"); return Response.json({ ok: true }); }
    if (input.action === "password") { const { token } = await session(request); if (typeof input.password !== "string" || input.password.length < 10) throw new ApiError("Use at least 10 characters."); await supa("/auth/v1/user", token, { method: "PUT", body: JSON.stringify({ password: input.password }) }); return Response.json({ message: "Password updated." }); }
    if (input.action === "refresh") { if (typeof input.refresh_token !== "string") throw new ApiError("Missing refresh token."); const data = await supa("/auth/v1/token?grant_type=refresh_token", undefined, { method: "POST", body: JSON.stringify({ refresh_token: input.refresh_token }) }); await setSession(data); return Response.json(data); }
    if (typeof input.email !== "string" || !input.email.includes("@")) throw new ApiError("Enter your email address.");
    if (input.action === "recover") { await supa("/auth/v1/recover", undefined, { method: "POST", body: JSON.stringify({ email: input.email }) }); return Response.json({ message: "If the account exists, a recovery email has been sent." }); }
    if (!["login","signup"].includes(input.action) || typeof input.password !== "string" || input.password.length < (input.action === "signup" ? 10 : 1)) throw new ApiError("Enter a valid password (at least 10 characters for a new account).");
    const data = await supa(input.action === "signup" ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password", undefined, { method: "POST", body: JSON.stringify({ email: input.email, password: input.password }) });
    if (data.access_token) await setSession(data);
    return Response.json(input.native ? data : { user: data.user, message: data.access_token ? "Signed in" : "Check your email to confirm your account." });
  } catch(e) { return failure(e); }
}
