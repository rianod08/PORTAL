param(
    [Parameter(Mandatory = $true)][guid]$ProjectId,
    [Parameter(Mandatory = $true)][uri]$PortalUrl,
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$WebService = 'portal-web',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$WorkerService = 'portal-worker'
)
$ErrorActionPreference = 'Stop'
$portalRoot = Split-Path -Parent $PSScriptRoot
if ($PortalUrl.Scheme -ne 'https' -or $PortalUrl.UserInfo -or $PortalUrl.Query -or $PortalUrl.Fragment -or $PortalUrl.AbsolutePath -ne '/') {
    throw 'Use the public HTTPS origin.'
}
function Read-PortalEnvironment([string]$Path) {
    $portalValues = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            $portalKey = $Matches[1]
            $portalValue = $Matches[2].Trim()
            if ($portalValue.Length -ge 2 -and (($portalValue.StartsWith('"') -and $portalValue.EndsWith('"')) -or ($portalValue.StartsWith("'") -and $portalValue.EndsWith("'")))) {
                $portalValue = $portalValue.Substring(1, $portalValue.Length - 2)
            }
            $portalValues[$portalKey] = $portalValue
        }
    }
    return $portalValues
}
function Set-PortalVariable([string]$Service, [string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "Missing $Name for $Service." }
    # Secrets travel through stdin; CLI output is suppressed to avoid logging values.
    $portalStart = New-Object System.Diagnostics.ProcessStartInfo
    $portalStart.FileName = $env:ComSpec
    $portalStart.Arguments = "/d /c npx --yes @railway/cli@5.68.0 variable set $Name --stdin --skip-deploys --project $ProjectId --service $Service"
    $portalStart.UseShellExecute = $false
    $portalStart.CreateNoWindow = $true
    $portalStart.RedirectStandardInput = $true
    $portalStart.RedirectStandardOutput = $true
    $portalStart.RedirectStandardError = $true
    $portalProcess = [System.Diagnostics.Process]::Start($portalStart)
    $portalOutput = $portalProcess.StandardOutput.ReadToEndAsync()
    $portalError = $portalProcess.StandardError.ReadToEndAsync()
    # Write without the newline that a PowerShell pipeline adds to password values.
    $portalProcess.StandardInput.Write($Value)
    $portalProcess.StandardInput.Close()
    if (!$portalProcess.WaitForExit(90000)) { $portalProcess.Kill(); throw "Timed out configuring $Name for $Service." }
    $portalOutput.GetAwaiter().GetResult() | Out-Null
    $portalError.GetAwaiter().GetResult() | Out-Null
    $portalExitCode = $portalProcess.ExitCode
    $portalProcess.Dispose()
    if ($portalExitCode -ne 0) { throw "Could not configure $Name for $Service. Check Railway login and service names." }
    Write-Output "Configured $Name for $Service."
}
$portalWebEnv = Read-PortalEnvironment (Join-Path $portalRoot '.env.local')
$portalWorkerEnv = Read-PortalEnvironment (Join-Path $portalRoot '.local/worker.env')
foreach ($portalKey in @('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY')) {
    if (!$portalWebEnv[$portalKey] -or $portalWebEnv[$portalKey] -ne $portalWorkerEnv[$portalKey]) { throw "Website and worker $portalKey must identify the same existing Supabase project." }
}
foreach ($portalKey in @('PORTAL_WORKER_EMAIL','PORTAL_WORKER_PASSWORD')) {
    if (!$portalWorkerEnv[$portalKey]) { throw "Missing restricted-worker setting $portalKey." }
}
foreach ($portalKey in @('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY')) { Set-PortalVariable $WebService $portalKey $portalWebEnv[$portalKey] }
Set-PortalVariable $WebService 'PORTAL_ORIGIN' $PortalUrl.GetLeftPart([System.UriPartial]::Authority)
foreach ($portalKey in @('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY','PORTAL_WORKER_EMAIL','PORTAL_WORKER_PASSWORD')) { Set-PortalVariable $WorkerService $portalKey $portalWorkerEnv[$portalKey] }
Write-Output 'Variables configured. Deploy the prepared sources next. No deployments were triggered by this script.'
