"use client";

import Link from "next/link";
import { TourCanvas } from "@/components/TourCanvas";
import type { Scene } from "@/lib/tour-contract";
import "@/app/tour-studio.css";

const scene: Scene = {
  id: "eba9d80f-7651-4557-ac63-0283bb3b7029",
  name: "v",
  assetPath: "",
  imageUrl: "/panoramas/v-eba9d80f-360-panorama.jpg",
  initialView: { yaw: 0, pitch: 0, fov: 80 },
  points: [],
};

export default function PanoramaVPreview() {
  return <main className="public-tour">
    <TourCanvas scene={scene} onSelect={() => {}} />
    <header>
      <Link href="/app/tour">◉ portal</Link>
      <div><h1>Room v · 360° photosphere</h1><p>Latest 15-photo capture. Drag to look around; scroll to zoom.</p></div>
    </header>
  </main>;
}
