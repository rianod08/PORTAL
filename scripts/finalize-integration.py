"""Complete the previously uploaded local integration capture and verify publishing."""
import json
from pathlib import Path
from importlib.machinery import SourceFileLoader
import requests

root = Path(__file__).resolve().parents[1]
flow = SourceFileLoader("test_capture_flow", str(root / "scripts/test-capture-flow.py")).load_module()
api, accounts, login = flow.api, flow.accounts, flow.login
ids = json.loads((root / ".local/last-integration.json").read_text())
token = login(accounts[0])
other = login(accounts[1])
tour_id, capture_id, slug = ids["tourId"], ids["captureId"], ids["slug"]
capture = next(c for c in api("captures", token=token) if c["id"] == capture_id)
assert capture["status"] == "ready", (capture["status"], capture.get("error"))
assert all(c["id"] != capture_id for c in api("captures", token=other))
asset = capture["result"]["path"]
document = {
    "version": 1,
    "title": "Portal capture integration",
    "description": "End-to-end validation",
    "defaultSceneId": "entrance",
    "scenes": [
        {"id": "entrance", "name": "Entrance", "assetPath": asset,
         "initialView": {"yaw": 0, "pitch": 0, "fov": 80},
         "points": [{"id": "door", "kind": "scene", "label": "Go to dining",
                     "description": "", "yaw": 30, "pitch": 0,
                     "targetSceneId": "dining", "enabled": True}]},
        {"id": "dining", "name": "Dining room", "assetPath": asset,
         "initialView": {"yaw": 90, "pitch": 0, "fov": 80}, "points": []},
    ],
}
tour = api("tours/" + tour_id, token=token)
saved = api("tours/" + tour_id, "PUT", {"revision": tour["revision"], "document": document}, token)
api("tours/" + tour_id, "PUT", {"revision": tour["revision"], "document": document}, token, 409)
api("tours/" + tour_id + "/publish", "POST", {"revision": saved["revision"]}, token)
public = api("public/" + slug)
assert public["document"]["title"] == document["title"]
assert requests.get(public["document"]["scenes"][0]["imageUrl"], timeout=60).ok
document["title"] = "Unpublished draft change"
saved = api("tours/" + tour_id, "PUT", {"revision": saved["revision"], "document": document}, token)
assert api("public/" + slug)["document"]["title"] == "Portal capture integration"
api("tours/" + tour_id + "/publish", "POST", {"revision": saved["revision"], "action": "unpublish"}, token)
api("public/" + slug, expected=404)
print("PASS ready stitched panorama, account isolation, scene links, public image,")
print("PASS revision conflicts, draft isolation, and unpublish")
