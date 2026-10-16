"""Local end-to-end test; credentials are read from ignored .local/test-accounts.json."""
import base64
import json
import sys
import time
from pathlib import Path
import requests
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/"worker"))
from test_stitch import fixture

BASE="http://localhost:3000/api/v1/"
accounts=json.loads((ROOT/".local/test-accounts.json").read_text())
def api(path,method="GET",value=None,token=None,expected=200):
    response=requests.request(method,BASE+path,json=value,headers={"Authorization":"Bearer "+token} if token else {},timeout=90)
    if response.status_code!=expected:raise AssertionError(f"{method} {path}: {response.status_code}: {response.text[:300]}")
    return response.json()
def login(account):return api("auth","POST",{"action":"login","email":account["email"],"password":account["password"],"native":True})["access_token"]
def upload(signed,data,interrupt=False):
    metadata=','.join(k+' '+base64.b64encode(v.encode()).decode() for k,v in {"bucketName":"portal-media","objectName":signed["path"],"contentType":"image/jpeg"}.items())
    headers={"Tus-Resumable":"1.0.0","x-signature":signed["token"],"Authorization":"Bearer "+signed["uploadAuthorization"]}
    response=requests.post(signed["storageUrl"]+"/storage/v1/upload/resumable",headers={**headers,"Upload-Length":str(len(data)),"Upload-Metadata":metadata},timeout=60)
    assert response.status_code==201,(response.status_code,response.text)
    url=response.headers["Location"]
    offset=0
    # TUS requires 6MB chunks except the last chunk. A HEAD round trip tests resume state.
    if interrupt:
        head=requests.head(url,headers=headers,timeout=60);assert int(head.headers["Upload-Offset"])==0
    while offset<len(data):
        response=requests.patch(url,data=data[offset:offset+6*1024*1024],headers={**headers,"Upload-Offset":str(offset),"Content-Type":"application/offset+octet-stream"},timeout=90)
        assert response.ok,(response.status_code,response.text);offset=int(response.headers["Upload-Offset"])

if __name__=="__main__":
    import uuid
    token=login(accounts[0]);other=login(accounts[1]);print("PASS real login",flush=True)
    api("tours",expected=401)
    tour=api("tours","POST",{"title":"Portal capture integration"},token,201)
    api("tours/"+tour["id"],token=other,expected=404)
    print("PASS unauthenticated and cross-account isolation",flush=True)
    folder=ROOT/".local/fixture";folder.mkdir(parents=True,exist_ok=True)
    manifest=fixture(folder)
    for f in manifest["frames"]:
        new=str(uuid.uuid4());(folder/(f["id"]+".jpg")).rename(folder/(new+".jpg"));f["id"]=new
    capture_id=str(uuid.uuid4());payload={"id":capture_id,"tourId":tour["id"],"name":"Captured room","frames":manifest["frames"]}
    capture=api("captures","POST",payload,token,201)
    api("captures","POST",payload,token)
    api("captures/"+capture_id,"POST",{"action":"complete"},token,400)
    print("PASS idempotent capture registration and incomplete-upload rejection",flush=True)
    for index,f in enumerate(manifest["frames"]):
        signed=api("captures/"+capture_id,"POST",{"action":"upload","frameId":f["id"]},token)
        upload(signed,(folder/(f["id"]+".jpg")).read_bytes(),index==0)
    capture=api("captures/"+capture_id,"POST",{"action":"complete"},token)
    print("PASS calibrated capture uploaded; waiting for worker",flush=True)
    (ROOT/".local/last-integration.json").write_text(json.dumps({"tourId":tour["id"],"captureId":capture_id,"slug":tour["slug"]}))
    deadline=time.time()+240
    while time.time()<deadline:
        capture=next(c for c in api("captures",token=token) if c["id"]==capture_id)
        if capture["status"] in ["ready","failed","needs-retake"]:break
        time.sleep(3)
    assert capture["status"]=="ready",capture
    print("PASS worker produced checked panorama",flush=True)
    asset=capture["result"]["path"]
    document={"version":1,"title":"Portal capture integration","description":"End-to-end validation","defaultSceneId":"entrance","scenes":[{"id":"entrance","name":"Entrance","assetPath":asset,"initialView":{"yaw":0,"pitch":0,"fov":80},"points":[{"id":"door","kind":"scene","label":"Go to dining","description":"","yaw":30,"pitch":0,"targetSceneId":"dining","enabled":True}]},{"id":"dining","name":"Dining room","assetPath":asset,"initialView":{"yaw":90,"pitch":0,"fov":80},"points":[]}]}
    saved=api("tours/"+tour["id"],"PUT",{"revision":1,"document":document},token)
    api("tours/"+tour["id"],"PUT",{"revision":1,"document":document},token,409)
    api("tours/"+tour["id"]+"/publish","POST",{"revision":saved["revision"]},token)
    public=api("public/"+tour["slug"])
    assert requests.get(public["document"]["scenes"][0]["imageUrl"],timeout=60).ok
    document["title"]="Unpublished draft change"
    saved=api("tours/"+tour["id"],"PUT",{"revision":saved["revision"],"document":document},token)
    assert api("public/"+tour["slug"])["document"]["title"]=="Portal capture integration"
    api("tours/"+tour["id"]+"/publish","POST",{"revision":saved["revision"],"action":"unpublish"},token)
    api("public/"+tour["slug"],expected=404)
    print("PASS revision conflicts, public media, draft isolation, and unpublish",flush=True)
    print("ALL END-TO-END CHECKS PASSED",flush=True)
