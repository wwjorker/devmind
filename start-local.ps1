$ErrorActionPreference = "Stop"

$projectDir = $PSScriptRoot
$backendScript = Join-Path $projectDir "backend\scripts\run-local.ps1"
$frontendScript = Join-Path $projectDir "frontend\scripts\run-local.ps1"

Write-Host "DevMind will open two terminals so backend and frontend logs stay separate." -ForegroundColor Cyan
Write-Host "Docker Desktop must already be running." -ForegroundColor Cyan

Start-Process `
    -FilePath "powershell.exe" `
    -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`"") `
    -WorkingDirectory (Join-Path $projectDir "backend")

Start-Process `
    -FilePath "powershell.exe" `
    -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$frontendScript`"") `
    -WorkingDirectory (Join-Path $projectDir "frontend")

Write-Host "Started. Open http://127.0.0.1:5173 after the backend shows port 8081." -ForegroundColor Green
