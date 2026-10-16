"use client";

import Link from "next/link";
import { TourCanvas } from "@/components/TourCanvas";
import type { Scene } from "@/lib/tour-contract";
import "@/app/tour-studio.css";

const scene: Scene = {
  id: "n",
  name: "n",
  assetPath: "",
  imageUrl: "/panoramas/n-360-panorama.jpg",
  initialView: { yaw: 55, pitch: 0, fov: 80 },
  points: [],
};

export default function PanoramaNPreview() {
  return <main className="public-tour">
    <TourCanvas scene={scene} onSelect={() => {}} />
    <header>
      <Link href="/app/tour">◉ portal</Link>
      <div><h1>Room n · diagnostic preview</h1><p>Capture failed photo alignment. Retake this room with the updated app before publishing.</p></div>
    </header>
  </main>;
}
