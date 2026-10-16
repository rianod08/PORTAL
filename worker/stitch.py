"""Calibrated photo-sphere stitching. No geometry reconstruction or invented pixels."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import cv2
import numpy as np
from scipy.optimize import least_squares
from scipy.spatial.transform import Rotation

class RetakeRequired(ValueError):
    pass

def load_frames(manifest: dict, directory: Path):
    images, cameras, rotations = [], [], []
    for frame in manifest["frames"]:
        path = directory / f'{frame["id"]}.jpg'
        if hashlib.sha256(path.read_bytes()).hexdigest() != frame["sha256"]:
            raise ValueError("Frame checksum mismatch; upload the original image again.")
        image = cv2.imread(str(path))
        if image is None or image.shape[:2] != (frame["height"], frame["width"]):
            raise ValueError("Frame dimensions do not match calibration.")
        fx, fy, cx, cy = frame["intrinsics"]
        if min(fx, fy) <= 0 or not (0 <= cx <= image.shape[1] and 0 <= cy <= image.shape[0]):
            raise ValueError("Invalid camera calibration.")
        pose = np.array(frame["pose"], dtype=np.float64).reshape(4,4,order="F")
        rotation = pose[:3,:3]
        if not np.allclose(rotation.T @ rotation,np.eye(3),atol=.02) or np.linalg.det(rotation) < .98:
            raise ValueError("Invalid camera rotation.")
        scale = min(1., 1600 / max(image.shape[:2]))
        images.append(cv2.resize(image,None,fx=scale,fy=scale) if scale < 1 else image)
        cameras.append(np.array([fx,fy,cx,cy])*scale)
        rotations.append(rotation)
    if len(images) < 8:
        raise RetakeRequired("Not enough photographs. Complete all capture targets.")
    # Translation produces parallax that a rotational panorama cannot remove.
    # Older Android builds flattened pose translations but retained the real
    # shutter position in observedPose. Never let that hide physical parallax.
    positions = np.array([np.array(f.get("observedPose", f["pose"]), dtype=np.float64).reshape(4,4,order="F")[:3,3] for f in manifest["frames"]])
    if not np.isfinite(positions).all():
        raise ValueError("Invalid camera position.")
    baseline = float(np.max(np.linalg.norm(positions[:,None] - positions[None,:],axis=2)))
    if baseline > .25:
        raise RetakeRequired(f"The camera moved too far: {baseline*100:.0f} cm between photos (maximum 25 cm). Keep the lens over one fixed point while turning and tilting, then recapture the room.")
    return images, cameras, rotations

def feature_rays(keypoints, descriptors, camera):
    fx,fy,cx,cy = camera
    xy = np.array([p.pt for p in keypoints])
    if len(xy)==0: return np.empty((0,3))
    rays = np.column_stack(((xy[:,0]-cx)/fx,-(xy[:,1]-cy)/fy,-np.ones(len(xy))))
    return rays / np.linalg.norm(rays,axis=1)[:,None]


def pair_rotation(points_a, points_b, rays_a, rays_b):
    """Reject degenerate matches before fitting a spherical rotation."""
    if len(points_a)<14:
        return None
    _,mask=cv2.findHomography(points_a,points_b,cv2.RANSAC,5.0)
    if mask is None:
        return None
    keep=mask.ravel()!=0
    pa,pb=points_a[keep],points_b[keep]
    ra,rb=rays_a[keep],rays_b[keep]
    if len(ra)<14:
        return None
    # A homography can map many repeated features onto one point or line.
    # Those correspondences do not constrain the camera orientation.
    for points in (pa,pb):
        if len(np.unique(np.round(points,1),axis=0))<14:
            return None
        if np.linalg.eigvalsh(np.cov(points.T))[0]<4:
            return None
    relative,_=Rotation.align_vectors(ra,rb)
    relative=relative.as_matrix()
    angular=np.rad2deg(np.arccos(np.clip(np.sum(ra*(rb@relative.T),axis=1),-1,1)))
    median=float(np.median(angular))
    if median>2.5 or np.percentile(angular,90)>5:
        return None
    return ra[:150],rb[:150],relative,len(ra),median


def rectified_pair(images, cameras, rotations, a, b):
    """Match wide-angle overlap in a shared tangent view, not raw perspectives."""
    forward=-(rotations[a][:,2]+rotations[b][:,2])
    if np.linalg.norm(forward)<.3:
        return None
    forward/=np.linalg.norm(forward)
    up=np.array([0.,1.,0.]) if abs(forward[1])<.9 else np.array([0.,0.,1.])
    right=np.cross(forward,up);right/=np.linalg.norm(right)
    common=np.column_stack((right,np.cross(right,forward),-forward))
    size=800;focal=size*.8
    yy,xx=np.mgrid[:size,:size]
    rays=np.stack(((xx-size/2)/focal,-(yy-size/2)/focal,-np.ones_like(xx)),axis=-1)
    rays/=np.linalg.norm(rays,axis=-1)[:,:,None]
    world=rays@common.T
    pa,ma=warp(images[a],cameras[a],rotations[a],world)
    pb,mb=warp(images[b],cameras[b],rotations[b],world)
    mask=cv2.erode(((ma>0)&(mb>0)).astype(np.uint8)*255,np.ones((21,21),np.uint8))
    if np.count_nonzero(mask)<10000:
        return None
    sift=cv2.SIFT_create(nfeatures=6000,contrastThreshold=.02)
    ka,da=sift.detectAndCompute(cv2.cvtColor(pa,cv2.COLOR_BGR2GRAY),mask)
    kb,db=sift.detectAndCompute(cv2.cvtColor(pb,cv2.COLOR_BGR2GRAY),mask)
    if da is None or db is None or min(len(da),len(db))<16:
        return None
    matcher=cv2.BFMatcher(cv2.NORM_L2)
    backward={m.queryIdx:m.trainIdx for m in matcher.match(db,da)}
    matches=[pair[0] for pair in matcher.knnMatch(da,db,k=2)
             if len(pair)==2 and pair[0].distance<.8*pair[1].distance
             and backward.get(pair[0].trainIdx)==pair[0].queryIdx]
    if len(matches)<16:
        return None
    points_a=np.float32([ka[m.queryIdx].pt for m in matches])
    points_b=np.float32([kb[m.trainIdx].pt for m in matches])
    raw=[]
    for points,rotation in ((points_a,rotations[a]),(points_b,rotations[b])):
        local=np.column_stack(((points[:,0]-size/2)/focal,-(points[:,1]-size/2)/focal,-np.ones(len(points))))
        local/=np.linalg.norm(local,axis=1)[:,None]
        raw.append(local@common.T@rotation)
    return pair_rotation(points_a,points_b,*raw)

def refine_rotations(images, cameras, rotations):
    # The phone's AR pose is only an initial heading. The physical ultrawide
    # shutter can occur after AR tracking pauses, so matching must not depend
    # on the pose being within a few degrees of the photographed view.
    sift = cv2.SIFT_create(nfeatures=3500)
    features = [sift.detectAndCompute(cv2.cvtColor(image,cv2.COLOR_BGR2GRAY),None) for image in images]
    points = [np.float32([keypoint.pt for keypoint in keypoints]) for keypoints,_ in features]
    rays = [feature_rays(keypoints,descriptors,camera) for (keypoints,descriptors),camera in zip(features,cameras)]
    matcher = cv2.BFMatcher(cv2.NORM_L2)
    edges = []
    for a in range(len(images)):
        for b in range(a+1,len(images)):
            da,db = features[a][1],features[b][1]
            if da is None or db is None or len(da)<16 or len(db)<16: continue
            matches = [pair[0] for pair in matcher.knnMatch(da,db,k=2)
                       if len(pair)==2 and pair[0].distance < .7*pair[1].distance]
            if len(matches)<16: continue
            ia=np.array([match.queryIdx for match in matches]);ib=np.array([match.trainIdx for match in matches])
            result=pair_rotation(points[a][ia],points[b][ib],rays[a][ia],rays[b][ib])
            if result is not None: edges.append((a,b,*result))

    # Only pay for perspective rectification when native-image matching leaves
    # disconnected groups. Recorded directions seed the projection, not the fit.
    def groups():
        labels=list(range(len(images)))
        for a,b,*_ in edges:
            old,new=labels[b],labels[a]
            labels[:]=[new if label==old else label for label in labels]
        return labels
    labels=groups()
    if len(set(labels))>1:
        for a in range(len(images)):
            for b in range(a+1,len(images)):
                if labels[a]==labels[b]:continue
                result=rectified_pair(images,cameras,rotations,a,b)
                if result is not None:
                    edges.append((a,b,*result));labels=groups()

    # A convincing image match can also reveal a missed target: two photos
    # assigned distant capture directions actually look in the same direction.
    # Do not use the recorded poses to paint the repeated view into the gap.
    for a,b,_,_,relative,count,_ in edges:
        recorded_angle=np.rad2deg(Rotation.from_matrix(rotations[a].T@rotations[b]).magnitude())
        image_angle=np.rad2deg(Rotation.from_matrix(relative).magnitude())
        if count>=40 and recorded_angle>35 and image_angle<8:
            raise RetakeRequired(
                f"Photo {b+1} repeats nearly the same view as photo {a+1}; "
                f"its assigned direction was not captured. Retake that target."
            )

    def connected(pairs, start):
        adjacency=[set() for _ in images]
        for a,b,*_ in pairs:
            adjacency[a].add(b);adjacency[b].add(a)
        reachable={start};frontier=[start]
        while frontier:
            for neighbor in adjacency[frontier.pop()]-reachable:
                reachable.add(neighbor);frontier.append(neighbor)
        return reachable

    # A plain ceiling may have no usable features. Its captured orientation is
    # still the best available estimate, provided the detailed photos form one
    # verified graph. Never use pose alone for a detailed, disconnected view.
    detailed={i for i,(keypoints,_) in enumerate(features) if len(keypoints)>=30}
    components=[];unseen=set(range(len(images)))
    while unseen:
        component=connected(edges,min(unseen))
        components.append(component)
        unseen-=component
    active=max(components,key=lambda part:(len(part & detailed),len(part)))
    pose_only=set(range(len(images)))-active
    if len(active)<6 or pose_only & detailed:
        missing=", ".join(str(i+1) for i in sorted(pose_only & detailed or pose_only))
        raise RetakeRequired(f"Photos {missing} have no reliable image overlap with the rest. Recapture with more overlapping directions, keep the lens over one point, and add light if the photos are noisy.")
    root=min(active)
    edges=[edge for edge in edges if edge[0] in active and edge[1] in active]

    def solve(pairs):
        # A maximum-confidence spanning tree gives a starting orientation even
        # when individual recorded poses are tens of degrees wrong.
        initial=[None]*len(images)
        initial[root]=rotations[root]
        for _ in range(len(active)-1):
            crossing=[edge for edge in pairs if (initial[edge[0]] is None)!=(initial[edge[1]] is None)]
            if not crossing: raise RetakeRequired("The photo overlap graph is disconnected.")
            a,b,_,_,relative,_,_=max(crossing,key=lambda edge:edge[5]/(1+edge[6]))
            if initial[a] is None: initial[a]=initial[b]@relative.T
            else: initial[b]=initial[a]@relative
        initial=np.stack([rotation if rotation is not None else recorded
                          for rotation,recorded in zip(initial,rotations)])
        movable=sorted(active-{root})

        def matrices(x):
            current=initial.copy()
            current[movable]=Rotation.from_rotvec(x.reshape(-1,3)).as_matrix()@initial[movable]
            return current

        def residual(x):
            current=matrices(x)
            return np.concatenate([
                Rotation.from_matrix(relative.T@current[a].T@current[b]).as_rotvec()
                * np.sqrt(min(count,150)/50)
                for a,b,_,_,relative,count,_ in pairs
            ])

        fit=least_squares(residual,np.zeros(len(movable)*3),loss="soft_l1",f_scale=.025,max_nfev=80)
        return matrices(fit.x)

    refined=solve(edges)
    # Remove geometrically inconsistent matches, then require the remaining
    # evidence to connect every photo. This prevents a false repeated pattern
    # from dragging a whole component to the wrong direction.
    for _ in range(2):
        consistent=[]
        for edge in edges:
            a,b,_,_,relative,_,_=edge
            discrepancy=np.rad2deg(Rotation.from_matrix(relative.T@refined[a].T@refined[b]).magnitude())
            if discrepancy<=4: consistent.append(edge)
        if len(consistent)==len(edges): break
        if connected(consistent,root)!=active:
            raise RetakeRequired("Photo matches disagree about camera direction. Retake the conflicting views.")
        edges=consistent
        refined=solve(edges)

    errors=[]
    for a,b,ra,rb,_,_,_ in edges:
        world_a=ra@refined[a].T;world_b=rb@refined[b].T
        errors.extend(np.rad2deg(np.arccos(np.clip(np.sum(world_a*world_b,axis=1),-1,1))))
    alignment_error=float(np.median(errors))
    if alignment_error>2:
        raise RetakeRequired("Photos could not be aligned cleanly. Retake with less movement and more light.")
    return refined,{"matchedPairs":len(edges),"alignmentResidualDegrees":alignment_error,
                    "poseOnlyFrames":[i+1 for i in sorted(pose_only)],
                    "largestPoseCorrectionDegrees":float(np.max(Rotation.from_matrix(refined@np.transpose(rotations,(0,2,1))).magnitude())*180/np.pi)}

def sphere_rays(width,height):
    yaw=(np.arange(width,dtype=np.float32)+.5)/width*(2*np.pi)-np.pi
    pitch=np.pi/2-(np.arange(height,dtype=np.float32)+.5)/height*np.pi
    return np.stack(np.broadcast_arrays(np.sin(yaw)[None,:]*np.cos(pitch)[:,None],np.sin(pitch)[:,None],-np.cos(yaw)[None,:]*np.cos(pitch)[:,None]),axis=-1)

def warp(image,camera,rotation,rays):
    local=rays@rotation
    depth=-local[:,:,2]; fx,fy,cx,cy=camera
    u=local[:,:,0]/np.maximum(depth,.0001)*fx+cx
    v=-local[:,:,1]/np.maximum(depth,.0001)*fy+cy
    valid=(depth>.01)&(u>=1)&(u<image.shape[1]-2)&(v>=1)&(v<image.shape[0]-2)
    weight=np.where(valid,depth**4,0).astype(np.float32)
    return cv2.remap(image,u.astype(np.float32),v.astype(np.float32),cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT),weight

def stitch(manifest: dict, directory: Path, output: Path, width=4096, refine=True):
    images,cameras,rotations=load_frames(manifest,directory)
    quality={}
    if refine: rotations,quality=refine_rotations(images,cameras,rotations)
    preview_width=min(width,1024); rays=sphere_rays(preview_width,preview_width//2)
    warped=[warp(i,c,r,rays) for i,c,r in zip(images,cameras,rotations)]
    weights=np.stack([w for _,w in warped]); covered=np.max(weights,axis=0)>0
    coverage=float(np.mean(covered));quality["coverage"]=coverage
    if coverage < .995: raise RetakeRequired(f"Coverage is {coverage:.1%}. Capture the missing ceiling, floor, or side targets.")
    # Solve exposure from actual overlap, anchored to the first image.
    equations,values=[],[]
    for a in range(len(images)):
        for b in range(a+1,len(images)):
            overlap=(weights[a]>.3)&(weights[b]>.3)
            if overlap.sum()<100:continue
            ca=np.median(warped[a][0][overlap],axis=0)+1;cb=np.median(warped[b][0][overlap],axis=0)+1
            row=np.zeros(len(images));row[a]=1;row[b]=-1;equations.append(row);values.append(np.log(cb/ca))
    equations.extend(np.eye(len(images))*.1);values.extend(np.zeros((len(images),3)))
    gains=np.exp(np.linalg.lstsq(np.array(equations),np.array(values),rcond=None)[0]).clip(.5,2)
    # Ownership seams prefer the optical centre, then multiband blending smooths exposure/seams.
    owner=np.argmax(weights,axis=0).astype(np.uint8)
    height=width//2;full_rays=sphere_rays(width,height);pad=128
    blender=cv2.detail_MultiBandBlender();blender.setNumBands(6);blender.prepare((0,0,width+pad*2,height))
    any_valid=np.zeros((height,width),np.uint8)
    for index,(frame,rotation) in enumerate(zip(manifest["frames"],rotations)):
        # Feature alignment uses reduced images, but the final sphere must use the original JPEGs.
        image=cv2.imread(str(directory / f'{frame["id"]}.jpg'))
        if image is None: raise IOError("Could not reopen an original photograph for rendering.")
        camera=np.asarray(frame["intrinsics"],dtype=np.float64)
        photo,weight=warp(image,camera,rotation,full_rays)
        mask=cv2.resize((owner==index).astype(np.uint8)*255,(width,height),interpolation=cv2.INTER_NEAREST)
        mask=cv2.dilate(mask,np.ones((5,5),np.uint8));mask[weight==0]=0;any_valid[weight>0]=255
        photo=np.clip(photo*gains[index],0,255).astype(np.int16)
        blender.feed(np.concatenate((photo[:,-pad:],photo,photo[:,:pad]),axis=1),np.concatenate((mask[:,-pad:],mask,mask[:,:pad]),axis=1),(0,0))
    result,result_mask=blender.blend(None,None); result=result[:,pad:pad+width].clip(0,255).astype(np.uint8)
    # Missing pixels remain visibly black, never fabricated. Reject gaps beyond sampling noise.
    if np.mean(any_valid>0)<.995: raise RetakeRequired("Full-resolution coverage is incomplete. Retake missing targets.")
    result[any_valid==0]=0
    output.parent.mkdir(parents=True,exist_ok=True)
    if not cv2.imwrite(str(output),result,[cv2.IMWRITE_JPEG_QUALITY,94]): raise IOError("Could not save panorama.")
    cv2.imwrite(str(output.with_name("preview.jpg")),cv2.resize(result,(1024,512)),[cv2.IMWRITE_JPEG_QUALITY,82])
    quality.update(width=width,height=height,frameCount=len(images))
    output.with_suffix(".json").write_text(json.dumps(quality,indent=2))
    return quality

if __name__=="__main__":
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument("manifest",type=Path);parser.add_argument("output",type=Path)
    args=parser.parse_args();print(json.dumps(stitch(json.loads(args.manifest.read_text()),args.manifest.parent,args.output)))
