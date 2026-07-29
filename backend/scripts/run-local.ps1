$ErrorActionPreference = "Stop"

$backendDir = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $backendDir

if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $javaHomeCandidate = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
        if (Test-Path -LiteralPath (Join-Path $javaHomeCandidate "bin\javac.exe")) {
            $env:JAVA_HOME = $javaHomeCandidate
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
    throw @"
DevMind requires JDK 17 or newer, but JAVA_HOME does not point to a JDK.
PowerShell example:
  `$env:JAVA_HOME='F:\IntelliJ IDEA 2024.1.2\jbr'
  `$env:Path="`$env:JAVA_HOME\bin;`$env:Path"
"@
}

$javaVersionOutput = & (Join-Path $env:JAVA_HOME "bin\java.exe") -version 2>&1
$javaVersionText = $javaVersionOutput -join " "
if ($javaVersionText -notmatch 'version "(?:1\.)?(\d+)') {
    throw "Unable to determine the Java version from JAVA_HOME=$env:JAVA_HOME"
}
if ([int]$Matches[1] -lt 17) {
    throw "DevMind requires JDK 17 or newer. Current JAVA_HOME reports Java $($Matches[1])."
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Starting DevMind MySQL (3307) and Redis (6380)..." -ForegroundColor Cyan
docker compose up -d devmind-mysql devmind-redis
if ($LASTEXITCODE -ne 0) {
    throw "Docker dependencies failed to start. Check whether ports 3307 or 6380 are already occupied."
}

$env:DEVMIND_DB_URL = if ($env:DEVMIND_DB_URL) {
    $env:DEVMIND_DB_URL
} else {
    "jdbc:mysql://localhost:3307/devmind?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
}
$env:DEVMIND_DB_USERNAME = if ($env:DEVMIND_DB_USERNAME) { $env:DEVMIND_DB_USERNAME } else { "root" }
$env:DEVMIND_DB_PASSWORD = if ($env:DEVMIND_DB_PASSWORD) { $env:DEVMIND_DB_PASSWORD } else { "root" }
$env:DEVMIND_REDIS_HOST = if ($env:DEVMIND_REDIS_HOST) { $env:DEVMIND_REDIS_HOST } else { "127.0.0.1" }
$env:DEVMIND_REDIS_PORT = if ($env:DEVMIND_REDIS_PORT) { $env:DEVMIND_REDIS_PORT } else { "6380" }
$env:DEVMIND_REDIS_DATABASE = if ($env:DEVMIND_REDIS_DATABASE) { $env:DEVMIND_REDIS_DATABASE } else { "1" }
$env:DEVMIND_AI_PROVIDER = if ($env:DEVMIND_AI_PROVIDER) { $env:DEVMIND_AI_PROVIDER } else { "mock" }
$env:DEVMIND_AI_EMBEDDING_PROVIDER = if ($env:DEVMIND_AI_EMBEDDING_PROVIDER) {
    $env:DEVMIND_AI_EMBEDDING_PROVIDER
} else {
    "local-sparse-vector"
}
$env:DEVMIND_AI_RERANK_PROVIDER = if ($env:DEVMIND_AI_RERANK_PROVIDER) {
    $env:DEVMIND_AI_RERANK_PROVIDER
} else {
    "none"
}
$env:DEVMIND_VECTOR_STORE_PROVIDER = if ($env:DEVMIND_VECTOR_STORE_PROVIDER) {
    $env:DEVMIND_VECTOR_STORE_PROVIDER
} else {
    "mysql-json"
}

Write-Host "Starting Spring Boot at http://127.0.0.1:8081 with provider=$env:DEVMIND_AI_PROVIDER" -ForegroundColor Green
& ".\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
