param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$Scope,
    [ValidatePattern('^[a-z0-9-]+$')][string]$WebProject = 'portal-capture-studio',
    [ValidatePattern('^[a-z0-9-]+$')][string]$WorkerProject = 'portal-panorama-worker',
    [uri]$PortalUrl,
    [uri]$WorkerUrl,
    [switch]$WorkerOnly
)
$ErrorActionPreference = 'Stop'
$portalRoot = Split-Path -Parent $PSScriptRoot
function Read-PortalValues([string]$Path) {
    $portalValues=@{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $portalValues[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'") }
    }
    return $portalValues
}
function Set-VercelValue([string]$Project,[string]$Name,[string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Missing $Name for $Project." }
    $portalStart=New-Object System.Diagnostics.ProcessStartInfo
    $portalStart.FileName=$env:ComSpec
    $portalStart.Arguments="/d /c vercel env add $Name production --sensitive --yes --force --project $Project --scope $Scope"
    $portalStart.UseShellExecute=$false;$portalStart.CreateNoWindow=$true
    $portalStart.RedirectStandardInput=$true;$portalStart.RedirectStandardOutput=$true;$portalStart.RedirectStandardError=$true
    $portalProcess=[System.Diagnostics.Process]::Start($portalStart)
    $portalOutput=$portalProcess.StandardOutput.ReadToEndAsync();$portalError=$portalProcess.StandardError.ReadToEndAsync()
    $portalProcess.StandardInput.Write($Value);$portalProcess.StandardInput.Close()
    if (!$portalProcess.WaitForExit(90000)) { $portalProcess.Kill();throw "Timed out configuring $Name." }
    $portalOutput.GetAwaiter().GetResult() | Out-Null;$portalError.GetAwaiter().GetResult() | Out-Null
    $portalCode=$portalProcess.ExitCode;$portalProcess.Dispose()
    if ($portalCode -ne 0) { throw "Could not configure $Name for $Project. Check Vercel login and project scope." }
    Write-Output "Configured $Name for $Project."
}
$portalValues=Read-PortalValues (Join-Path $portalRoot '.env.local')
$portalWorkerValues=Read-PortalValues (Join-Path $portalRoot '.local/worker.env')
foreach ($portalName in @('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY')) {
    if (!$portalValues[$portalName] -or $portalValues[$portalName] -ne $portalWorkerValues[$portalName]) { throw "Website and worker $portalName must match." }
}
$portalSecretPath=Join-Path $portalRoot '.local/online-worker-secret.txt'
if (!(Test-Path -LiteralPath $portalSecretPath)) {
    $portalBytes=New-Object byte[] 32
    $portalRandom=[System.Security.Cryptography.RandomNumberGenerator]::Create();$portalRandom.GetBytes($portalBytes);$portalRandom.Dispose()
    [System.IO.File]::WriteAllText($portalSecretPath,([BitConverter]::ToString($portalBytes).Replace('-','').ToLowerInvariant()))
}
$portalSecret=[System.IO.File]::ReadAllText($portalSecretPath).Trim()
foreach ($portalName in @('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY','PORTAL_WORKER_EMAIL','PORTAL_WORKER_PASSWORD')) { Set-VercelValue $WorkerProject $portalName $portalWorkerValues[$portalName] }
Set-VercelValue $WorkerProject 'PORTAL_WORKER_SECRET' $portalSecret
if (!$WorkerOnly) {
    if (!$WorkerUrl -or $WorkerUrl.Scheme -ne 'https' -or $WorkerUrl.UserInfo -or $WorkerUrl.Query -or $WorkerUrl.Fragment) { throw 'Provide the deployed HTTPS worker endpoint.' }
    foreach ($portalName in @('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY')) { Set-VercelValue $WebProject $portalName $portalValues[$portalName] }
    Set-VercelValue $WebProject 'PORTAL_WORKER_URL' $WorkerUrl.AbsoluteUri
    Set-VercelValue $WebProject 'PORTAL_WORKER_SECRET' $portalSecret
    if ($PortalUrl) {
        if ($PortalUrl.Scheme -ne 'https' -or $PortalUrl.UserInfo -or $PortalUrl.Query -or $PortalUrl.Fragment -or $PortalUrl.AbsolutePath -ne '/') { throw 'Provide the deployed HTTPS website origin.' }
        Set-VercelValue $WebProject 'PORTAL_ORIGIN' $PortalUrl.GetLeftPart([System.UriPartial]::Authority)
    }
}
