[CmdletBinding()]
param(
    [switch]$AllowUnsigned
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$isWindowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$gradleName = if ($isWindowsHost) { "gradlew.bat" } else { "gradlew" }
$gradle = Join-Path $repoRoot $gradleName

Push-Location $repoRoot
try {
    & $gradle clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleQa :app:assembleQaAndroidTest :app:bundleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle quality gate failed with exit code $LASTEXITCODE"
    }

    & (Join-Path $PSScriptRoot "verify-release.ps1") -AllowUnsigned:$AllowUnsigned
    if ($LASTEXITCODE -ne 0) {
        throw "Release verification failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
