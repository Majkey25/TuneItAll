import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class VerifyBundleTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]), "bundle-verifier-");
        try {
            Path key = root.resolve("test.p12");
            for (String alias : new String[]{"expected", "rogue"}) {
                run("keytool", "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keystore", key.toString(),
                    "-storetype", "PKCS12", "-storepass", "test-password", "-keypass", "test-password",
                    "-dname", "CN=" + alias, "-validity", "2", "-noprompt");
            }
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(key)) { store.load(input, "test-password".toCharArray()); }
            String pin = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(store.getCertificate("expected").getEncoded()));
            Map<String, byte[]> payload = new LinkedHashMap<>();
            payload.put("base/manifest/AndroidManifest.xml", new byte[]{1, 2, 3});
            payload.put("META-INF/services/payload", new byte[]{4, 5, 6});
            Path unsigned = root.resolve("unsigned.aab");
            write(unsigned, payload);
            VerifyBundle.verify(unsigned, "unsigned"); checks++;
            payload.put("feature/manifest/AndroidManifest.xml", new byte[]{1});
            Path extraModule = root.resolve("extra-module.aab");
            write(extraModule, payload);
            reject(extraModule, "unsigned");
            payload.remove("feature/manifest/AndroidManifest.xml");
            reject(unsigned, pin);
            Path signed = root.resolve("signed.aab");
            Files.copy(unsigned, signed);
            sign(key, signed, "expected");
            VerifyBundle.verify(signed, pin); checks++;
            reject(signed, "unsigned");
            Map<String, byte[]> signedEntries = read(signed);
            signedEntries.put("META-INF/services/unsigned", new byte[]{7});
            Path appended = root.resolve("appended.aab");
            write(appended, signedEntries);
            reject(appended, pin);
            signedEntries.remove("META-INF/services/unsigned");
            signedEntries.put("base/manifest/AndroidManifest.xml", new byte[]{9});
            Path tampered = root.resolve("tampered.aab");
            write(tampered, signedEntries);
            reject(tampered, pin);
            Path mixed = root.resolve("mixed.aab");
            Files.copy(signed, mixed);
            sign(key, mixed, "rogue");
            reject(mixed, pin);
            Path wrong = root.resolve("wrong.aab");
            Files.copy(unsigned, wrong);
            sign(key, wrong, "rogue");
            reject(wrong, pin);
            payload.put("payloadA", new byte[]{1});
            payload.put("payloadB", new byte[]{2});
            Path duplicate = root.resolve("duplicate.aab");
            write(duplicate, payload);
            byte[] bytes = Files.readAllBytes(duplicate);
            byte[] name = "payloadB".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            for (int index = 0; index <= bytes.length - name.length; index++) {
                if (java.util.Arrays.equals(bytes, index, index + name.length, name, 0, name.length)) {
                    bytes[index + name.length - 1] = 'A';
                }
            }
            Files.write(duplicate, bytes);
            reject(duplicate, "unsigned");
            System.out.println("Passed " + checks + " bundle signature checks");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void reject(Path file, String pin) throws Exception {
        try { VerifyBundle.verify(file, pin); }
        catch (SecurityException expected) { checks++; return; }
        throw new AssertionError("Accepted malicious bundle: " + file);
    }

    private static void sign(Path key, Path file, String alias) throws Exception {
        run("jarsigner", "-keystore", key.toString(), "-storepass", "test-password", file.toString(), alias);
    }

    private static void run(String tool, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        command.add(Path.of(System.getProperty("java.home"), "bin", tool + (windows ? ".exe" : "")).toString());
        command.addAll(java.util.List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError(tool + " failed: " + output);
    }

    private static Map<String, byte[]> read(Path file) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(file.toFile())) {
            var enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                var entry = enumeration.nextElement();
                try (var input = jar.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); }
            }
        }
        return entries;
    }

    private static void write(Path file, Map<String, byte[]> entries) throws Exception {
        try (var output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }
}
