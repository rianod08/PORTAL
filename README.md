# Portal Capture

## Connected storefronts

The website now saves account-owned venue profiles in Supabase. Sign in at `/login`, create a venue, select an existing Android or web tour, and customize its details, images, buttons and theme. Each account can manage multiple venues; each venue selects one main tour. The website's venue selector does not change Android tour documents.

**Save changes** updates a private storefront draft. **Save & preview** opens an authenticated preview. **Publish storefront** releases a snapshot at `/v/{venue-slug}` and enables its share link and downloadable QR code. Publish the tour separately in Android or the web tour editor: `/v/{venue-slug}/tour` resolves the selected tour's latest published revision. If the tour is unpublished, the storefront hides its tour entry. Existing `/v/{tour-slug}/tour` links continue to work. Venue links become fixed after their first publication.

Booking and menu buttons open the owner's external links. Contact, directions and custom actions use saved venue details. Booking enquiry storage, live analytics and sample reviews are excluded from production storefronts. Samples are explicitly available under `/demo/v/{slug}`; browser-only records and the existing legacy tour importer are preserved.

Apply both additive migrations in `supabase/migrations/` in filename order before deploying this version. Venue APIs live under `/api/v1/venues` (list/create, read/update, publish, preview, media); `/api/v1/public/venues/{slug}` serves published content only. Writes use expected revisions and return HTTP 409 for conflicts, preserving local form edits. Supabase policies enforce ownership and only allow public image signing for currently published storefront assets. Existing signed image links retain their one-hour expiry after unpublishing; fresh public lookup and signing are denied immediately.

`scripts/test-venue-flow.py` runs opt-in integration checks against the configured Supabase project using ignored `.local/test-accounts.json`. Use the worker Python with `.worker-deps` on `PYTHONPATH`. It checks ownership, conflicts, private previews, image access, separate publishing and route compatibility. Fixtures end unpublished; `--keep-public` and then `--cleanup` support browser verification. Existing captures and tours are never modified by this test.

Portal Capture has two parts: an Android app with guided capture, an offline tour builder, and a native 360° viewer, and a Portal website with a tour editor and 360° visitor viewer. A Python worker stitches the photos into a 2:1 panorama. It does not create a 3D model.

## Run locally

For an online server that does not depend on this computer, see [Vercel deployment](deploy/vercel.md). The website/API and protected on-demand Python stitch worker run as separate Vercel projects while preserving the existing Supabase accounts, tours, and files. Android can bundle that HTTPS address so sign-in needs only email and password. [Railway deployment](deploy/README.md) remains an option for a persistent worker with larger compute limits. Local development below remains available.

The current workspace already has its Supabase schema and private `.env.local` / `.local/worker.env` configured. To run it, open two PowerShell terminals in this folder:

```powershell
npm install
npm run dev -- --webpack --hostname 0.0.0.0
```

```powershell
.\scripts\setup-worker.ps1
.\scripts\start-worker.ps1
```

Open [http://localhost:3000/login](http://localhost:3000/login), sign in or create an account, then use the [tour editor](http://localhost:3000/app/tour). The website and worker must both be running for new Android captures to process. Existing saved tours remain available if the worker is stopped. Supabase provides authentication, private media storage, and the durable job queue; the UI and worker run on this computer.

For a new Supabase project, copy `.env.example` to `.env.local`, fill in the project URL and publishable key, and apply `supabase/schema.sql` followed by `supabase/worker-security.sql` and `supabase/tour-editor.sql` in the SQL editor. Create a dedicated worker Auth user and insert its user ID into `portal_private.workers`. Put its email and password in `.local/worker.env` along with `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`; this file is Git-ignored. Do not put a service role key in a browser or Android build.

## Android capture

Build in `android/VenueLinkStudio`:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

Install `android/VenueLinkStudio/app/build/outputs/apk/debug/app-debug.apk` on an ARCore-capable Android phone with a camera. The debug app accepts local HTTP addresses: use `http://10.0.2.2:3000` in an emulator or `http://<computer-LAN-IP>:3000` on a phone connected to the same network. The release app requires HTTPS. Sign into the same Portal account in Android and the website.

Create or choose a tour, name a room, then rotate and tilt around one fixed lens position through 15 green targets: seven around the horizon, four tilted upward at 55°, and four tilted downward at 55°. The tilted photos include the ceiling and floor. Keep the lens within 12 cm of its starting point; the hold ring pauses when the lens moves away. Stationary feet alone are not enough: avoid swinging the phone around your body. Center a dot within the 5° aiming area and hold the phone steady for one second; the ring shows progress and resets if the gyroscope detects movement. The guide preview uses ARCore's main camera so its target stays anchored. If ARCore briefly loses visual tracking on a plain ceiling or floor, the phone's game rotation sensor carries the last tracked orientation while its position stays fixed; the screen labels this as motion guidance. Each saved still is taken with a back ultrawide lens (at least 90° horizontal field of view). On the tested Galaxy S22 this is Camera2 ID 2, separate from ARCore's ID 0. Keep the lens over the same point while each still briefly switches lenses. Intrinsics use Camera2 measured calibration when available, with the physical focal-length calculation only as a fallback. Sensor fallback is aligned through ARCore’s Android sensor pose and matched frame timestamps, including landscape display orientation. The app saves the original 4032×3024 ultrawide photos and calibration to private device storage. After the last target, upload starts automatically; WorkManager resumes interrupted transfers. A local worker checks coverage and alignment, stitches the panorama, and marks the capture **ready** or **needs retake**. The Android app follows processing progress, downloads ready panoramas privately, and opens them in its native 360° viewer. Saved captures can be reopened offline with **View panorama**; drag to look around, pinch to zoom, or reset to the wider 95° default view. In the web editor, use **Capture inbox → Add to tour**, connect rooms with points, preview, and publish. A manually produced 2:1 panorama can also be uploaded directly in the editor.

The stitch worker matches image features across all photo pairs and solves the camera directions automatically. If raw perspective matching leaves disconnected groups, it also matches overlap projected into shared tangent views. Degenerate matches onto a single point or line are rejected. Movement checks use original `observedPose` positions from older captures even if their `pose` translations were flattened. More than 25 cm between any two shutter positions is rejected with the measured displacement. It can correct a wrong recorded direction when overlapping detail is visible. A plain ceiling or floor photo can use its recorded direction if the detailed photos form a connected match graph. Repeated views, disconnected detailed photos, and incomplete sphere coverage are marked **needs retake** rather than stitched into a misleading panorama. The archived `n` capture contains a repeated direction (photos 1 and 6), so it cannot yield a complete verified photosphere from those 12 originals.

The old Android local-tour library remains accessible as an archive. New guided captures and tours use the Portal Android tour builder or the web editor. The old local stitcher was a prototype and should not be used for new photos.

## Checks

```powershell
npm run typecheck
npm run lint
npm test
.\android\VenueLinkStudio\gradlew.bat -p android\VenueLinkStudio testDebugUnitTest assembleDebug
```

`worker/test_stitch.py` exercises alignment and coverage with synthetic calibrated photos, including the legacy 12-shot pattern, the 15-shot portrait layout, shared-view matching, degenerate correspondences, hidden camera translation, and 100 sets of ±5° aiming variation. `scripts/test-capture-flow.py` and `scripts/finalize-integration.py` are end-to-end checks using ignored test accounts in `.local/test-accounts.json`; they create a test tour and upload photos to the configured Supabase project. The full local integration check passed with a 4096×2048 stitched panorama, public image access, revision conflicts, draft isolation, and unpublishing. On a Galaxy S22, the installed Android build displayed 0/12, captured and saved 4032×3024 ultrawide JPEGs, and resumed AR guidance after the lens switch. A complete 12-photo physical room stitch has not yet been captured.

## Capture h diagnosis

The 12 originals from `h` are intact. The earlier Android build replaced their camera positions with a fixed centre, while `observedPose` records a maximum 74 cm separation. That conceals parallax in the nearby furniture and floor. Native SIFT matching also misses some overlap between strongly different ultrawide perspectives. The revised worker detects the movement directly, and the updated app preserves real positions, gates capture near its initial lens position, uses measured intrinsics, and captures 15 overlapping directions. The existing `h` draft remains unverified; its photos need a new capture from a fixed optical centre. Unit and synthetic checks do not replace a complete physical room capture with the updated app.

## Android tour builder

The signed-in home screen lists your tours. Choose **Edit** for Rooms, the 360° Editor, Connections, and Style. Successful captures automatically enter the matching draft. Aim the centre marker to place room links, text, uploaded images/videos, or external links. A guided return-link action positions the reverse doorway in its destination. The connection map has draggable room cards and directed links; branding supports a logo, accent colour, and light/dark style.

Draft edits save on the phone, including offline. Media uploads resume through TUS checkpoints and WorkManager. Server revision conflicts preserve local edits and require a deliberate resolution; the previous phone draft remains recoverable. **Preview** uses the real sphere viewer and cached media; **Publish** updates the public snapshot, while later edits remain private. Share links use the configured server address: the current LAN address is available on your network, and public internet sharing requires a deployed server URL. Images accept JPEG/PNG/WebP, videos accept H.264 MP4 with optional AAC audio, and each file is limited to 50 MB.

See `android/VenueLinkStudio/README.md` for editor testing and migration details. The database upgrade preserves existing capture originals and panorama files. The public browser viewer supports the same branding and rich point media, and existing web-editor saves retain the Android additions.
