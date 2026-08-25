import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class RenderStoreScreenshots {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int PHONE_X = 170;
    private static final int PHONE_Y = 330;
    private static final int PHONE_WIDTH = 740;
    private static final int PHONE_HEIGHT = 1550;
    private static final int SCREEN_X = 200;
    private static final int SCREEN_Y = 370;
    private static final int SCREEN_WIDTH = 680;
    private static final Color BACKGROUND = new Color(0x10, 0x12, 0x10);
    private static final Color GREEN = new Color(0x63, 0xD1, 0x7A);
    private static final Color TEXT = new Color(0xF4, 0xF1, 0xEA);
    private static final Color SECONDARY = new Color(0xB9, 0xBD, 0xB9);
    private static final Path CAPTURE_DIR = Path.of(".reference", "tmp", "store-captures");
    private static final Path OUTPUT_DIR = Path.of(".reference", "tmp", "store-rendered");

    private static final Slide[] SLIDES = {
        new Slide("01-tuner.png", "1_tuner.png", "Tune with confidence",
            "Universal and unplugged-electric profiles keep quiet notes stable."),
        new Slide("02-chromatic.png", "2_chromatic.png", "Every note. Every instrument.",
            "Chromatic mode and adjustable A4 reference keep any setup precise."),
        new Slide("03-tunings.png", "3_tunings.png", "Your tunings, ready",
            "Search 38 presets, save favorites and build custom tunings offline."),
        new Slide("04-metronome.png", "4_metronome.png", "Stay perfectly in time",
            "A clean mechanical metronome from 20 to 400 BPM."),
        new Slide("05-chords.png", "5_chords.png", "Playable chords, offline",
            "Canonical guitar and ukulele shapes with clear fingering."),
        new Slide("06-song-chords.png", "6_song_chords.png", "Hear the song structure",
            "Analyze a local song, follow chords and transpose instantly."),
        new Slide("07-trainer.png", "7_trainer.png", "Train your musical ear",
            "Hear a note, choose the answer and track progress locally."),
        new Slide("08-auto-scroll.png", "8_auto_scroll.png", "Scroll without your hands",
            "Floating Start, Stop and speed controls work across Android."),
    };

    private RenderStoreScreenshots() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: java tools/RenderStoreScreenshots.java");
        }
        Files.createDirectories(OUTPUT_DIR);
        List<BufferedImage> rendered = new ArrayList<>();
        for (Slide slide : SLIDES) {
            BufferedImage capture = ImageIO.read(CAPTURE_DIR.resolve(slide.capture()).toFile());
            if (capture == null || capture.getWidth() != 1080 || capture.getHeight() < 2200) {
                throw new IOException("Invalid emulator capture: " + slide.capture());
            }
            BufferedImage image = render(slide, capture);
            Path output = OUTPUT_DIR.resolve(slide.output());
            if (!ImageIO.write(image, "png", output.toFile())) {
                throw new IOException("PNG writer is unavailable");
            }
            BufferedImage verified = ImageIO.read(output.toFile());
            if (verified == null || verified.getWidth() != WIDTH || verified.getHeight() != HEIGHT) {
                throw new IOException("Generated screenshot verification failed: " + output);
            }
            rendered.add(image);
            System.out.println(output.toAbsolutePath());
        }
        writeContactSheet(rendered);
    }

    private static BufferedImage render(Slide slide, BufferedImage capture) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            drawBackground(graphics);
            drawCopy(graphics, slide);
            drawPhone(graphics, capture);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static void drawBackground(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.10f));
        graphics.setColor(GREEN);
        graphics.fill(new Ellipse2D.Double(670, 80, 520, 520));
        graphics.fill(new Ellipse2D.Double(-210, 1420, 620, 620));
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(GREEN);
        graphics.fillRoundRect(70, 54, 54, 8, 8, 8);
    }

    private static void drawCopy(Graphics2D graphics, Slide slide) {
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 20));
        graphics.setColor(GREEN);
        graphics.drawString("INTONIVA", 142, 68);

        graphics.setFont(new Font("Segoe UI", Font.BOLD, 56));
        graphics.setColor(TEXT);
        int headlineBottom = drawWrapped(graphics, slide.headline(), 70, 130, 940, 64, 2);

        graphics.setFont(new Font("Segoe UI", Font.PLAIN, 28));
        graphics.setColor(SECONDARY);
        drawWrapped(graphics, slide.subline(), 70, headlineBottom + 38, 940, 38, 2);
    }

    private static void drawPhone(Graphics2D graphics, BufferedImage capture) {
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.28f));
        graphics.setColor(Color.BLACK);
        graphics.fill(new RoundRectangle2D.Double(
            PHONE_X + 12, PHONE_Y + 18, PHONE_WIDTH, PHONE_HEIGHT, 92, 92
        ));
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(0x05, 0x06, 0x05));
        graphics.fill(new RoundRectangle2D.Double(PHONE_X, PHONE_Y, PHONE_WIDTH, PHONE_HEIGHT, 92, 92));
        graphics.setStroke(new BasicStroke(3f));
        graphics.setColor(new Color(0x38, 0x40, 0x3A));
        graphics.draw(new RoundRectangle2D.Double(PHONE_X, PHONE_Y, PHONE_WIDTH, PHONE_HEIGHT, 92, 92));

        int screenHeight = (int) Math.round(capture.getHeight() * (SCREEN_WIDTH / (double) capture.getWidth()));
        Shape oldClip = graphics.getClip();
        graphics.clip(new RoundRectangle2D.Double(SCREEN_X, SCREEN_Y, SCREEN_WIDTH, screenHeight, 48, 48));
        graphics.drawImage(capture, SCREEN_X, SCREEN_Y, SCREEN_WIDTH, screenHeight, null);
        graphics.setClip(oldClip);

        graphics.setColor(new Color(0x26, 0x2A, 0x26));
        graphics.fillRoundRect(440, 346, 200, 10, 10, 10);
        graphics.fillOval(662, 341, 18, 18);
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(GREEN);
        graphics.draw(new RoundRectangle2D.Double(SCREEN_X, SCREEN_Y, SCREEN_WIDTH, screenHeight, 48, 48));
    }

    private static int drawWrapped(
        Graphics2D graphics,
        String text,
        int x,
        int firstBaseline,
        int maxWidth,
        int lineHeight,
        int maxLines
    ) {
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() > maxLines) {
            throw new IllegalArgumentException("Copy exceeds " + maxLines + " lines: " + text);
        }
        int baseline = firstBaseline;
        for (String value : lines) {
            graphics.drawString(value, x, baseline);
            baseline += lineHeight;
        }
        return baseline - lineHeight;
    }

    private static void writeContactSheet(List<BufferedImage> images) throws IOException {
        BufferedImage sheet = new BufferedImage(1080, 960, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            configure(graphics);
            for (int index = 0; index < images.size(); index++) {
                int x = (index % 4) * 270;
                int y = (index / 4) * 480;
                graphics.drawImage(images.get(index), x, y, 270, 480, null);
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(sheet, "png", OUTPUT_DIR.resolve("contact-sheet.png").toFile());
    }

    private record Slide(String capture, String output, String headline, String subline) {}
}
