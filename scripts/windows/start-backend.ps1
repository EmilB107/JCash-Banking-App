$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $root '.env'
$mvnw = Join-Path $root 'backend\mvnw.cmd'

if (-not (Test-Path $envFile)) { throw "Missing $envFile. Copy .env.example to .env and set DB_PASSWORD." }
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$' -and $Matches[1] -eq 'DB_PASSWORD') {
        $env:DB_PASSWORD = $Matches[2].Trim("'`"")
    }
}
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw "DB_PASSWORD is missing or empty in .env." }
if (-not (Test-Path $mvnw)) { throw "Missing Maven Wrapper: $mvnw" }

Push-Location (Join-Path $root 'backend')
try {
    Write-Host "Starting backend with Maven Wrapper."
    & $mvnw spring-boot:run
} finally { Pop-Location }
