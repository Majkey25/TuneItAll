import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class RenderStoreIcon {
    private static final int SIZE = 512;
    private static final double SCALE = SIZE / 108.0;
    private static final Path OUTPUT = Path.of(
        "fastlane", "metadata", "android", "en-US", "images", "icon.png"
    );

    private RenderStoreIcon() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: java tools/RenderStoreIcon.java");
        }

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setColor(new Color(0x10, 0x12, 0x10));
            graphics.fill(new RoundRectangle2D.Double(36, 36, 440, 440, 206, 206));

            graphics.scale(SCALE, SCALE);
            graphics.setColor(new Color(0x63, 0xD1, 0x7A));
            graphics.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D fork = new Path2D.Double();
            fork.moveTo(40, 25);
            fork.lineTo(40, 38);
            fork.curveTo(40, 46, 46, 52, 54, 52);
            fork.curveTo(62, 52, 68, 46, 68, 38);
            fork.lineTo(68, 25);
            fork.moveTo(54, 52);
            fork.lineTo(54, 74);
            fork.moveTo(48, 74);
            fork.lineTo(60, 74);
            graphics.draw(fork);

            graphics.setColor(new Color(0xF3, 0xF1, 0xEA));
            graphics.fillRect(28, 82, 52, 4);
            graphics.fillRect(28, 77, 3, 14);
            graphics.fillRect(37, 79, 3, 10);
            graphics.fillRect(46, 77, 3, 14);
            graphics.fillRect(53, 75, 3, 18);
            graphics.fillRect(62, 77, 3, 14);
            graphics.fillRect(71, 79, 3, 10);
            graphics.fillRect(77, 77, 3, 14);

            graphics.setColor(new Color(0x63, 0xD1, 0x7A));
            graphics.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D scroll = new Path2D.Double();
            scroll.moveTo(88, 76);
            scroll.lineTo(88, 90);
            scroll.moveTo(83, 85);
            scroll.lineTo(88, 90);
            scroll.lineTo(93, 85);
            graphics.draw(scroll);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(OUTPUT.getParent());
        if (!ImageIO.write(image, "png", OUTPUT.toFile())) {
            throw new IOException("PNG writer is unavailable");
        }
        BufferedImage rendered = ImageIO.read(OUTPUT.toFile());
        if (rendered == null
            || rendered.getWidth() != SIZE
            || rendered.getHeight() != SIZE
            || rendered.getRGB(0, 0) != 0x00000000
            || rendered.getRGB(256, 64) != 0xFF101210
            || rendered.getRGB(190, 142) != 0xFF63D17A
            || rendered.getRGB(256, 426) != 0xFFF3F1EA) {
            throw new IOException("Generated icon verification failed");
        }
        System.out.println(OUTPUT.toAbsolutePath());
    }
}
