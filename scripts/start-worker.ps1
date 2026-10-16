$ErrorActionPreference = 'Stop'
$portalRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'worker-python.ps1')
Get-Content (Join-Path $portalRoot '.local/worker.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:PYTHONPATH = Join-Path $portalRoot '.worker-deps'
$portalPython = Get-PortalWorkerPython
& $portalPython.Exe @($portalPython.Args) (Join-Path $portalRoot 'worker/worker.py')
