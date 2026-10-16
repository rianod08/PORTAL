# Local setup

## Requirements

- Node.js 20.9 or newer.
- Python 3.12 for the panorama worker.
- A Supabase project.
- Android Studio, Android SDK 36 and JDK 17 if you want to build the app.

The commands below use PowerShell from the repository root.

## Configure Supabase

For a new project, run these SQL files in the Supabase SQL editor, in this order:

1. [schema.sql](../supabase/schema.sql)
2. [worker-security.sql](../supabase/worker-security.sql)
3. [worker-policy-fix.sql](../supabase/worker-policy-fix.sql)
4. [tour-editor.sql](../supabase/tour-editor.sql)
5. Every file in [supabase/migrations](../supabase/migrations), in filename order.

These scripts set up accounts, tour storage, capture processing and storefronts. If your project is already configured, apply only the scripts or migrations it is missing.

Copy the environment example:

```powershell
Copy-Item .env.example .env.local
```

Edit `.env.local` with your Supabase project URL and publishable key. Keep `PORTAL_ORIGIN=http://localhost:3000` for local development.

## Start the website

```powershell
npm ci
npm run dev -- --webpack --hostname 0.0.0.0
```

Open [localhost:3000/login](http://localhost:3000/login) and sign in or create an account.

## Start the panorama worker

The worker is needed to stitch new phone captures. You can view and edit existing tours without running it.

Create a dedicated worker user in Supabase Auth. In the SQL editor, add its user ID to the worker list:

```sql
insert into portal_private.workers (user_id)
values ('YOUR_WORKER_USER_ID');
```

Create `.local/worker.env` with these values:

```dotenv
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-publishable-key
PORTAL_WORKER_EMAIL=your-worker-email
PORTAL_WORKER_PASSWORD=your-worker-password
```

In another PowerShell terminal, run:

```powershell
.\scripts\setup-worker.ps1
.\scripts\start-worker.ps1
```

The launcher looks for the bundled Python runtime or the Windows Python 3.12 launcher. To use another installation, set `PORTAL_PYTHON` to its executable path.

Environment files are ignored by Git. Use the publishable key and the restricted worker account; do not place a service-role key in the website or Android app.

## Connect Android

Sign in with the same Portal account on your phone and the website.

For a local development build, use `http://<computer-LAN-IP>:3000` on a phone connected to the same network, or `http://10.0.2.2:3000` in the Android emulator. Release builds require HTTPS.

See the [Android guide](../android/VenueLinkStudio/README.md) to build and install the app.

For a hosted website and worker, follow the [Vercel](../deploy/vercel.md) or [Railway](../deploy/README.md) guide instead.
