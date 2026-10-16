import { after } from "next/server";

type CaptureState = { status: string; lease_until?: string | null };

export function needsCloudWorker(capture: CaptureState): boolean {
  return capture.status === "queued" || (capture.status === "processing" && !!capture.lease_until && Date.parse(capture.lease_until) < Date.now());
}

/** Original photos stay in private Storage; only our server can invoke the worker. */
export function scheduleCloudWorker(captures: CaptureState[]) {
  const url = process.env.PORTAL_WORKER_URL, secret = process.env.PORTAL_WORKER_SECRET;
  if (!url || !secret || !captures.some(needsCloudWorker)) return;
  after(async () => {
    try {
      const response = await fetch(url, {
        method: "POST", headers: { Authorization: `Bearer ${secret}` },
        cache: "no-store", signal: AbortSignal.timeout(285_000),
      });
      if (!response.ok) console.warn("Cloud stitching will retry through the durable queue.");
      await response.body?.cancel();
    } catch {
      console.warn("Cloud stitching is temporarily unavailable; the capture remains in its durable queue.");
    }
  });
}
