import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Exports the ImageGen background and its clean native fork into platform assets. */
public final class RenderStoreIcon {
    private static final Path ROOT = Path.of("assets", "source");
    private static final Path RES = Path.of("app", "src", "main", "res");
    private static final Color IVORY = new Color(0xF5, 0xE9, 0xCC);
    private static final double VIEWPORT = 108.0;
    private static final double MASK_VIEWPORT = 72.0;

    public static void main(String[] args) throws IOException {
        if (args.length != 0) throw new IllegalArgumentException("Usage: java tools/RenderStoreIcon.java");
        BufferedImage background = ImageIO.read(ROOT.resolve("intoniva-icon-background.png").toFile());
        if (background == null || background.getWidth() != background.getHeight() || background.getWidth() < 512) {
            throw new IOException("Expected a square ImageGen background of at least 512px");
        }
        for (int y = 0; y < background.getHeight(); y++) {
            for (int x = 0; x < background.getWidth(); x++) {
                if ((background.getRGB(x, y) >>> 24) != 255) throw new IOException("Background must be opaque");
            }
        }
        Path2D fork = fork();
        if (Math.abs(fork.getBounds2D().getCenterX() - 54) > 0.01
            || Math.abs(fork.getBounds2D().getCenterY() - 54) > 0.01
            || fork.getBounds2D().getHeight() < 48 || fork.getBounds2D().getHeight() > 66) {
            throw new IOException("Fork must be centered inside the adaptive-icon safe zone");
        }
        String data = pathData(fork);
        writeText(RES.resolve("drawable/ic_launcher_foreground.xml"), vector(data, "#F5E9CC", false));
        writeText(RES.resolve("drawable/ic_launcher_monochrome.xml"), vector(data, "#FFFFFF", false));
        writeText(RES.resolve("drawable/ic_notification.xml"), vector(data, "#FFFFFF", true));
        writePng(scale(background, 432), RES.resolve("drawable-nodpi/ic_launcher_background_art.png"));

        BufferedImage icon = icon(background, fork, 512);
        verifyIcon(icon);
        writePng(icon, Path.of("fastlane/metadata/android/en-US/images/icon.png"));
        writePng(icon, Path.of("fastlane/metadata/android/cs-CZ/images/icon.png"));
        writePng(icon, Path.of("docs/assets/icon.png"));
        writePng(icon(background, fork, 1024), Path.of("assets/intoniva-icon.png"));
        writePng(scale(icon, 32), Path.of("docs/assets/favicon-32.png"));
        writePng(scale(icon, 180), Path.of("docs/assets/apple-touch-icon.png"));
        if (Files.size(Path.of("docs/assets/icon.png")) > 1_048_576) throw new IOException("Play icon exceeds 1MB");

        banner(icon, 1600, 600, Path.of("assets/tuneitall-banner.png"), ROOT.resolve("tuneitall-banner.svg"));
        banner(icon, 1024, 500, Path.of("docs/assets/feature-graphic.png"), ROOT.resolve("tuneitall-feature-graphic.svg"));
        banner(icon, 1280, 640, Path.of("assets/social-preview.png"), null);
        preview(icon, Path.of(".reference/tmp/icon-mask-preview.png"));
        System.out.println("Full-square PNGs, adaptive layers, monochrome and notification icons verified.");
    }

    // Same silhouette as the generated master, centered at (54,54), 49dp tall.
    private static Path2D fork() {
        Path2D p = new Path2D.Double();
        p.moveTo(46.87, 29.5);
        p.curveTo(48.12, 29.5, 49.14, 30.52, 49.14, 31.77);
        p.lineTo(49.14, 51.98);
        p.curveTo(49.14, 55.08, 51.31, 57.5, 54, 57.5);
        p.curveTo(56.69, 57.5, 58.86, 55.08, 58.86, 51.98);
        p.lineTo(58.86, 31.77);
        p.curveTo(58.86, 30.52, 59.88, 29.5, 61.13, 29.5);
        p.curveTo(62.38, 29.5, 63.4, 30.52, 63.4, 31.77);
        p.lineTo(63.4, 51.98);
        p.curveTo(63.4, 57.08, 60.65, 60.24, 56.89, 61.7);
        p.curveTo(56.16, 62.02, 56.16, 62.86, 56.16, 63.98);
        p.lineTo(56.16, 76.34);
        p.curveTo(56.16, 77.53, 55.19, 78.5, 54, 78.5);
        p.curveTo(52.81, 78.5, 51.84, 77.53, 51.84, 76.34);
        p.lineTo(51.84, 63.98);
        p.curveTo(51.84, 62.86, 51.84, 62.02, 51.11, 61.7);
        p.curveTo(47.35, 60.24, 44.6, 57.08, 44.6, 51.98);
        p.lineTo(44.6, 31.77);
        p.curveTo(44.6, 30.52, 45.62, 29.5, 46.87, 29.5);
        p.closePath();
        return p;
    }

    private static String pathData(Path2D path) {
        StringBuilder data = new StringBuilder();
        double[] points = new double[6];
        for (PathIterator iterator = path.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            int type = iterator.currentSegment(points);
            data.append(switch (type) {
                case PathIterator.SEG_MOVETO -> "M";
                case PathIterator.SEG_LINETO -> "L";
                case PathIterator.SEG_CUBICTO -> "C";
                case PathIterator.SEG_CLOSE -> "Z";
                default -> throw new IllegalStateException("Unexpected path segment");
            });
            int count = type == PathIterator.SEG_CUBICTO ? 6 : type == PathIterator.SEG_CLOSE ? 0 : 2;
            for (int i = 0; i < count; i++) data.append(String.format(Locale.ROOT, "%.2f ", points[i]));
        }
        return data.toString().trim();
    }

    private static String vector(String data, String color, boolean notification) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="%sdp" android:height="%sdp"
                android:viewportWidth="%s" android:viewportHeight="%s">
                %s<path android:fillColor="%s" android:pathData="%s" />%s
            </vector>
            """.formatted(notification ? 24 : 108, notification ? 24 : 108,
                notification ? 54 : 108, notification ? 54 : 108,
                notification ? "<group android:translateX=\"-27\" android:translateY=\"-27\">" : "",
                color, data, notification ? "</group>" : "");
    }

    private static BufferedImage icon(BufferedImage background, Path2D fork, int size) {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            configure(g);
            // AdaptiveIconDrawable expands 108dp layers around the visible 72dp viewport.
            g.scale(size / MASK_VIEWPORT, size / MASK_VIEWPORT);
            g.translate(-18, -18);
            g.drawImage(background, 0, 0, (int) VIEWPORT, (int) VIEWPORT, null);
            g.setColor(IVORY);
            g.fill(fork);
        } finally { g.dispose(); }
        return output;
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try { configure(g); g.drawImage(image, 0, 0, size, size, null); }
        finally { g.dispose(); }
        return output;
    }

    private static void verifyIcon(BufferedImage icon) throws IOException {
        int minX = 512, minY = 512, maxX = 0, maxY = 0;
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) {
            int pixel = icon.getRGB(x, y);
            if ((pixel >>> 24) != 255) throw new IOException("Store icon must fill all square pixels");
            if ((pixel >>> 16 & 255) > 200 && (pixel >>> 8 & 255) > 200) {
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        }
        if (Math.abs((minX + maxX) / 2.0 - 255.5) > 1 || Math.abs((minY + maxY) / 2.0 - 255.5) > 1
            || maxY - minY < 340 || maxY - minY > 355 || maxX - minX > 140) {
            throw new IOException("Unexpected icon alignment or scale");
        }
    }

    private static void banner(BufferedImage icon, int width, int height, Path output, Path svg) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        int margin = width >= 1280 ? 92 : 62;
        int logoSize = height - 160;
        int logoX = width - margin - logoSize;
        int titleSize = width >= 1280 ? 100 : 72;
        int titleY = height / 2 - 30;
        try {
            configure(g);
            g.setColor(new Color(0x0B, 0x16, 0x13)); g.fillRect(0, 0, width, height);
            g.setColor(IVORY); g.setFont(new Font("Arial", Font.BOLD, titleSize));
            g.drawString("Intoniva", margin, titleY);
            g.setFont(new Font("Arial", Font.PLAIN, 28)); g.drawString("Tune. Time. Learn.", margin + 4, titleY + 62);
            g.setFont(new Font("Arial", Font.PLAIN, 20)); g.drawString("No ads. On-device music tools.", margin + 4, titleY + 128);
            Shape clip = g.getClip();
            g.clip(new RoundRectangle2D.Double(logoX, 80, logoSize, logoSize, logoSize * .25, logoSize * .25));
            g.drawImage(icon, logoX, 80, logoSize, logoSize, null); g.setClip(clip);
        } finally { g.dispose(); }
        writePng(image, output);
        if (svg != null) {
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("docs/assets/icon.png")));
            writeText(svg, """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  <rect width="100%%" height="100%%" fill="#0B1613"/>
                  <text x="%d" y="%d" fill="#F5E9CC" font-family="Arial,sans-serif" font-size="%d" font-weight="700">Intoniva</text>
                  <text x="%d" y="%d" fill="#F5E9CC" font-family="Arial,sans-serif" font-size="28">Tune. Time. Learn.</text>
                  <text x="%d" y="%d" fill="#F5E9CC" font-family="Arial,sans-serif" font-size="20">No ads. On-device music tools.</text>
                  <defs><clipPath id="icon"><rect x="%d" y="80" width="%d" height="%d" rx="%s"/></clipPath></defs>
                  <image x="%d" y="80" width="%d" height="%d" clip-path="url(#icon)" href="data:image/png;base64,%s"/>
                </svg>
                """.formatted(width, height, width, height, margin, titleY, titleSize,
                    margin + 4, titleY + 62, margin + 4, titleY + 128,
                    logoX, logoSize, logoSize, logoSize * .125, logoX, logoSize, logoSize, encoded));
        }
    }

    private static void preview(BufferedImage icon, Path output) throws IOException {
        BufferedImage sheet = new BufferedImage(720, 280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            configure(g); g.setColor(new Color(0xE9ECE8)); g.fillRect(0, 0, 720, 280);
            for (int i = 0; i < 3; i++) {
                int x = 32 + i * 240;
                Shape clip = i == 0 ? new Ellipse2D.Double(x, 24, 176, 176)
                    : new RoundRectangle2D.Double(x, 24, 176, 176, i == 1 ? 60 : 12, i == 1 ? 60 : 12);
                g.setClip(clip); g.drawImage(icon, x, 24, 176, 176, null); g.setClip(null);
                g.setColor(Color.BLACK); g.setFont(new Font("Arial", Font.PLAIN, 16));
                g.drawString(i == 0 ? "Circle" : i == 1 ? "Squircle" : "Rounded square", x, 236);
            }
        } finally { g.dispose(); }
        writePng(sheet, output);
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG writer unavailable");
        System.out.println(path.toAbsolutePath());
    }

    private static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent()); Files.writeString(path, text);
    }
}
