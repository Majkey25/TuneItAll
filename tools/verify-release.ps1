[CmdletBinding()]
param(
    [switch]$AllowUnsigned,
    [switch]$RequireUnsigned,
    [string]$BundletoolPath,
    [string]$ExpectedVersion,
    [string]$ExpectedVersionCode,
    [string]$ExpectedSha256,
    [string]$ArtifactDirectory
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($ArtifactDirectory)) { $ArtifactDirectory = Join-Path $repoRoot "app/build/outputs" }
$apk = Join-Path $ArtifactDirectory "apk/debug/app-debug.apk"
$aab = Join-Path $ArtifactDirectory "bundle/release/app-release.aab"
$androidNamespace = "http://schemas.android.com/apk/res/android"

function Read-ArtifactXml([string[]]$Text) {
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [System.Xml.XmlReader]::Create([IO.StringReader]::new(($Text -join "`n")), $settings)
    $xml = [xml]::new()
    try { $xml.Load($reader) } finally { $reader.Dispose() }
    return $xml
}

function Get-AccessibilityResource([xml]$Manifest, [string]$ResourcePattern) {
    $services = @($Manifest.manifest.application.SelectNodes('service') | Where-Object {
        $_.GetAttribute("permission", $androidNamespace) -ceq "android.permission.BIND_ACCESSIBILITY_SERVICE"
    })
    if ($services.Count -ne 1 -or
        $services[0].GetAttribute("name", $androidNamespace) -cne "com.tuneitall.tuner.autoscroll.AutoScrollAccessibilityService") {
        throw "Unexpected Accessibility service declarations"
    }
    if ($services[0].GetAttribute("exported", $androidNamespace) -cne "true") {
        throw "Accessibility service must be exported for the Android system to bind it"
    }
    $metadata = @($services[0].SelectNodes('meta-data') | Where-Object {
        $_.GetAttribute("name", $androidNamespace) -ceq "android.accessibilityservice"
    })
    if ($metadata.Count -ne 1 -or $metadata[0].GetAttribute("resource", $androidNamespace) -cnotmatch $ResourcePattern) {
        throw "Accessibility service metadata is missing or invalid"
    }
    return $metadata[0].GetAttribute("resource", $androidNamespace)
}

function Assert-AccessibilityXml([string]$ApkPath, [string[]]$ResourcePaths, [string]$DescriptionId) {
    $approvedAttributes = @(
        "description(0x01010020)=@$DescriptionId", 'accessibilityEventTypes(0x01010380)=0x00000020',
        'accessibilityFeedbackType(0x01010382)=0x00000010', 'notificationTimeout(0x01010383)=0',
        'canRetrieveWindowContent(0x01010385)=false', 'canPerformGestures(0x0101050d)=true'
    )
    foreach ($resourcePath in $ResourcePaths) {
        $accessibility = & $aapt dump xmltree $ApkPath --file $resourcePath 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) { throw "aapt2 could not inspect Accessibility metadata: $accessibility" }
        $attributes = @([regex]::Matches($accessibility, '(?m)^\s+A: (.+\(0x[0-9a-f]+\))=(.+)\r?$') | ForEach-Object {
            $name = $_.Groups[1].Value
            if (-not $name.StartsWith("${androidNamespace}:", [StringComparison]::Ordinal)) {
                throw "Accessibility attribute has an unexpected namespace"
            }
            $name.Substring($androidNamespace.Length + 1) + '=' + $_.Groups[2].Value.Trim()
        })
        if ('canRetrieveWindowContent(0x01010385)=false' -cnotin $attributes) {
            throw "Accessibility service must not retrieve window content"
        }
        if ('canPerformGestures(0x0101050d)=true' -cnotin $attributes) {
            throw "Accessibility service gesture capability is missing"
        }
        if ($attributes.Count -ne $approvedAttributes.Count -or
            (Compare-Object $approvedAttributes $attributes -CaseSensitive)) {
            throw "Accessibility capabilities differ from approved metadata"
        }
    }
}

foreach ($artifact in @($apk, $aab)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "Missing build artifact: $artifact"
    }
    $length = (Get-Item -LiteralPath $artifact).Length
    if ($length -le 0 -or $length -gt 512L * 1024 * 1024) {
        throw "Empty or oversized build artifact: $artifact"
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
$analyzerName = if ($isWindowsHost) { "apkanalyzer.bat" } else { "apkanalyzer" }
$apkAnalyzer = Join-Path $sdkRoot "cmdline-tools/latest/bin/$analyzerName"
if (-not (Test-Path -LiteralPath $apkAnalyzer -PathType Leaf)) {
    throw "apkanalyzer is unavailable; install Android SDK Command-Line Tools (latest)"
}

if ($ExpectedSha256 -and (Get-FileHash -Algorithm SHA256 -LiteralPath $aab).Hash -ne $ExpectedSha256) {
    throw "Bundle differs from approved SHA-256"
}
$expectedFingerprint = 'DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096'
$signer = if ($RequireUnsigned) { 'unsigned' } else { $expectedFingerprint }
try {
    # Windows PowerShell treats native stderr as errors; the verifier's exit code decides success.
    $ErrorActionPreference = "Continue"
    $global:LASTEXITCODE = -1
    $signatureResult = & java (Join-Path $PSScriptRoot "VerifyBundle.java") $aab $signer 2>&1 | Out-String
    if ($global:LASTEXITCODE -ne 0 -and $AllowUnsigned -and -not $RequireUnsigned) {
        $signer = 'unsigned'
        $signatureResult = & java (Join-Path $PSScriptRoot "VerifyBundle.java") $aab $signer 2>&1 | Out-String
    }
} finally {
    $ErrorActionPreference = "Stop"
}
if ($global:LASTEXITCODE -ne 0) { throw "Bundle entry signature verification failed: $signatureResult" }
Write-Output $signatureResult.Trim()

$archive = [IO.Compression.ZipFile]::OpenRead($aab)
try {
    $metadata = $archive.GetEntry('BUNDLE-METADATA/com.android.tools/r8.json')
    if ($null -eq $metadata -or $metadata.Length -gt 1MB) { throw 'R8 optimization metadata is missing or oversized' }
    $reader = [IO.StreamReader]::new($metadata.Open())
    try { $optimization = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if ($optimization.options.isObfuscationEnabled -ne $true -or
        $optimization.options.isOptimizationsEnabled -ne $true -or
        $optimization.options.isShrinkingEnabled -ne $true -or
        $optimization.resourceOptimization.isOptimizedShrinkingEnabled -ne $true) {
        throw 'Release code and resource optimization must stay enabled'
    }
    foreach ($metric in @('noObfuscationPercentage', 'noOptimizationPercentage', 'noShrinkingPercentage')) {
        $value = $optimization.stats.$metric
        if (($value -isnot [double] -and $value -isnot [long] -and $value -isnot [int]) -or
            -not [double]::IsFinite([double]$value) -or $value -lt 0 -or $value -gt 75) {
            throw "Release DEX optimization is below 25%: $metric"
        }
    }
} finally { $archive.Dispose() }

if ([string]::IsNullOrWhiteSpace($BundletoolPath)) {
    $BundletoolPath = Join-Path $repoRoot ".reference/tmp/bundletool-all-1.18.3.jar"
    if (-not (Test-Path -LiteralPath $BundletoolPath -PathType Leaf)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $BundletoolPath) | Out-Null
        Invoke-WebRequest -Uri "https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar" -OutFile $BundletoolPath
    }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $BundletoolPath).Hash -ne
    'A099CFA1543F55593BC2ED16A70A7C67FE54B1747BB7301F37FDFD6D91028E29') {
    throw "Unexpected bundletool SHA-256"
}
$manifestText = & java -jar $BundletoolPath dump manifest "--bundle=$aab" --module=base
if ($LASTEXITCODE -ne 0) { throw "Could not inspect exact bundle manifest" }
$manifest = Read-ArtifactXml $manifestText
$applicationId = $manifest.manifest.package
$versionCode = $manifest.manifest.GetAttribute("versionCode", $androidNamespace)
$versionName = $manifest.manifest.GetAttribute("versionName", $androidNamespace)
$permissions = @($manifest.manifest.SelectNodes('uses-permission | uses-permission-sdk-23') | ForEach-Object {
    $_.GetAttribute("name", $androidNamespace)
})
if ($applicationId -ne "com.tuneitall.tuner") {
    throw "Unexpected application ID: $applicationId"
}
if ([string]::IsNullOrWhiteSpace($versionCode) -or [string]::IsNullOrWhiteSpace($versionName)) {
    throw "Release version metadata is missing"
}
if (($ExpectedVersion -and $versionName -ne $ExpectedVersion) -or
    ($ExpectedVersionCode -and $versionCode -ne $ExpectedVersionCode)) {
    throw "Bundle version differs from approved release"
}
if ('android.permission.INTERNET' -in $permissions) {
    throw "INTERNET permission must not be present"
}
if ('com.google.android.gms.permission.AD_ID' -in $permissions) {
    throw "AD_ID permission must not be present"
}
$badging = & $aapt dump badging $apk 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") {
    throw "Could not inspect exact APK identity"
}
if ($Matches[1] -ne $applicationId -or $Matches[2] -ne $versionCode -or $Matches[3] -ne $versionName) {
    throw "APK and bundle identity/version differ"
}
$apkPermissions = @([regex]::Matches($badging, "uses-permission(?:-sdk-23)?: name='([^']+)'") |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
if (Compare-Object ($permissions | Sort-Object -Unique) $apkPermissions) {
    throw "APK and bundle permissions differ"
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
$resource = (Get-AccessibilityResource $manifest '^@xml/[a-z0-9_]+$').Substring(1)
$resources = & java -jar $BundletoolPath dump resources "--bundle=$aab" "--resource=$resource" --values
if ($LASTEXITCODE -ne 0) { throw "Could not resolve bundle Accessibility resource" }
$resourcePaths = @($resources | Where-Object { $_ -match '^\s+\S' } | ForEach-Object {
    if ($_ -notmatch '^\s+[^\r\n]+ - \[FILE\] (res/xml(?:-[a-zA-Z0-9_-]+)?/[a-z0-9_]+\.xml)$') {
        throw "Unsupported bundle Accessibility resource value"
    }
    $Matches[1]
} | Sort-Object -Unique)
if ($resourcePaths.Count -eq 0) { throw "Bundle Accessibility resource has no XML files" }
$descriptionResource = & java -jar $BundletoolPath dump resources "--bundle=$aab" --resource=string/auto_scroll_accessibility_description
if ($LASTEXITCODE -ne 0 -or ($descriptionResource -join "`n") -cnotmatch '(?m)^(0x[0-9a-f]+) - string/auto_scroll_accessibility_description$') {
    throw "Accessibility description resource is missing"
}
$descriptionId = $Matches[1]

# The preview APK is built separately; its capabilities must pass independently.
$apkManifestText = & $apkAnalyzer manifest print $apk
if ($LASTEXITCODE -ne 0) { throw "Could not inspect exact APK manifest" }
$apkResourceId = (Get-AccessibilityResource (Read-ArtifactXml $apkManifestText) '^@ref/0x[0-9a-f]+$').Substring(5)
$apkResources = & $aapt dump resources $apk 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { throw "Could not inspect exact APK resources" }
$apkResource = [regex]::Match($apkResources,
    "(?m)^ +resource $apkResourceId xml/[a-z0-9_]+\r?\n((?: +\([^\r\n]*\)[^\r\n]*\r?\n)+)")
if (-not $apkResource.Success) { throw "APK Accessibility resource has no XML values" }
$apkResourcePaths = @($apkResource.Groups[1].Value.TrimEnd() -split '\r?\n' | ForEach-Object {
    if ($_ -cnotmatch '^ +\([^\r\n]*\) \(file\) (res/xml(?:-[a-zA-Z0-9_-]+)?/[a-z0-9_]+\.xml) type=XML$') {
        throw "Unsupported APK Accessibility resource value"
    }
    $Matches[1]
} | Sort-Object -Unique)
if ($apkResources -cnotmatch '(?m)^ +resource (0x[0-9a-f]+) string/auto_scroll_accessibility_description\r?$') {
    throw "APK Accessibility description resource is missing"
}
Assert-AccessibilityXml $apk $apkResourcePaths $Matches[1]

$scratchRoot = Join-Path $repoRoot ".reference/tmp"
$scratch = Join-Path $scratchRoot ("aab-verifier-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $scratch -Force | Out-Null
try {
    $apkSet = Join-Path $scratch "universal.apks"
    $bundleApk = Join-Path $scratch "universal.apk"
    & java -jar $BundletoolPath build-apks "--bundle=$aab" "--output=$apkSet" --mode=universal "--aapt2=$aapt" --max-threads=2 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not generate bundle verification APK" }
    $archive = [IO.Compression.ZipFile]::OpenRead($apkSet)
    try {
        $entry = $archive.GetEntry("universal.apk")
        if ($null -eq $entry) { throw "Bundle verification APK is missing" }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $bundleApk)
    } finally { $archive.Dispose() }
    Assert-AccessibilityXml $bundleApk $resourcePaths $descriptionId
} finally {
    $resolved = (Resolve-Path -LiteralPath $scratch).Path
    $expectedRoot = [IO.Path]::GetFullPath($scratchRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($expectedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe scratch path" }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

Get-FileHash -Algorithm SHA256 -LiteralPath $apk, $aab |
    Select-Object Path, Hash
Write-Output "Verified exact package $applicationId version $versionName ($versionCode); signer: $signer"
