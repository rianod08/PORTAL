import { notFound } from "next/navigation";
import { hydrateVenue, publicVenue } from "@/lib/venue-server";
import { VenueStorefront } from "@/features/VenueStorefront";
export default async function VenuePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const venue = await publicVenue((await params).slug);
  if (!venue) notFound();
  return <VenueStorefront venue={await hydrateVenue(venue)} />;
}
