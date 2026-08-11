[CmdletBinding()]
param(
    [switch]$AllowUnsigned
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$apk = Join-Path $repoRoot "app/build/outputs/apk/debug/app-debug.apk"
$aab = Join-Path $repoRoot "app/build/outputs/bundle/release/app-release.aab"

foreach ($artifact in @($apk, $aab)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "Missing build artifact: $artifact"
    }
    if ((Get-Item -LiteralPath $artifact).Length -eq 0) {
        throw "Empty build artifact: $artifact"
    }
}

$sdkRoot = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $localProperties = Join-Path $repoRoot "local.properties"
    $sdkLine = Select-String -LiteralPath $localProperties -Pattern '^sdk.dir=(.+)$' | Select-Object -First 1
    if ($null -eq $sdkLine) {
        throw "Android SDK path is unavailable"
    }
    $sdkRoot = $sdkLine.Matches[0].Groups[1].Value.Replace('\:', ':').Replace('/', [IO.Path]::DirectorySeparatorChar)
}

$isWindowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$analyzerName = if ($isWindowsHost) { "apkanalyzer.bat" } else { "apkanalyzer" }
$analyzer = Join-Path $sdkRoot "cmdline-tools/latest/bin/$analyzerName"
if (-not (Test-Path -LiteralPath $analyzer -PathType Leaf)) {
    throw "apkanalyzer is unavailable: $analyzer"
}

$applicationId = (& $analyzer manifest application-id $apk | Out-String).Trim()
$versionName = (& $analyzer manifest version-name $apk | Out-String).Trim()
$permissions = & $analyzer manifest permissions $apk | Out-String
if ($applicationId -ne "com.tuneitall.tuner") {
    throw "Unexpected application ID: $applicationId"
}
if ([string]::IsNullOrWhiteSpace($versionName)) {
    throw "Version name is missing"
}
if ($permissions -notmatch 'android.permission.RECORD_AUDIO') {
    throw "RECORD_AUDIO permission is missing"
}
if ($permissions -match 'android.permission.INTERNET') {
    throw "INTERNET permission must not be present"
}

$archive = [IO.Compression.ZipFile]::OpenRead($aab)
try {
    if ($null -eq $archive.GetEntry("base/manifest/AndroidManifest.xml")) {
        throw "Android App Bundle is missing its base manifest"
    }
} finally {
    $archive.Dispose()
}

$jarsigner = (Get-Command jarsigner -ErrorAction Stop).Source
$signingResult = & $jarsigner -verify -certs $aab 2>&1 | Out-String
$signed = $LASTEXITCODE -eq 0 -and $signingResult -notmatch 'jar is unsigned'
if (-not $signed -and -not $AllowUnsigned) {
    throw "Release bundle is unsigned"
}

Get-FileHash -Algorithm SHA256 -LiteralPath $apk, $aab |
    Select-Object Path, Hash
Write-Output "Verified package $applicationId version $versionName; signed release: $signed"
