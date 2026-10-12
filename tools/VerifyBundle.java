import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/** Verify every payload byte before trusting the bundle's signer or metadata. */
public final class VerifyBundle {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: VerifyBundle.java bundle.aab SHA256|unsigned");
        verify(Path.of(args[0]), args[1]);
        System.out.println("Verified all bundle entries: " + args[1]);
    }

    static void verify(Path bundle, String expected) throws Exception {
        boolean unsigned = expected.equals("unsigned");
        if (!unsigned && !expected.matches("(?i)[0-9a-f]{64}")) throw new SecurityException("Invalid signer pin");
        if (Files.size(bundle) > 512L * 1024 * 1024) throw new SecurityException("Bundle exceeds byte limit");
        Set<String> names = new HashSet<>();
        Set<String> signatureNames = new HashSet<>();
        int signatureFiles = 0;
        long total = 0;
        byte[] buffer = new byte[65536];
        // JarFile initializes signature metadata internally before returning payload streams.
        // Bound that metadata before letting the verifier parse it.
        long declaredTotal = 0;
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!names.add(entry.getName()) || names.size() > 100000 || entry.getSize() < 0 ||
                    entry.getSize() > 256L * 1024 * 1024) throw new SecurityException("Invalid bundle entry");
                declaredTotal += entry.getSize();
                if (declaredTotal > 1024L * 1024 * 1024) throw new SecurityException("Expanded bundle exceeds byte limit");
                String upper = entry.getName().toUpperCase(Locale.ROOT);
                if (upper.equals("META-INF/MANIFEST.MF") || upper.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)") ||
                    upper.startsWith("META-INF/SIG-")) {
                    long size = 0;
                    try (var input = zip.getInputStream(entry)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            size += count;
                            if (size > 1024 * 1024) throw new SecurityException("Excess signature metadata");
                        }
                    }
                }
            }
        }
        names.clear();
        try (JarFile jar = new JarFile(bundle.toFile(), true)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name) || names.size() > 100000) throw new SecurityException("Duplicate/excess bundle entries");
                if (entry.isDirectory()) continue;
                long size = 0;
                try (InputStream input = jar.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count;
                        total += count;
                        if (size > 256L * 1024 * 1024 || total > 1024L * 1024 * 1024) {
                            throw new SecurityException("Expanded bundle exceeds byte limit");
                        }
                    }
                }
                String upper = name.toUpperCase(Locale.ROOT);
                if (name.equals("META-INF/MANIFEST.MF")) continue;
                if (name.matches("META-INF/[A-Za-z0-9_-]+\\.(SF|RSA|DSA|EC)")) {
                    if (unsigned) throw new SecurityException("Unsigned bundle contains signature metadata");
                    signatureFiles++;
                    signatureNames.add(upper.substring(0, upper.lastIndexOf('.')));
                    continue;
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if (unsigned) {
                    if (signers != null) throw new SecurityException("Unsigned bundle contains signed payload");
                } else {
                    if (signers == null || signers.length != 1) throw new SecurityException("Unsigned/mixed entry: " + name);
                    var leaf = signers[0].getSignerCertPath().getCertificates().get(0);
                    String pin = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()));
                    if (!pin.equalsIgnoreCase(expected)) throw new SecurityException("Unexpected signer: " + name);
                }
            }
        }
        if (!names.contains("base/manifest/AndroidManifest.xml")) throw new SecurityException("Missing base manifest");
        if (names.stream().anyMatch(name -> name.endsWith("/manifest/AndroidManifest.xml") &&
            !name.equals("base/manifest/AndroidManifest.xml"))) throw new SecurityException("Unexpected bundle module");
        if (!unsigned && (signatureNames.size() != 1 || signatureFiles != 2)) {
            throw new SecurityException("Unexpected signature metadata");
        }
    }
}
