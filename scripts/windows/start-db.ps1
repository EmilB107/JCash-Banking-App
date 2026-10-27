$ErrorActionPreference = 'Stop'

if (-not (Get-Command pg_isready -ErrorAction SilentlyContinue)) {
    throw "pg_isready is required. Install PostgreSQL client tools and add them to PATH."
}
$service = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue |
    Where-Object Status -ne 'Running' | Select-Object -First 1
if ($service) {
    Write-Host "Starting PostgreSQL service $($service.Name) (administrator privileges may be required)."
    Start-Service -Name $service.Name
}
pg_isready -h localhost -p 5432 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL is not ready on localhost:5432. Start it using the service manager for your installation."
}
Write-Host "PostgreSQL is ready on localhost:5432."
