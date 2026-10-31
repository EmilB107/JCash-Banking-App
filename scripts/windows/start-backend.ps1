$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $root '.env'
$mvnw = Join-Path $root 'backend\mvnw.cmd'

$required = @('DB_PASSWORD', 'ACTUATOR_USERNAME', 'ACTUATOR_PASSWORD')

if (-not (Test-Path $envFile)) { throw "Missing $envFile. Copy .env.example to .env and set $($required -join ', ')." }
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$' -and $required -contains $Matches[1]) {
        Set-Item -Path "env:$($Matches[1])" -Value $Matches[2].Trim("'`"")
    }
}
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace((Get-Item -Path "env:$name" -ErrorAction SilentlyContinue).Value)) {
        throw "$name is missing or empty in .env."
    }
}
if (-not (Test-Path $mvnw)) { throw "Missing Maven Wrapper: $mvnw" }

Push-Location (Join-Path $root 'backend')
try {
    Write-Host "Starting backend with Maven Wrapper."
    & $mvnw spring-boot:run
} finally { Pop-Location }
