function Get-PortalWorkerPython {
    if ($env:PORTAL_PYTHON) {
        if (-not (Test-Path -LiteralPath $env:PORTAL_PYTHON)) { throw "PORTAL_PYTHON does not exist." }
        return @{ Exe = $env:PORTAL_PYTHON; Args = @() }
    }
    $bundled = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    if (Test-Path -LiteralPath $bundled) { return @{ Exe = $bundled; Args = @() } }
    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if ($launcher) { return @{ Exe = $launcher.Source; Args = @('-3.12') } }
    throw 'Python 3.12 is required. Install it or set PORTAL_PYTHON to its executable.'
}
