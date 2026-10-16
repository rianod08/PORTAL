# Portal on Vercel Hobby

## Connected storefront release — 15 October 2026

The website deployment `dpl_Bt3ihXqp1ZoonqYqTypzEDV5PZJP` connects the existing storefront design to account-owned venues and Android-created tours. The additive `venue_storefronts` and `venue_conflict_responses` migrations were applied before deployment. No worker or Android rebuild was needed.

Production checks passed for account and tour ownership, unique links, revision conflicts, private image uploads/previews, published image access, storefront and tour draft isolation, independent publication, venue tour routes, existing direct tour links, explicit demos and unpublishing. Website typechecking, linting (one existing image-optimization warning), 18 unit tests and the production build passed. Browser checks covered phone and desktop storefronts, cookie sign-in, onboarding, private previews and preserving form edits on conflict. Native camera capture was not repeated for this website-only release. Disposable verification storefronts and tours were left unpublished.

The two new anonymous database functions are deliberately limited to published storefront lookup and published-image access checks. Mutation RPCs reject anonymous callers and enforce ownership. The advisor continues to report unrelated legacy schema issues; see the Android README's existing security notes. Published storage links retain their existing expiry after unpublishing, while new lookups and signing are denied immediately.

Website: https://portal-capture-studio.vercel.app

Protected worker endpoint: https://portal-panorama-worker.vercel.app/api/stitch

Both projects use the existing Supabase database, Auth accounts, private Storage, and durable capture queue. The website uses Next.js; the worker uses an on-demand Python function, not a continuously running process. No local website, tunnel, or computer worker is required for online processing.

Verified on 14 October 2026: the cloud-only 15-photo `v` test reached **ready** in 62.62 seconds and produced a checked 4096×2048 panorama while the local worker was stopped. Existing-account login, tour loading, private-result download, branded publication, anonymous original-photo denial, and public-browser point rendering passed. The online URL build was installed on the S22 and loaded the new room. Android unit tests and website tests each passed 48 checks. The user updated Supabase's Site URL; a browser test confirmed recovery links arriving at the homepage reach the password screen and remove the token from the address bar. The disposable test tour was unpublished afterwards, with public lookup returning 404. Local website servers and the local worker were stopped.

## Free-plan boundaries

The final S22 touch check also passed over mobile data with Wi-Fi disabled and all local servers stopped: refresh, reopening the cloud panorama, drag, zoom/reset, and the content point worked. The test tour remains an unpublished private draft.

The Vercel team was confirmed to be on **Hobby** before deployment; no paid upgrade was made. Hobby is for personal, non-commercial use and has monthly usage allowances. Reaching an allowance may pause processing until it resets. Review [Hobby allowances](https://vercel.com/docs/plans/hobby) before commercial launch. This is an initial testing setup, not a promise of unlimited free compute.

Python functions have a 300-second execution limit and 2 GB RAM on Hobby. The protected adapter runs one scientific worker in a child process with a 270-second timeout and isolates overlapping requests within an instance. The same 4096×2048 stitch and quality checks are used. The latest `v` 15-photo capture took 65.48 seconds in the final single-thread local benchmark, with a 1,115 MB peak working set (1,761 MB peak Windows commit). Cloud acceptance must use a real hosted stitch; local timing alone does not guarantee hosted performance. Larger captures or slower jobs may require moving the worker to a dedicated host later.

## Queue and recovery

Completing or retrying a capture schedules a protected worker invocation after the API response. Authenticated capture polling also resumes queued jobs or expired leases. A live processing lease is not dispatched again. Photos move directly between private Supabase Storage and the worker; they never pass through Vercel's request-body size limit. The worker downloads calibrated originals to temporary storage, processes one claimed job, uploads the panorama/preview privately, and exits. The web server invokes it with a server-only random secret, compared in constant time. Anonymous POSTs receive `401`.

If an invocation times out, its child process is killed and the two-minute durable lease expires. Polling can retry; the existing three-attempt limit prevents endless retries. Recovery does not depend on idle cron jobs. If no client is polling after an interruption, reopening the capture inbox or resuming the app initiates recovery. The original photos remain private and intact.

## Redeploy

1. `vercel login`, using the existing Hobby scope.
2. `./scripts/prepare-online.ps1 -HostProvider vercel -Json` emits sanitized website and worker folders. No local `.env`, passwords, Android build artifacts, or diagnostic `public/panoramas` images are included.
3. Configure server-only values with `./scripts/configure-vercel.ps1 -Scope <scope> -WorkerUrl https://portal-panorama-worker.vercel.app/api/stitch -PortalUrl https://portal-capture-studio.vercel.app`. The helper sends values through stdin without trailing newlines or secret command arguments. The random shared secret is retained only in ignored local state and Vercel secrets. The worker receives its restricted Supabase worker account; no service-role key is used.
4. Link and deploy each emitted directory to its existing project: `vercel link --yes --project <project> --scope <scope> --cwd <folder>` then `vercel deploy --prod --yes --scope <scope> --cwd <folder>`. Do not deploy the whole Android workspace or raw capture folders. Root `.vercelignore` adds another exclusion layer.
5. Check web `/api/v1/health`, worker GET `/api/stitch`, rejected anonymous worker POST, sign-in, private asset access, and a real 15-photo cloud stitch with the local worker stopped.
6. Build/install Android with `./scripts/build-online-app.ps1 -PortalUrl https://portal-capture-studio.vercel.app -Install`. Hosted builds hide the address field and replace a previous saved LAN address while retaining tokens, drafts, and photos.

Supabase Auth Site URL and allowed recovery/callback URLs should use the public website origin. This requires updating the existing project's Auth URL configuration, not migrating its database or Storage. Keep production-origin cookies secure and do not use localhost in hosted environment variables.
