$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Users\wty\.jdks\openjdk-19.0.2"
$env:PATH = "$env:JAVA_HOME\bin;F:\maven\apache-maven-3.8.1\bin;$env:PATH"

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

& "F:\maven\apache-maven-3.8.1\bin\mvn.cmd" spring-boot:run
