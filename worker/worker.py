"""Durable Supabase queue consumer. Run separately from the Next.js web process."""
import hashlib
import logging
import os
import tempfile
import threading
import time
import uuid
from datetime import datetime,timedelta,timezone
from pathlib import Path
import requests
from stitch import stitch,RetakeRequired

logging.basicConfig(level=logging.INFO,format="%(asctime)s %(levelname)s %(message)s")
URL=os.environ["SUPABASE_URL"].rstrip("/")
KEY=os.environ.get("SUPABASE_SERVICE_ROLE_KEY") or os.environ["SUPABASE_PUBLISHABLE_KEY"]
HEADERS={"apikey":KEY}
AUTH_LOCK=threading.Lock()
TOKEN_EXPIRES=0.
WORKER=str(uuid.uuid4())

def authenticate():
    global TOKEN_EXPIRES
    with AUTH_LOCK:
        if time.time()<TOKEN_EXPIRES:return
        if os.environ.get("SUPABASE_SERVICE_ROLE_KEY"):
            HEADERS["Authorization"]=f"Bearer {KEY}";TOKEN_EXPIRES=time.time()+3600;return
        response=requests.post(URL+"/auth/v1/token?grant_type=password",headers={"apikey":KEY},json={"email":os.environ["PORTAL_WORKER_EMAIL"],"password":os.environ["PORTAL_WORKER_PASSWORD"]},timeout=30)
        response.raise_for_status();data=response.json();HEADERS["Authorization"]="Bearer "+data["access_token"];TOKEN_EXPIRES=time.time()+data["expires_in"]-120

def request(method,path,**kwargs):
    authenticate()
    response=requests.request(method,URL+path,headers={**HEADERS,**kwargs.pop("headers",{})},timeout=90,**kwargs)
    response.raise_for_status()
    return response

def patch(job,values):
    return request("PATCH",f'/rest/v1/portal_captures?id=eq.{job["id"]}&worker_id=eq.{WORKER}&status=eq.processing',json=values,headers={"Prefer":"return=representation"}).json()

def process(job):
    stop=threading.Event();alive=threading.Event();alive.set()
    def heartbeat():
        while not stop.wait(25):
            try:
                rows=patch(job,{"lease_until":(datetime.now(timezone.utc)+timedelta(minutes=2)).isoformat()})
                if not rows:alive.clear();return
            except requests.RequestException:
                alive.clear();return
    thread=threading.Thread(target=heartbeat,daemon=True);thread.start();started=time.monotonic()
    try:
        with tempfile.TemporaryDirectory(prefix="portal-stitch-") as temp:
            folder=Path(temp)
            for frame in job["manifest"]["frames"]:
                if not alive.is_set():return
                expected=f'{job["owner_id"]}/{job["tour_id"]}/captures/{job["id"]}/{frame["id"]}.jpg'
                if frame["path"] != expected or "/" in frame["id"] or ".." in frame["id"]:
                    raise ValueError("Invalid capture asset path.")
                response=request("GET",f'/storage/v1/object/portal-media/{frame["path"]}')
                if hashlib.sha256(response.content).hexdigest()!=frame["sha256"]:raise ValueError("Uploaded photo checksum mismatch.")
                (folder/f'{frame["id"]}.jpg').write_bytes(response.content)
            quality=stitch(job["manifest"],folder,folder/"panorama.jpg")
            if not alive.is_set():return
            prefix=f'{job["owner_id"]}/{job["tour_id"]}/processed/{job["id"]}/{WORKER}'
            for name in ["panorama.jpg","preview.jpg"]:
                request("POST",f'/storage/v1/object/portal-media/{prefix}/{name}',data=(folder/name).read_bytes(),headers={"Content-Type":"image/jpeg"})
            patch(job,{"status":"ready","result":{"path":prefix+"/panorama.jpg","previewPath":prefix+"/preview.jpg","quality":quality},"lease_until":None,"error":None,"updated_at":datetime.now(timezone.utc).isoformat()})
            logging.info("capture=%s ready duration=%.1fs",job["id"],time.monotonic()-started)
    except RetakeRequired as e:
        patch(job,{"status":"needs-retake","error":str(e),"lease_until":None})
    except Exception as e:
        detail = e.response.text[:250] if isinstance(e, requests.HTTPError) and e.response is not None else str(e)[:250]
        logging.error("capture=%s failed type=%s detail=%s",job["id"],type(e).__name__,detail)
        patch(job,{"status":"failed" if job["attempts"]>=3 else "queued","error":"Processing failed; retrying." if job["attempts"]<3 else "Processing failed after three attempts. Retry the capture.","lease_until":None})
    finally:
        stop.set();thread.join(timeout=2)

if __name__=="__main__":
    logging.info("Portal stitch worker started")
    while True:
        try:
            job=request("POST","/rest/v1/rpc/portal_claim_capture",json={"p_worker":WORKER}).json()
            if job:process(job)
            else:time.sleep(5)
        except KeyboardInterrupt:break
        except requests.RequestException as e:
            logging.error("Queue request failed: %s",type(e).__name__);time.sleep(10)
