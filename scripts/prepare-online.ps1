param([ValidateSet('railway','vercel')][string]$HostProvider = 'railway', [switch]$Json)
$ErrorActionPreference = 'Stop'
$portalRoot = Split-Path -Parent $PSScriptRoot
$portalBundle = Join-Path $portalRoot ('.local/online-deploy-' + [guid]::NewGuid().ToString('N'))
$portalWeb = Join-Path $portalBundle 'web'
$portalWorker = Join-Path $portalBundle 'worker'
New-Item -ItemType Directory -Path $portalWeb, (Join-Path $portalWeb 'public'), (Join-Path $portalWorker 'worker') -Force | Out-Null
foreach ($portalFile in @('package.json','package-lock.json','next.config.ts','tsconfig.json','postcss.config.mjs','Dockerfile','.dockerignore')) {
    Copy-Item -LiteralPath (Join-Path $portalRoot $portalFile) -Destination $portalWeb
}
Copy-Item -LiteralPath (Join-Path $portalRoot 'src') -Destination $portalWeb -Recurse
Get-ChildItem -LiteralPath (Join-Path $portalRoot 'public') | Where-Object { $_.Name -ne 'panoramas' } | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $portalWeb 'public') -Recurse
}
foreach ($portalFile in @('Dockerfile','requirements.txt','stitch.py','worker.py')) {
    Copy-Item -LiteralPath (Join-Path $portalRoot "worker/$portalFile") -Destination (Join-Path $portalWorker 'worker')
}
Copy-Item -LiteralPath (Join-Path $portalRoot '.dockerignore') -Destination $portalWorker
if ($HostProvider -eq 'vercel') {
    Copy-Item -LiteralPath (Join-Path $portalRoot 'deploy/vercel-web.json') -Destination (Join-Path $portalWeb 'vercel.json')
    Copy-Item -LiteralPath (Join-Path $portalRoot 'deploy/vercel-worker.json') -Destination (Join-Path $portalWorker 'vercel.json')
    Copy-Item -LiteralPath (Join-Path $portalRoot 'worker/api') -Destination $portalWorker -Recurse
    Copy-Item -LiteralPath (Join-Path $portalRoot 'worker/cloud_job.py') -Destination (Join-Path $portalWorker 'worker')
    Copy-Item -LiteralPath (Join-Path $portalRoot 'worker/requirements.txt') -Destination $portalWorker
    Set-Content -LiteralPath (Join-Path $portalWorker '.python-version') -Value '3.13'
    foreach ($portalFolder in @($portalWeb, $portalWorker)) { Copy-Item -LiteralPath (Join-Path $portalRoot '.vercelignore') -Destination $portalFolder }
} else {
    Copy-Item -LiteralPath (Join-Path $portalRoot 'deploy/railway-web.json') -Destination (Join-Path $portalWeb 'railway.json')
    Copy-Item -LiteralPath (Join-Path $portalRoot 'deploy/railway-worker.json') -Destination (Join-Path $portalWorker 'railway.json')
}
$portalFiles = Get-ChildItem -LiteralPath $portalBundle -Recurse -File
if ($portalFiles | Where-Object { $_.Name -like '.env*' -or $_.Extension -eq '.pem' -or $_.FullName -match '[\\/]public[\\/]panoramas[\\/]' }) {
    throw 'Unexpected private file in deployment bundle. Do not upload it.'
}
if ($Json) { [pscustomobject]@{web=$portalWeb;worker=$portalWorker;files=$portalFiles.Count} | ConvertTo-Json }
else {
    Write-Output "Website source: $portalWeb"
    Write-Output "Worker source: $portalWorker"
    Write-Output "Prepared $($portalFiles.Count) files without local credentials or capture previews."
}
