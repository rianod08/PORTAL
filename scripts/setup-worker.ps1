$ErrorActionPreference = 'Stop'
$portalRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'worker-python.ps1')
$portalPython = Get-PortalWorkerPython
& $portalPython.Exe @($portalPython.Args) -m pip install --target (Join-Path $portalRoot '.worker-deps') -r (Join-Path $portalRoot 'worker/requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Worker dependency installation failed.' }
