"""Opt-in live storefront checks using ignored test accounts; fixtures end unpublished.

Run with the bundled worker Python. --keep-public retains a disposable fixture for
browser QA; --cleanup unpublishes it afterwards. No existing tours are modified.
"""
import argparse
import io
import json
import uuid
from pathlib import Path

import requests
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--origin", default="http://127.0.0.1:3000")
parser.add_argument("--keep-public", action="store_true")
parser.add_argument("--cleanup", action="store_true")
args = parser.parse_args()
base = args.origin.rstrip("/") + "/api/v1/"
accounts = json.loads((ROOT / ".local/test-accounts.json").read_text())
settings = dict(line.split("=", 1) for line in (ROOT / ".env.local").read_text().splitlines() if "=" in line and not line.startswith("#"))
supabase_url = settings["SUPABASE_URL"].strip('"')
public_key = settings["SUPABASE_PUBLISHABLE_KEY"].strip('"')


def api(path, method="GET", value=None, token=None, expected=200):
    r = requests.request(method, base + path, json=value, headers={"Authorization": "Bearer " + token} if token else {}, timeout=90)
    assert r.status_code == expected, f"{method} {path}: {r.status_code} {r.text[:250]}"
    return r.json()


def login(a):
    return api("auth", "POST", {"action": "login", "email": a["email"], "password": a["password"], "native": True})["access_token"]


token, other = login(accounts[0]), login(accounts[1])
fixture_path = ROOT / ".local/venue-integration.json"


def unpublish_fixture(ids):
    for prefix, key in [("venues/", "venueId"), ("tours/", "tourId")]:
        row = api(prefix + ids[key], token=token)
        api(prefix + ids[key] + "/publish", "POST", {"revision": row["revision"], "action": "unpublish"}, token)


if args.cleanup:
    unpublish_fixture(json.loads(fixture_path.read_text()))
    print("PASS disposable storefront and tour unpublished", flush=True)
    raise SystemExit()

ids = {}
success = False
try:
    api("venues", expected=401)
    suffix = uuid.uuid4().hex[:10]
    doc = {"version": 1, "name": "Portal Connected Cafe", "type": "Restaurant / Cafe", "description": "Step inside our neighbourhood cafe. Explore the space, find our opening hours and plan your visit.", "address": "Dublin, Ireland", "phone": "+353 1 555 0100", "email": "hello@example.com", "bookingUrl": "https://example.com/book", "menuUrl": "https://example.com/menu", "themeId": "opal-glass", "openingHours": [{"day": day, "open": "09:00", "close": "17:00", "closed": False} for day in ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]], "buttons": [{"id": "tour", "type": "tour", "label": "Enter 360° tour", "icon": "route", "order": 0, "isPrimary": True, "isEnabled": True}, {"id": "book", "type": "booking", "label": "Book a table", "icon": "calendar", "order": 1, "isPrimary": False, "isEnabled": True}, {"id": "menu", "type": "menu", "label": "View menu", "icon": "menu", "order": 2, "isPrimary": False, "isEnabled": True}, {"id": "directions", "type": "directions", "label": "Directions", "icon": "map-pin", "order": 3, "isPrimary": False, "isEnabled": True}]}
    slug = "venue-check-" + suffix
    v = api("venues", "POST", {"revision": 0, "slug": slug, "document": doc}, token, 201)
    ids["venueId"], ids["slug"] = v["id"], slug
    path = "venues/" + v["id"]

    def save(document, tour_id=None, expected=200, revision=None):
        return api(path, "PUT", {"revision": v["revision"] if revision is None else revision, "slug": slug, "document": document, "mainTourId": tour_id}, token, expected)

    def publish():
        return api(path + "/publish", "POST", {"revision": v["revision"], "action": "publish"}, token)

    api(path, token=other, expected=404)
    api(path + "/preview", token=other, expected=404)
    api(path + "/publish", "POST", {"revision": 1, "action": "publish"}, other, 404)
    api("public/venues/" + slug, expected=404)
    assert all(row["id"] != v["id"] for row in api("venues", token=other))
    api("venues", "POST", {"revision": 0, "slug": slug, "document": doc}, token, 409)
    assert requests.patch(supabase_url + "/rest/v1/portal_venues?id=eq." + v["id"], headers={"apikey": public_key, "Authorization": "Bearer " + token}, json={"owner_id": str(uuid.uuid4())}, timeout=30).status_code >= 400
    foreign_tours = api("tours", token=other)
    foreign_tour = foreign_tours[0] if foreign_tours else api("tours", "POST", {"title": "Private ownership verification"}, other, 201)
    save(doc, foreign_tour["id"], expected=404)
    direct = requests.post(supabase_url + "/rest/v1/rpc/portal_save_venue", headers={"apikey": public_key, "Authorization": "Bearer " + token}, json={"p_id": v["id"], "p_revision": v["revision"], "p_slug": slug, "p_document": doc, "p_tour": foreign_tour["id"]}, timeout=30)
    assert direct.status_code == 403
    save({**doc, "bookingUrl": "javascript:alert(1)"}, expected=400)
    print("PASS auth, account isolation, ownership, unique slugs and unsafe links", flush=True)

    tour = api("tours", "POST", {"title": "Storefront verification tour " + suffix}, token, 201)
    ids["tourId"], ids["tourSlug"] = tour["id"], tour["slug"]
    panorama_path = ROOT / "public/panoramas/v-eba9d80f-360-panorama.jpg"
    panorama = panorama_path.read_bytes()
    signed = api("media", "POST", {"tourId": tour["id"], "mimeType": "image/jpeg", "size": len(panorama)}, token)
    r = requests.put(signed["uploadUrl"], data=panorama, headers={"Content-Type": "image/jpeg"}, timeout=90)
    assert r.ok, "Panorama upload failed"
    tour_doc = {"version": 1, "title": "Portal Connected Cafe", "description": "A real captured panorama", "defaultSceneId": "room", "scenes": [{"id": "room", "name": "Main room", "assetPath": signed["path"], "initialView": {"yaw": 0, "pitch": 0, "fov": 80}, "points": []}]}
    tour = api("tours/" + tour["id"], "PUT", {"revision": tour["revision"], "document": tour_doc}, token)
    v = save(doc, tour["id"])
    publish()
    assert api("public/venues/" + slug)["tourSlug"] is None
    assert requests.get(args.origin + "/v/" + slug + "/tour", timeout=90).status_code == 404
    api("tours/" + tour["id"] + "/publish", "POST", {"revision": tour["revision"]}, token)
    assert api("public/venues/" + slug)["tourSlug"] == tour["slug"]
    print("PASS separate publishing and automatic published-tour availability", flush=True)

    api(path + "/media", "POST", {"mimeType": "image/png", "size": 10485761}, token, 400)
    api(path + "/media", "POST", {"mimeType": "video/mp4", "size": 100}, token, 400)
    api(path + "/media", "POST", {"mimeType": "image/png", "size": 100}, other, 404)

    def image_upload(colour=None):
        image = Image.open(ROOT / "public/venues/bloom-dining-room.jpeg") if colour is None else Image.new("RGB", (96, 96), colour)
        image.thumbnail((1200, 800))
        buffer = io.BytesIO()
        image.save(buffer, "WEBP", quality=85)
        data = buffer.getvalue()
        ticket = api(path + "/media", "POST", {"mimeType": "image/webp", "size": len(data)}, token)
        assert requests.put(ticket["uploadUrl"], headers={"Content-Type": "image/webp"}, data=data, timeout=60).ok
        return ticket["path"]

    def sign(asset):
        return requests.post(supabase_url + "/storage/v1/object/sign/portal-venue-media/" + asset, headers={"apikey": public_key}, json={"expiresIn": 60}, timeout=30)

    cover, logo = image_upload(), image_upload("#49bdf2")
    assert sign(cover).status_code != 200
    doc = {**doc, "coverAssetPath": cover, "logoAssetPath": logo}
    old_revision = v["revision"]
    v = save(doc, tour["id"])
    save(doc, tour["id"], revision=old_revision, expected=409)
    api(path + "/publish", "POST", {"revision": old_revision, "action": "publish"}, token, 409)
    assert api(path + "/preview", token=token)["coverImageUrl"]
    assert "coverImageUrl" not in api("public/venues/" + slug)
    v = publish()
    public = api("public/venues/" + slug)
    assert requests.get(public["coverImageUrl"], timeout=30).ok
    assert sign(cover).status_code == 200 and sign(logo).status_code == 200
    print("PASS private image upload, preview, publication and stale revision rejection", flush=True)

    newer = image_upload("#ffffff")
    changed = {**doc, "name": "Private changed name", "coverAssetPath": newer}
    v = save(changed, None)
    public = api("public/venues/" + slug)
    assert public["document"]["name"] == doc["name"] and public["tourSlug"] == tour["slug"]
    assert sign(newer).status_code != 200 and sign(cover).status_code == 200
    v = publish()
    assert api("public/venues/" + slug)["document"]["name"] == changed["name"]
    assert api("public/venues/" + slug)["tourSlug"] is None
    assert sign(newer).status_code == 200 and sign(cover).status_code != 200
    v = save(doc, tour["id"])
    v = publish()
    api("tours/" + tour["id"] + "/publish", "POST", {"revision": tour["revision"], "action": "unpublish"}, token)
    assert api("public/venues/" + slug)["tourSlug"] is None
    assert requests.get(args.origin + "/v/" + slug + "/tour", timeout=90).status_code == 404
    api("tours/" + tour["id"] + "/publish", "POST", {"revision": tour["revision"]}, token)
    changed_tour = {**tour_doc, "title": "Private tour draft"}
    tour = api("tours/" + tour["id"], "PUT", {"revision": tour["revision"], "document": changed_tour}, token)
    assert api("public/" + tour["slug"])["document"]["title"] == tour_doc["title"]
    print("PASS draft isolation for venue, images, tour selection and Android-compatible tour revisions", flush=True)

    for route in ["/v/" + slug, "/v/" + slug + "/tour", "/v/" + tour["slug"] + "/tour", "/demo/v/bloom-cafe"]:
        assert requests.get(args.origin + route, timeout=90).status_code == 200, route
    api(path + "/publish", "POST", {"revision": v["revision"], "action": "unpublish"}, token)
    api("public/venues/" + slug, expected=404)
    assert sign(cover).status_code != 200
    assert requests.get(args.origin + "/v/" + slug, timeout=90).status_code == 404
    v = publish()
    fixture_path.write_text(json.dumps(ids, indent=2))
    print("PASS storefront and direct-tour routes, explicit demos, unpublishing and media revocation", flush=True)
    success = True
finally:
    if ids.get("venueId") and not (args.keep_public and success):
        if ids.get("tourId"):
            unpublish_fixture(ids)
        else:
            row = api("venues/" + ids["venueId"], token=token)
            api("venues/" + row["id"] + "/publish", "POST", {"revision": row["revision"], "action": "unpublish"}, token)
        print("Disposable verification records left unpublished", flush=True)
