import { notFound } from "next/navigation";
import { supa } from "@/lib/cloud-server";
import { publicVenue } from "@/lib/venue-server";
import CloudTourViewer from "@/features/CloudTourViewer";
export default async function VenueTourPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const direct = await supa("/rest/v1/rpc/portal_public_tour", undefined, {
    method: "POST",
    body: JSON.stringify({ p_slug: slug }),
  });
  if (direct) return <CloudTourViewer slug={slug} />;
  const venue = await publicVenue(slug);
  if (!venue?.tourSlug) notFound();
  return (
    <CloudTourViewer slug={venue.tourSlug} backHref={`/v/${venue.slug}`} />
  );
}
