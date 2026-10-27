$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$frontend = Join-Path $root 'frontend'
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Node.js is required." }
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw "npm is required." }
if (-not (Test-Path (Join-Path $frontend 'package-lock.json'))) { throw "Missing frontend/package-lock.json." }
if (-not (Test-Path (Join-Path $frontend 'node_modules\.bin\ng.cmd'))) {
    throw "Frontend dependencies are not installed. Run 'npm ci' in frontend first."
}
Push-Location $frontend
try { Write-Host "Starting Angular frontend with the existing npm start script."; & npm start }
finally { Pop-Location }
