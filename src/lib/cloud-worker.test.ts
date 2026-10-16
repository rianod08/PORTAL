import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
const deferred = vi.hoisted(() => [] as Array<() => Promise<void>>);
vi.mock("next/server", () => ({ after: (callback: () => Promise<void>) => deferred.push(callback) }));
import { needsCloudWorker, scheduleCloudWorker } from "./cloud-worker";

describe("on-demand cloud stitching", () => {
  beforeEach(() => { deferred.length = 0; vi.stubEnv("PORTAL_WORKER_URL", "https://worker.example/api/stitch"); vi.stubEnv("PORTAL_WORKER_SECRET", "test-secret"); });
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
  it("restarts expired leases but does not duplicate a live stitch", () => {
    expect(needsCloudWorker({ status: "queued" })).toBe(true);
    expect(needsCloudWorker({ status: "processing", lease_until: new Date(Date.now() - 60_000).toISOString() })).toBe(true);
    expect(needsCloudWorker({ status: "processing", lease_until: new Date(Date.now() + 60_000).toISOString() })).toBe(false);
    for (const status of ["ready", "failed", "needs-retake", "cancelled", "uploading"]) expect(needsCloudWorker({ status })).toBe(false);
  });
  it("keeps local deployments usable without cloud-worker credentials", () => {
    vi.stubEnv("PORTAL_WORKER_SECRET", ""); scheduleCloudWorker([{ status: "queued" }]); expect(deferred).toHaveLength(0);
  });
  it("defers one protected invocation for a batch of pending captures", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response("{}")); vi.stubGlobal("fetch", fetch);
    scheduleCloudWorker([{ status: "queued" }, { status: "queued" }]);
    expect(fetch).not.toHaveBeenCalled(); expect(deferred).toHaveLength(1);
    await deferred[0]();
    expect(fetch).toHaveBeenCalledWith("https://worker.example/api/stitch", expect.objectContaining({ method: "POST", headers: { Authorization: "Bearer test-secret" } }));
  });
  it("leaves durable recovery to polling when the host is unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("unavailable"))); vi.spyOn(console, "warn").mockImplementation(() => {});
    scheduleCloudWorker([{ status: "queued" }]); await expect(deferred[0]()).resolves.toBeUndefined();
  });
});
