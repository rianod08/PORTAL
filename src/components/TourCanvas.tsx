"use client";
import { useEffect, useRef, useState } from "react";
import type { Scene, TourPoint, View } from "@/lib/tour-contract";
import { normalizeYaw } from "@/lib/tour-contract";

export function TourCanvas({ scene, editing = false, selected, onSelect, onAdd, onMove, onView }: { scene: Scene; editing?: boolean; selected?: string; onSelect: (point: TourPoint) => void; onAdd?: (view: View) => void; onMove?: (id: string, view: View) => void; onView?: (view: View) => void }) {
  const mount = useRef<HTMLDivElement>(null), live = useRef({ scene, onSelect, onMove, onView });
  const view = useRef(scene.initialView), controls = useRef<{ ray: (x: number,y: number) => View; zoom: (delta: number) => void } | null>(null);
  const [positions, setPositions] = useState<Record<string,{ x: number;y: number }>>({});
  const [error, setError] = useState("");
  useEffect(() => { live.current = { scene,onSelect,onMove,onView }; }, [scene,onSelect,onMove,onView]);
  useEffect(() => {
    const container = mount.current; if (!container || !scene.imageUrl) return;
    let dispose = () => {}, cancelled = false;
    import("three").then(async THREE => {
      if (cancelled) return;
      const renderer = new THREE.WebGLRenderer({ antialias: true });
      renderer.setPixelRatio(Math.min(devicePixelRatio,2)); container.appendChild(renderer.domElement);
      const world = new THREE.Scene(), camera = new THREE.PerspectiveCamera(80,1,0.1,20);
      const geometry = new THREE.SphereGeometry(10,96,64); geometry.scale(-1,1,1);
      const material = new THREE.MeshBasicMaterial(); const sphere = new THREE.Mesh(geometry,material);
      // Rotate the worker's -Z camera frame into Three's +Z viewing frame.
      sphere.rotation.y = Math.PI/2; world.add(sphere);
      view.current = { ...live.current.scene.initialView }; let frame = 0, dragging = false, lastX=0,lastY=0;
      const raycaster = new THREE.Raycaster();
      const direction = (v: View) => new THREE.Vector3(-Math.sin(v.yaw*Math.PI/180)*Math.cos(v.pitch*Math.PI/180),Math.sin(v.pitch*Math.PI/180),Math.cos(v.yaw*Math.PI/180)*Math.cos(v.pitch*Math.PI/180));
      controls.current = { ray: (x,y) => { const rect=container.getBoundingClientRect(); raycaster.setFromCamera(new THREE.Vector2((x-rect.left)/rect.width*2-1,1-(y-rect.top)/rect.height*2),camera); const d=raycaster.ray.direction; return { yaw: normalizeYaw(Math.atan2(-d.x,d.z)*180/Math.PI),pitch: Math.asin(d.y)*180/Math.PI,fov:view.current.fov }; }, zoom: delta => { view.current.fov=Math.max(30,Math.min(110,view.current.fov+delta)); } };
      const down=(e:PointerEvent)=>{dragging=true;lastX=e.clientX;lastY=e.clientY;renderer.domElement.setPointerCapture(e.pointerId);};
      const move=(e:PointerEvent)=>{if(!dragging)return;view.current.yaw=normalizeYaw(view.current.yaw-(e.clientX-lastX)*0.18);view.current.pitch=Math.max(-89,Math.min(89,view.current.pitch+(e.clientY-lastY)*0.18));lastX=e.clientX;lastY=e.clientY;};
      const up=()=>{dragging=false;}; const wheel=(e:WheelEvent)=>{e.preventDefault();controls.current?.zoom(e.deltaY*0.03);};
      const key=(e:KeyboardEvent)=>{const actions:Record<string,()=>void>={ArrowLeft:()=>{view.current.yaw-=5;},ArrowRight:()=>{view.current.yaw+=5;},ArrowUp:()=>{view.current.pitch=Math.min(89,view.current.pitch+5);},ArrowDown:()=>{view.current.pitch=Math.max(-89,view.current.pitch-5);},"+":()=>controls.current?.zoom(-5),"-":()=>controls.current?.zoom(5)};if(actions[e.key]){e.preventDefault();actions[e.key]();}};
      const resize=new ResizeObserver(()=>{const w=container.clientWidth,h=container.clientHeight;renderer.setSize(w,h);camera.aspect=w/Math.max(1,h);camera.updateProjectionMatrix();});resize.observe(container);
      renderer.domElement.addEventListener("pointerdown",down);renderer.domElement.addEventListener("pointermove",move);renderer.domElement.addEventListener("pointerup",up);renderer.domElement.addEventListener("pointercancel",up);renderer.domElement.addEventListener("wheel",wheel,{passive:false});container.addEventListener("keydown",key);
      let previous="";
      const render=()=>{camera.fov=view.current.fov;camera.lookAt(direction(view.current));camera.updateProjectionMatrix();camera.updateMatrixWorld();const positions:Record<string,{x:number;y:number}>={};const forward=direction(view.current);
        for(const p of live.current.scene.points){if(!p.enabled&&!editing)continue;const d=direction({...p,fov:80});if(d.dot(forward)<=0)continue;const projected=d.clone().multiplyScalar(10).project(camera);if(Math.abs(projected.x)<=1.1&&Math.abs(projected.y)<=1.1)positions[p.id]={x:(projected.x+1)*50,y:(1-projected.y)*50};}
        const serial=JSON.stringify(positions);if(previous!==serial){setPositions(positions);previous=serial;}renderer.render(world,camera);live.current.onView?.({...view.current});frame=requestAnimationFrame(render);};
      dispose=()=>{cancelAnimationFrame(frame);resize.disconnect();container.removeEventListener("keydown",key);geometry.dispose();material.map?.dispose();material.dispose();renderer.dispose();renderer.domElement.remove();controls.current=null;};
      try { const texture=await new THREE.TextureLoader().loadAsync(scene.imageUrl!);if(cancelled){texture.dispose();return;}texture.colorSpace=THREE.SRGBColorSpace;texture.wrapS=THREE.RepeatWrapping;material.map=texture;material.needsUpdate=true;setError("");render(); } catch { if(!cancelled)setError("Could not load panorama. Reload to refresh media access."); }
    }).catch(()=>setError("The panorama viewer needs WebGL. Try another browser or enable hardware acceleration."));
    return ()=>{cancelled=true;dispose();};
  }, [scene.id,scene.imageUrl,editing]);
  return <div className="tour-canvas" tabIndex={0} aria-label="360 panorama. Drag to look around; arrow keys to rotate; plus and minus to zoom.">
    <div ref={mount} className="tour-render" tabIndex={0}/>
    {!scene.imageUrl&&<div className="tour-canvas-message">Loading panorama…</div>}{error&&<div role="alert" className="tour-canvas-message">{error}</div>}
    {scene.points.map(p=>positions[p.id]&&<button key={p.id} className={`tour-pin ${selected===p.id?"selected":""}`} style={{left:`${positions[p.id].x}%`,top:`${positions[p.id].y}%`,opacity:p.enabled?1:0.45}} title={p.label} aria-label={p.label} onClick={()=>onSelect(p)} onPointerDown={e=>{if(!editing)return;e.currentTarget.setPointerCapture(e.pointerId);}} onPointerMove={e=>{if(editing&&e.currentTarget.hasPointerCapture(e.pointerId)){const v=controls.current?.ray(e.clientX,e.clientY);if(v)onMove?.(p.id,v);}}} onPointerUp={e=>{if(e.currentTarget.hasPointerCapture(e.pointerId))e.currentTarget.releasePointerCapture(e.pointerId);}}>{p.kind==="scene"?"↗":"+"}<span>{p.label}</span></button>)}
    {editing&&<><div className="tour-reticle">+</div><button className="tour-add-point" onClick={()=>onAdd?.({...view.current})}>＋ Add point here</button></>}
    <div className="tour-zoom"><button aria-label="Zoom in" onClick={()=>controls.current?.zoom(-10)}>＋</button><button aria-label="Zoom out" onClick={()=>controls.current?.zoom(10)}>−</button><button aria-label="Full screen" onClick={()=>mount.current?.parentElement?.requestFullscreen?.()}>⛶</button></div>
  </div>;
}
