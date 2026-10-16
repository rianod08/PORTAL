"use client";
export default function VenueError({ reset }: { reset: () => void }) {
  return (
    <main className="grid min-h-screen place-content-center gap-4 p-8">
      <h1 className="text-2xl font-bold">This venue could not load</h1>
      <p>Please try again in a moment.</p>
      <button
        className="rounded-full bg-slate-950 p-4 text-white"
        onClick={reset}
      >
        Try again
      </button>
    </main>
  );
}
