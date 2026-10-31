[CmdletBinding()]
param([Parameter(Mandatory)][string]$BundletoolPath)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$verifier = Join-Path $PSScriptRoot "verify-release.ps1"
$outputs = Join-Path $repoRoot "app/build/outputs"
$scratchRoot = Join-Path $repoRoot ".reference/tmp"
$scratch = Join-Path $scratchRoot ("release-verifier-" + [guid]::NewGuid())
$source = Get-Content -LiteralPath (Join-Path $repoRoot "app/build.gradle.kts") -Raw
$version = [regex]::Match($source, 'versionName = "([^"]+)"').Groups[1].Value
$code = [regex]::Match($source, 'versionCode = ([0-9]+)').Groups[1].Value
if (-not $version -or -not $code) { throw "Source version is missing" }
$arguments = @{ AllowUnsigned = $true; BundletoolPath = $BundletoolPath; ExpectedVersion = $version; ExpectedVersionCode = $code }

function Assert-Rejected([hashtable]$Parameters, [string]$Reason, [string]$ExpectedError) {
    try { & $verifier @Parameters | Out-Null }
    catch {
        if ($ExpectedError -and -not $_.Exception.Message.StartsWith($ExpectedError)) { throw }
        Write-Output "Rejected: $Reason"
        return
    }
    throw "Accepted invalid artifact: $Reason"
}

try {
    & (Join-Path $PSScriptRoot "verify-release-policy.ps1") -SelfTest
    & $verifier @arguments
    $invalid = $arguments.Clone()
    $invalid.ExpectedVersion = "999.0.0"
    Assert-Rejected $invalid "wrong version"
    $invalid = $arguments.Clone()
    $invalid.ExpectedVersionCode = "999999"
    Assert-Rejected $invalid "wrong versionCode"
    $invalid = $arguments.Clone()
    $invalid.ExpectedSha256 = "0" * 64
    Assert-Rejected $invalid "wrong approved digest"

    New-Item -ItemType Directory -Force -Path (Join-Path $scratch "apk/debug"), (Join-Path $scratch "bundle/release") | Out-Null
    Copy-Item -LiteralPath (Join-Path $outputs "bundle/release/app-release.aab") -Destination (Join-Path $scratch "bundle/release/app-release.aab")
    Copy-Item -LiteralPath (Join-Path $outputs "apk/qa/app-qa.apk") -Destination (Join-Path $scratch "apk/debug/app-debug.apk")
    $invalid = $arguments.Clone()
    $invalid.ArtifactDirectory = $scratch
    Assert-Rejected $invalid "substituted QA APK/package"
    [IO.File]::WriteAllBytes((Join-Path $scratch "bundle/release/app-release.aab"), [byte[]]@(0))
    Assert-Rejected $invalid "malformed bundle after both signature attempts" "Bundle entry signature verification failed:"

    Copy-Item -LiteralPath (Join-Path $outputs "apk/debug/app-debug.apk") -Destination (Join-Path $scratch "apk/debug/app-debug.apk")
    $fixtureSource = Join-Path $scratch "ReleaseBundleFixture.java"
    [IO.File]::WriteAllText($fixtureSource, @'
import com.android.aapt.Resources.XmlNode;
import com.android.aapt.Resources.ResourceTable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class ReleaseBundleFixture {
    static int changes;
    public static void main(String[] args) throws Exception {
        try (var input = new ZipFile(args[0]);
             var output = new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (name.equals("META-INF/MANIFEST.MF") || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) continue;
                if (name.equals("BUNDLE-METADATA/com.android.tools/r8.json") && args[2].equals("r8-missing")) {
                    changes++; continue;
                }
                byte[] bytes;
                try (var stream = input.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                if (name.equals("BUNDLE-METADATA/com.android.tools/r8.json") && args[2].equals("r8-unoptimized")) {
                    String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    bytes = json.replace("\"isObfuscationEnabled\":true", "\"isObfuscationEnabled\":false")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (java.util.Arrays.equals(bytes, json.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        throw new AssertionError("Missing R8 optimization fixture field");
                    }
                    changes++;
                }
                if (name.equals("BUNDLE-METADATA/com.android.tools/r8.json") && args[2].equals("r8-low-score")) {
                    String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    bytes = json.replaceAll("\"noObfuscationPercentage\"\\s*:\\s*[0-9.]+", "\"noObfuscationPercentage\":99")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (java.util.Arrays.equals(bytes, json.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        throw new AssertionError("Missing R8 score fixture field");
                    }
                    changes++;
                }
                if (name.equals("base/resources.pb") && args[2].equals("qualified-window-content")) {
                    var table = ResourceTable.parseFrom(bytes).toBuilder();
                    for (var pkg : table.getPackageBuilderList()) {
                        for (var type : pkg.getTypeBuilderList()) {
                            for (var resource : type.getEntryBuilderList()) {
                                if (!type.getName().equals("xml") || !resource.getName().equals("auto_scroll_accessibility_service")) continue;
                                var value = resource.getConfigValue(0).toBuilder();
                                value.getConfigBuilder().setSdkVersion(35);
                                value.getValueBuilder().getItemBuilder().getFileBuilder()
                                    .setPath("res/xml-v35/auto_scroll_accessibility_service.xml");
                                resource.addConfigValue(value);
                            }
                        }
                    }
                    bytes = table.build().toByteArray();
                }
                if (name.equals("base/res/xml/auto_scroll_accessibility_service.xml") && args[2].equals("qualified-window-content")) {
                    var xml = XmlNode.parseFrom(bytes).toBuilder();
                    alter(xml, "window-content");
                    output.putNextEntry(new ZipEntry("base/res/xml-v35/auto_scroll_accessibility_service.xml"));
                    output.write(xml.build().toByteArray());
                    output.closeEntry();
                }
                if (name.equals("base/manifest/AndroidManifest.xml") ||
                    name.equals("base/res/xml/auto_scroll_accessibility_service.xml")) {
                    var xml = XmlNode.parseFrom(bytes).toBuilder();
                    alter(xml, args[2]);
                    bytes = xml.build().toByteArray();
                }
                output.putNextEntry(new ZipEntry(name));
                output.write(bytes);
                output.closeEntry();
            }
        }
        if (changes != 1) throw new AssertionError("Expected one fixture mutation, got " + changes);
    }
    static void alter(XmlNode.Builder node, String variant) {
        if (!node.hasElement()) return;
        var element = node.getElementBuilder();
        if (element.getName().equals("manifest") && variant.startsWith("sdk-")) {
            var permission = element.getChildList().stream()
                .filter(child -> child.getElement().getName().equals("uses-permission")).findFirst().orElseThrow().toBuilder();
            permission.getElementBuilder().setName("uses-permission-sdk-23");
            for (var attribute : permission.getElementBuilder().getAttributeBuilderList()) {
                if (attribute.getName().equals("name")) {
                    String value = variant.equals("sdk-internet") ? "android.permission.INTERNET" : "com.google.android.gms.permission.AD_ID";
                    attribute.setValue(value).getCompiledItemBuilder().getStrBuilder().setValue(value);
                }
            }
            element.addChild(permission); changes++;
        }
        if (element.getName().equals("application") && variant.equals("second-service")) {
            var service = element.getChildList().stream()
                .filter(child -> child.getElement().getName().equals("service")).findFirst().orElseThrow().toBuilder();
            for (var attribute : service.getElementBuilder().getAttributeBuilderList()) {
                if (attribute.getName().equals("name")) {
                    attribute.setValue("com.example.UnapprovedService").getCompiledItemBuilder()
                        .getStrBuilder().setValue("com.example.UnapprovedService");
                }
            }
            element.addChild(service); changes++;
        }
        if (element.getName().equals("accessibility-service") && variant.equals("swapped-attribute-ids")) {
            for (var attribute : element.getAttributeBuilderList()) {
                if (attribute.getName().equals("canRetrieveWindowContent")) attribute.setResourceId(0x0101050d);
                if (attribute.getName().equals("canPerformGestures")) attribute.setResourceId(0x01010385);
            }
            changes++;
        }
        if (element.getName().equals("accessibility-service") && variant.equals("extra-capability")) {
            var attribute = element.getAttributeList().stream()
                .filter(item -> item.getName().equals("canRetrieveWindowContent")).findFirst().orElseThrow().toBuilder();
            attribute.setName("canRequestFilterKeyEvents").setResourceId(0x010103d9).setValue("true")
                .getCompiledItemBuilder().getPrimBuilder().setBooleanValue(true);
            element.addAttribute(attribute); changes++;
        }
        if (element.getName().equals("accessibility-service") && variant.equals("window-content")) {
            for (var attribute : element.getAttributeBuilderList()) {
                if (attribute.getName().equals("canRetrieveWindowContent")) {
                    attribute.setValue("true").getCompiledItemBuilder().getPrimBuilder().setBooleanValue(true);
                    changes++;
                }
            }
        }
        if (element.getName().equals("service") && variant.equals("missing-metadata")) {
            for (int index = element.getChildCount() - 1; index >= 0; index--) {
                var child = element.getChild(index).getElement();
                if (child.getName().equals("meta-data") && child.getAttributeList().stream()
                    .anyMatch(attribute -> attribute.getName().equals("name") && attribute.getValue().equals("android.accessibilityservice"))) {
                    element.removeChild(index); changes++;
                }
            }
        }
        for (var child : element.getChildBuilderList()) alter(child, variant);
    }
}
'@)
    foreach ($case in @(
        @{ Variant = "r8-missing"; Error = "R8 optimization metadata is missing" },
        @{ Variant = "r8-unoptimized"; Error = "Release code and resource optimization must stay enabled" },
        @{ Variant = "r8-low-score"; Error = "Release DEX optimization is below 25%" },
        @{ Variant = "sdk-internet"; Error = "INTERNET permission must not be present" },
        @{ Variant = "sdk-ad-id"; Error = "AD_ID permission must not be present" },
        @{ Variant = "window-content"; Error = "Accessibility service must not retrieve window content" },
        @{ Variant = "qualified-window-content"; Error = "Accessibility service must not retrieve window content" },
        @{ Variant = "swapped-attribute-ids"; Error = "Accessibility service must not retrieve window content" },
        @{ Variant = "missing-metadata"; Error = "Accessibility service metadata is missing" },
        @{ Variant = "second-service"; Error = "Unexpected Accessibility service declarations" },
        @{ Variant = "extra-capability"; Error = "Accessibility capabilities differ from approved metadata" }
    )) {
        Copy-Item -LiteralPath (Join-Path $outputs "apk/debug/app-debug.apk") -Destination (Join-Path $scratch "apk/debug/app-debug.apk")
        & java --class-path $BundletoolPath $fixtureSource (Join-Path $outputs "bundle/release/app-release.aab") (Join-Path $scratch "bundle/release/app-release.aab") $case.Variant
        if ($LASTEXITCODE -ne 0) { throw "Could not create adversarial release fixture" }
        Assert-Rejected $invalid $case.Variant $case.Error
        if ($case.Variant.StartsWith('r8-')) { continue }

        # Keep the approved AAB intact while substituting a same-identity APK.
        $apkSet = Join-Path $scratch "mutated.apks"
        & java -jar $BundletoolPath build-apks "--bundle=$(Join-Path $scratch 'bundle/release/app-release.aab')" "--output=$apkSet" --mode=universal --overwrite --max-threads=2 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not create adversarial APK fixture" }
        $archive = [IO.Compression.ZipFile]::OpenRead($apkSet)
        try {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry("universal.apk"), (Join-Path $scratch "apk/debug/app-debug.apk"), $true)
        } finally { $archive.Dispose() }
        Copy-Item -LiteralPath (Join-Path $outputs "bundle/release/app-release.aab") -Destination (Join-Path $scratch "bundle/release/app-release.aab")
        $apkError = if ($case.Variant.StartsWith('sdk-')) { "APK and bundle permissions differ" } else { $case.Error }
        Assert-Rejected $invalid "APK-only $($case.Variant)" $apkError
    }
    Write-Output "Passed release identity + digest controls"
} finally {
    if (Test-Path -LiteralPath $scratch) {
        $resolved = (Resolve-Path -LiteralPath $scratch).Path
        $expectedRoot = [IO.Path]::GetFullPath($scratchRoot) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($expectedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe scratch path" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
