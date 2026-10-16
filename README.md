# Portal

Capture, edit and share interactive 360° venue tours using an Android phone.

[Open Portal](https://portal-capture-studio.vercel.app) · [Try the demo](https://portal-capture-studio.vercel.app/demo)

## What you can do

- Capture rooms with guided targets on Android.
- Connect rooms into a tour with links, photos and videos.
- Create a storefront with your branding, opening hours and booking links.
- Share your venue through one public link or QR code.
- Edit downloaded tours offline and sync when you're connected.

## How it works

1. Sign in with the same account on Android and the website.
2. Capture your rooms and edit the tour.
3. Create a venue on the website and select its tour.
4. Publish the tour and storefront, then share your link.

Saved edits stay private until you publish. Tours and storefronts are published separately.

## Run locally

Set up your Supabase project and environment files using the [setup guide](docs/setup.md), then start the website:

```powershell
npm ci
npm run dev -- --webpack --hostname 0.0.0.0
```

Open [localhost:3000](http://localhost:3000). To process new phone captures, run the worker in another terminal:

```powershell
.\scripts\setup-worker.ps1
.\scripts\start-worker.ps1
```

## Build the Android app

Requires Android SDK 36 and JDK 17. Guided capture also needs a supported ARCore phone with an ultrawide camera.

```powershell
.\android\VenueLinkStudio\gradlew.bat -p android\VenueLinkStudio assembleDebug
```

See the [Android guide](android/VenueLinkStudio/README.md) for installation and capture instructions.

## Checks

```powershell
npm run typecheck
npm run lint
npm test
```

## More information

- [Local setup](docs/setup.md)
- [Android app](android/VenueLinkStudio/README.md)
- [Vercel deployment](deploy/vercel.md)
- [Railway deployment](deploy/README.md)

The project uses Next.js for the website, Kotlin for Android, Supabase for accounts and storage, and Python for panorama stitching.
