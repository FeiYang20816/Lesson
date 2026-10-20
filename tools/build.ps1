param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
Push-Location $projectRoot
try {
    $gradleCommand = Join-Path $projectRoot '.tools\gradle-8.11.1\bin\gradle.bat'
    if (-not (Test-Path -LiteralPath $gradleCommand)) { $gradleCommand = Join-Path $projectRoot 'gradlew.bat' }
    & $gradleCommand @Tasks '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
} finally { Pop-Location }
