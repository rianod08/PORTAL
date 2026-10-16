"use client";

import { useEffect } from "react";

/** Supabase's Site URL may be the homepage; recovery belongs on the login screen. */
export function AccountLinkRedirect() {
  useEffect(() => {
    const parameters = new URLSearchParams(window.location.hash.slice(1));
    if (window.location.pathname === "/login" || !parameters.has("access_token")) return;
    if (parameters.get("type") === "recovery") window.location.replace(`/login${window.location.hash}`);
    else if (parameters.get("type") === "signup") window.location.replace("/login");
  }, []);
  return null;
}
