[CmdletBinding()]
param(
    [switch]$AllowUnsigned
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$apk = Join-Path $repoRoot "app/build/outputs/apk/debug/app-debug.apk"
$aab = Join-Path $repoRoot "app/build/outputs/bundle/release/app-release.aab"
$releaseManifest = Join-Path $repoRoot "app/build/intermediates/bundle_manifest/release/processApplicationManifestReleaseForBundle/AndroidManifest.xml"

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
$aaptName = if ($isWindowsHost) { "aapt2.exe" } else { "aapt2" }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName $aaptName) -PathType Leaf } |
    Select-Object -First 1
if ($null -eq $buildTools) {
    throw "aapt2 is unavailable under $sdkRoot/build-tools"
}
$aapt = Join-Path $buildTools.FullName $aaptName

if (-not (Test-Path -LiteralPath $releaseManifest -PathType Leaf)) {
    throw "Missing merged release manifest: $releaseManifest"
}
[xml]$manifest = Get-Content -LiteralPath $releaseManifest -Raw
$androidNamespace = "http://schemas.android.com/apk/res/android"
$applicationId = $manifest.manifest.package
$versionCode = $manifest.manifest.GetAttribute("versionCode", $androidNamespace)
$versionName = $manifest.manifest.GetAttribute("versionName", $androidNamespace)
$permissions = @($manifest.manifest.'uses-permission' | ForEach-Object {
    $_.GetAttribute("name", $androidNamespace)
})
if ($applicationId -ne "com.tuneitall.tuner") {
    throw "Unexpected application ID: $applicationId"
}
if ([string]::IsNullOrWhiteSpace($versionCode) -or [string]::IsNullOrWhiteSpace($versionName)) {
    throw "Release version metadata is missing"
}
foreach ($requiredPermission in @(
    "android.permission.RECORD_AUDIO",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.SYSTEM_ALERT_WINDOW"
)) {
    if ($requiredPermission -notin $permissions) {
        throw "$requiredPermission is missing"
    }
}
if ('android.permission.INTERNET' -in $permissions) {
    throw "INTERNET permission must not be present"
}
if ('com.google.android.gms.permission.AD_ID' -in $permissions) {
    throw "AD_ID permission must not be present"
}
$accessibilityService = @($manifest.manifest.application.service) | Where-Object {
    $_.GetAttribute("name", $androidNamespace) -eq "com.tuneitall.tuner.autoscroll.AutoScrollAccessibilityService"
} | Select-Object -First 1
if ($null -eq $accessibilityService -or $accessibilityService.GetAttribute("exported", $androidNamespace) -ne "true") {
    throw "Accessibility service must be exported for the Android system to bind it"
}
if ($accessibilityService.GetAttribute("permission", $androidNamespace) -ne "android.permission.BIND_ACCESSIBILITY_SERVICE") {
    throw "Accessibility service binding permission is missing"
}

$accessibility = & $aapt dump xmltree $apk --file res/xml/auto_scroll_accessibility_service.xml 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "aapt2 could not inspect Accessibility metadata: $accessibility"
}
if ($accessibility -notmatch 'canRetrieveWindowContent[^\r\n]*=false') {
    throw "Accessibility service must not retrieve window content"
}
if ($accessibility -notmatch 'canPerformGestures[^\r\n]*=true') {
    throw "Accessibility service gesture capability is missing"
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
# Android upload certificates are self-signed, so jarsigner -strict reports a
# PKIX error even for a valid Play bundle. Verify every entry, then pin signer.
$signingResult = & $jarsigner -verify -certs $aab 2>&1 | Out-String
$signed = $LASTEXITCODE -eq 0 -and $signingResult -match 'jar verified'
if (-not $signed -and -not $AllowUnsigned) {
    throw "Release bundle is unsigned"
}
if ($signed) {
    $keytool = (Get-Command keytool -ErrorAction Stop).Source
    $certificate = & $keytool -printcert -jarfile $aab 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the release signing certificate: $certificate"
    }
    $fingerprintMatch = [regex]::Match($certificate, 'SHA256:\s*([0-9A-F:]+)', 'IgnoreCase')
    if (-not $fingerprintMatch.Success) {
        throw "Release signing certificate has no SHA-256 fingerprint"
    }
    $fingerprint = $fingerprintMatch.Groups[1].Value.Replace(':', '').ToUpperInvariant()
    $expectedFingerprint = 'DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096'
    if ($fingerprint -ne $expectedFingerprint) {
        throw "Unexpected release signing certificate SHA-256: $fingerprint"
    }
}

Get-FileHash -Algorithm SHA256 -LiteralPath $apk, $aab |
    Select-Object Path, Hash
Write-Output "Verified package $applicationId version $versionName ($versionCode); signed release: $signed"
