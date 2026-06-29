$ErrorActionPreference = "Stop"

if ($env:JAVA_HOME) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
} else {
    Write-Warning "JAVA_HOME is not set. Make sure Java 17 or newer is available on PATH."
}

if (-not $env:DEVMIND_DB_URL) {
    $env:DEVMIND_DB_URL = "jdbc:mysql://localhost:3306/devmind?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
}
if (-not $env:DEVMIND_DB_USERNAME) {
    $env:DEVMIND_DB_USERNAME = "root"
}
if (-not $env:DEVMIND_DB_PASSWORD) {
    $env:DEVMIND_DB_PASSWORD = "root"
}
if (-not $env:DEVMIND_REDIS_HOST) {
    $env:DEVMIND_REDIS_HOST = "localhost"
}
if (-not $env:DEVMIND_REDIS_PORT) {
    $env:DEVMIND_REDIS_PORT = "6379"
}
if (-not $env:DEVMIND_REDIS_DATABASE) {
    $env:DEVMIND_REDIS_DATABASE = "1"
}

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
}
if (-not $maven) {
    throw "Maven is not available on PATH. Install Maven or run this project from IntelliJ IDEA."
}

& $maven.Source spring-boot:run
