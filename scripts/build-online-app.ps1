param(
    [Parameter(Mandatory = $true)][uri]$PortalUrl,
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$portalRoot = Split-Path -Parent $PSScriptRoot
if ($PortalUrl.Scheme -ne 'https' -or $PortalUrl.UserInfo -or $PortalUrl.Query -or $PortalUrl.Fragment -or $PortalUrl.AbsolutePath -ne '/') {
    throw 'Use the public HTTPS origin, for example https://your-service.up.railway.app.'
}
$portalOrigin = $PortalUrl.GetLeftPart([System.UriPartial]::Authority)
$portalHealth = Invoke-RestMethod -Uri "$portalOrigin/api/v1/health" -TimeoutSec 30
if ($portalHealth.app -ne 'Portal' -or $portalHealth.status -ne 'ready') { throw 'The online Portal service is not ready.' }
Push-Location (Join-Path $portalRoot 'android/VenueLinkStudio')
try {
    & ./gradlew.bat assembleDebug "-PportalBaseUrl=$portalOrigin"
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    $portalApk = Join-Path (Get-Location) 'app/build/outputs/apk/debug/app-debug.apk'
    if ($Install) {
        $portalAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
        if (!(Test-Path -LiteralPath $portalAdb)) { throw "APK built at $portalApk; Android platform tools were not found." }
        & $portalAdb install -r $portalApk
        if ($LASTEXITCODE -ne 0) { throw "APK built at $portalApk; reconnect and unlock the phone, then retry installation." }
    }
    Write-Output "Online Portal APK: $portalApk"
} finally { Pop-Location }
