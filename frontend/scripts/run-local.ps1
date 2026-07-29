$ErrorActionPreference = "Stop"

$frontendDir = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $frontendDir

if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    throw "npm was not found. Install Node.js 20 or newer before starting the DevMind frontend."
}

if (-not (Test-Path -LiteralPath (Join-Path $frontendDir "node_modules"))) {
    Write-Host "Installing frontend dependencies..." -ForegroundColor Cyan
    npm install
    if ($LASTEXITCODE -ne 0) {
        throw "npm install failed."
    }
}

Write-Host "Starting Vite at http://127.0.0.1:5173" -ForegroundColor Green
npm run dev
exit $LASTEXITCODE
