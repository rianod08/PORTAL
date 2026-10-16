"use client";
import Link from "next/link";
import Image from "next/image";
import { useEffect, useState, type CSSProperties } from "react";
import { api } from "@/lib/cloud-client";
import type { TourDocument, TourPoint } from "@/lib/tour-contract";
import { TourCanvas } from "@/components/TourCanvas";
import "@/app/tour-studio.css";
export default function CloudTourViewer({slug,backHref,backLabel="Back to storefront"}:{slug:string;backHref?:string;backLabel?:string}){
 const [doc,setDoc]=useState<TourDocument|null>(null),[active,setActive]=useState(""),[info,setInfo]=useState<TourPoint|null>(null),[error,setError]=useState("");
 useEffect(()=>{let alive=true;const load=()=>api<{document:TourDocument}>(`public/${encodeURIComponent(slug)}`).then(r=>{if(alive){setDoc(r.document);setActive(a=>a||r.document.defaultSceneId);setError("");}}).catch(e=>{if(alive){setError(e.message);setDoc(null);}});load();const timer=setInterval(load,45*60*1000);return()=>{alive=false;clearInterval(timer);};},[slug]);
 const scene=doc?.scenes.find(s=>s.id===active)??doc?.scenes[0];
 function select(p:TourPoint){if(p.kind==="scene"){setActive(p.targetSceneId??"");setInfo(null);}else setInfo(p);}
 return <main className={`public-tour branded-tour ${doc?.branding?.colorScheme??"dark"}`} style={{"--green":doc?.branding?.accentColor??"#bce867"} as CSSProperties}>{scene&&<TourCanvas scene={scene} onSelect={select}/>}<header>{backHref&&<Link href={backHref}>{backLabel} ←</Link>}{doc?.branding?.logoUrl?<Image unoptimized width={64} height={64} src={doc.branding.logoUrl} alt={`${doc.title} logo`}/>:<Link href="/">◉ portal</Link>}<div><h1>{doc?.title??"Portal tour"}</h1><p>{scene?.name??error??"Loading…"}</p></div></header>{!scene&&<div className="tour-canvas-message" role="status">{error||"Opening your tour…"}</div>}<nav aria-label="Tour rooms">{doc?.scenes.map(s=><button key={s.id} aria-current={s.id===scene?.id?"true":undefined} onClick={()=>{setActive(s.id);setInfo(null);}}>{s.name}</button>)}</nav>{info&&<div className="tour-modal" role="dialog" aria-modal="true" aria-label={info.label}><div><button autoFocus aria-label="Close details" onClick={()=>setInfo(null)}>×</button><h2>{info.label}</h2><p>{info.description}</p>{info.kind==="image"&&(info.mediaUrl||info.url)&&<Image unoptimized width={800} height={500} src={(info.mediaUrl||info.url)!} alt={info.label}/>} {info.kind==="video"&&(info.mediaUrl||info.url)&&<video controls playsInline preload="metadata" src={info.mediaUrl||info.url} onError={e=>{e.currentTarget.insertAdjacentText("afterend","This video could not play. Try opening its link.");}}/>}{(info.url||info.mediaUrl)&&<a href={info.url||info.mediaUrl} target="_blank" rel="noopener noreferrer">{info.kind==="booking"?"Book now":info.kind==="menu"?"View menu":"Open link"} ↗</a>}</div></div>}</main>;
}
