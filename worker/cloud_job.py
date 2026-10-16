"""Process one durable queue job within an on-demand function invocation."""
import json
import os
import time

os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")

import cv2
import worker as engine

cv2.setNumThreads(1)
started = time.monotonic()
job = engine.request("POST", "/rest/v1/rpc/portal_claim_capture", json={"p_worker": engine.WORKER}).json()
if job:
    engine.process(job)
    rows = engine.request("GET", f'/rest/v1/portal_captures?id=eq.{job["id"]}&select=status').json()
    print(json.dumps({"status": rows[0]["status"] if rows else "processed", "durationSeconds": round(time.monotonic() - started, 2)}))
else:
    print(json.dumps({"status": "idle"}))
