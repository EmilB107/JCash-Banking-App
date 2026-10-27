$ErrorActionPreference = 'Stop'
$scriptRoot = $PSScriptRoot
& (Join-Path $scriptRoot 'start-db.ps1')
$backend = Start-Process powershell -ArgumentList '-NoProfile', '-File', (Join-Path $scriptRoot 'start-backend.ps1') -PassThru
$frontend = Start-Process powershell -ArgumentList '-NoProfile', '-File', (Join-Path $scriptRoot 'start-frontend.ps1') -PassThru
try {
    Write-Host "Waiting for backend health endpoint..."
    $ready = $false
    1..60 | ForEach-Object {
        if (-not $ready -and -not $backend.HasExited) {
            try { Invoke-WebRequest http://localhost:8080/api/health -UseBasicParsing -TimeoutSec 2 | Out-Null; $ready = $true }
            catch { Start-Sleep -Seconds 1 }
        }
    }
    if (-not $ready) { throw "Backend did not become ready; inspect the backend process output." }
    if ($frontend.HasExited) { throw "Frontend exited during startup; inspect its PowerShell window." }
    Write-Host "Backend is ready at http://localhost:8080/api/health."
    Write-Host "Frontend process is running; Angular normally serves http://localhost:4200."
    Write-Host "Press Ctrl+C to stop both processes."
    Wait-Process -Id $backend.Id
} finally {
    foreach ($process in @($backend, $frontend)) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
}
