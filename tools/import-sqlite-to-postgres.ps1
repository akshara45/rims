$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sqliteDriver = Join-Path $projectRoot "lib\sqlite-jdbc.jar"
$postgresDriver = Join-Path $projectRoot "target\dependency\postgresql-42.7.13.jar"
$helperSource = Join-Path $PSScriptRoot "SqliteToPostgresImport.java"
$helperOutput = Join-Path $projectRoot "target\sqlite-importer"

if (-not $env:RIMS_DB_URL -or -not $env:RIMS_DB_USER -or -not $env:RIMS_DB_PASSWORD) {
    throw "Set RIMS_DB_URL, RIMS_DB_USER, and RIMS_DB_PASSWORD before importing."
}
if (-not (Test-Path -LiteralPath $sqliteDriver)) { throw "SQLite JDBC driver is missing: $sqliteDriver" }
if (-not (Test-Path -LiteralPath $postgresDriver)) {
    throw "PostgreSQL JDBC driver is missing. Run: mvn -B dependency:copy-dependencies -DoutputDirectory=target/dependency"
}

New-Item -ItemType Directory -Force -Path $helperOutput | Out-Null
$classpath = "$projectRoot\target\classes;$postgresDriver;$sqliteDriver"
& javac -cp $classpath -d $helperOutput $helperSource
if ($LASTEXITCODE -ne 0) { throw "Could not compile the import helper." }
$sourcePath = $env:RIMS_SQLITE_SOURCE
if (-not $sourcePath) { $sourcePath = Join-Path $projectRoot "rental_system.db" }
$env:RIMS_SQLITE_SOURCE = $sourcePath
& java -cp "$helperOutput;$classpath" SqliteToPostgresImport
if ($LASTEXITCODE -ne 0) { throw "SQLite import failed; the PostgreSQL transaction was rolled back." }
