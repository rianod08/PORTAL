import type { ReactNode } from "react";
import { VenueProvider } from "@/features/VenueProvider";
export default function OwnerLayout({ children }: { children: ReactNode }) {
  return <VenueProvider>{children}</VenueProvider>;
}
