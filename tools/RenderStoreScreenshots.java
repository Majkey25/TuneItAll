import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
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
    private static final int PHONE_Y = 410;
    private static final int PHONE_WIDTH = 740;
    private static final int SCREEN_X = 200;
    private static final int SCREEN_Y = 450;
    private static final int SCREEN_WIDTH = 680;
    private static final int SYSTEM_BAR_HEIGHT = 108;
    private static final Color BACKGROUND = new Color(0xF7, 0xF5, 0xF1);
    private static final Color TEXT = new Color(0x11, 0x13, 0x10);
    private static final Color SECONDARY = new Color(0x5F, 0x65, 0x60);
    private static final Path CAPTURE_DIR = Path.of(".reference", "tmp", "store-captures");
    private static final Path OUTPUT_DIR = Path.of(".reference", "tmp", "store-rendered");
    private static final Path ICON_PATH = Path.of("docs", "assets", "icon.png");

    private static final Slide[] SLIDES = {
        new Slide("1_tuner.png", "Tune with confidence",
            "Quiet-note profiles keep acoustic and unplugged electric guitars stable.",
            "La\u010fte bez dohad\u016f", "Citliv\u00e9 profily udr\u017e\u00ed i tich\u00e9 t\u00f3ny stabiln\u00ed."),
        new Slide("2_chromatic.png", "Every note. Every instrument.",
            "Chromatic mode and adjustable A4 reference keep any setup precise.",
            "Ka\u017ed\u00fd t\u00f3n. Ka\u017ed\u00fd n\u00e1stroj.",
            "Chromatick\u00fd re\u017eim a vlastn\u00ed A4 pro p\u0159esn\u00e9 lad\u011bn\u00ed."),
        new Slide("3_tunings.png", "Your tunings, ready",
            "Search 49 presets, save favorites and build custom tunings offline.",
            "Va\u0161e lad\u011bn\u00ed po ruce",
            "Hledejte v 49 lad\u011bn\u00edch, ukl\u00e1dejte obl\u00edben\u00e1 a tvo\u0159te vlastn\u00ed."),
        new Slide("4_metronome.png", "Stay perfectly in time",
            "A clean mechanical metronome from 20 to 400 BPM.",
            "Dr\u017ete p\u0159esn\u00e9 tempo", "\u010cist\u00fd mechanick\u00fd metronom od 20 do 400 BPM."),
        new Slide("5_chords.png", "Playable chords, offline",
            "Reviewed guitar and ukulele shapes with clear fingering.",
            "Hrateln\u00e9 akordy offline", "Ov\u011b\u0159en\u00e9 hmaty pro kytaru a ukulele."),
        new Slide("6_song_chords.png", "Follow the chords of a song",
            "Analyze a local recording, follow its timeline and transpose.",
            "Sledujte akordy skladby",
            "Analyzujte m\u00edstn\u00ed soubor, sledujte \u010dasovou osu a transponujte."),
        new Slide("7_trainer.png", "Train your musical ear",
            "Recognize notes and chords while progress stays on your phone.",
            "Tr\u00e9nujte hudebn\u00ed sluch",
            "Pozn\u00e1vejte t\u00f3ny a akordy, v\u00fdsledky z\u016fst\u00e1vaj\u00ed v telefonu."),
        new Slide("8_auto_scroll.png", "Scroll without your hands",
            "Floating Start, Stop and speed controls work across Android.",
            "Rolujte bez rukou", "Plovouc\u00ed Start, Stop a rychlost funguj\u00ed nap\u0159\u00ed\u010d Androidem."),
    };

    private RenderStoreScreenshots() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: java tools/RenderStoreScreenshots.java");
        }
        BufferedImage icon = ImageIO.read(ICON_PATH.toFile());
        if (icon == null || icon.getWidth() != 512 || icon.getHeight() != 512) {
            throw new IOException("Invalid store icon: " + ICON_PATH);
        }
        for (StoreLocale locale : StoreLocale.values()) {
            Path localeDir = OUTPUT_DIR.resolve(locale.id);
            Files.createDirectories(localeDir);
            List<BufferedImage> rendered = new ArrayList<>();
            for (Slide slide : SLIDES) {
                BufferedImage capture = ImageIO.read(CAPTURE_DIR.resolve(slide.output()).toFile());
                if (capture == null || capture.getWidth() != 1080 || capture.getHeight() < 2200) {
                    throw new IOException("Invalid device capture: " + slide.output());
                }
                BufferedImage image = render(slide, capture, icon, locale);
                Path output = localeDir.resolve(slide.output());
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
            writeContactSheet(rendered, localeDir);
        }
    }

    private static BufferedImage render(Slide slide, BufferedImage capture, BufferedImage icon, StoreLocale locale) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            drawCopy(graphics, slide, icon, locale);
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

    private static void drawCopy(Graphics2D graphics, Slide slide, BufferedImage icon, StoreLocale locale) {
        graphics.drawImage(icon, 70, 30, 58, 58, null);
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 20));
        graphics.setColor(TEXT);
        graphics.drawString("INTONIVA", 146, 68);

        graphics.setFont(new Font("Segoe UI", Font.BOLD, 56));
        graphics.setColor(TEXT);
        int headlineBottom = drawWrapped(graphics, slide.headline(locale), 70, 150, 940, 64, 2);

        graphics.setFont(new Font("Segoe UI", Font.PLAIN, 28));
        graphics.setColor(SECONDARY);
        drawWrapped(graphics, slide.subline(locale), 70, headlineBottom + 38, 940, 38, 2);
    }

    private static void drawPhone(Graphics2D graphics, BufferedImage capture) {
        int sourceBottom = capture.getHeight() - SYSTEM_BAR_HEIGHT;
        int sourceHeight = sourceBottom - SYSTEM_BAR_HEIGHT;
        int screenHeight = (int) Math.round(sourceHeight * (SCREEN_WIDTH / (double) capture.getWidth()));
        int phoneHeight = screenHeight + 80;
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.28f));
        graphics.setColor(Color.BLACK);
        graphics.fill(new RoundRectangle2D.Double(
            PHONE_X + 12, PHONE_Y + 18, PHONE_WIDTH, phoneHeight, 92, 92
        ));
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(0x05, 0x06, 0x05));
        graphics.fill(new RoundRectangle2D.Double(PHONE_X, PHONE_Y, PHONE_WIDTH, phoneHeight, 92, 92));
        graphics.setStroke(new BasicStroke(3f));
        graphics.setColor(new Color(0xCF, 0xCD, 0xC7));
        graphics.draw(new RoundRectangle2D.Double(PHONE_X, PHONE_Y, PHONE_WIDTH, phoneHeight, 92, 92));

        Shape oldClip = graphics.getClip();
        graphics.clip(new RoundRectangle2D.Double(SCREEN_X, SCREEN_Y, SCREEN_WIDTH, screenHeight, 48, 48));
        graphics.drawImage(
            capture,
            SCREEN_X,
            SCREEN_Y,
            SCREEN_X + SCREEN_WIDTH,
            SCREEN_Y + screenHeight,
            0,
            SYSTEM_BAR_HEIGHT,
            capture.getWidth(),
            sourceBottom,
            null
        );
        graphics.setClip(oldClip);

        graphics.setColor(new Color(0x26, 0x2A, 0x26));
        graphics.fillRoundRect(440, PHONE_Y + 16, 200, 10, 10, 10);
        graphics.fillOval(662, PHONE_Y + 11, 18, 18);
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

    private static void writeContactSheet(List<BufferedImage> images, Path outputDir) throws IOException {
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
        ImageIO.write(sheet, "png", outputDir.resolve("contact-sheet.png").toFile());
    }

    private enum StoreLocale {
        ENGLISH("en-US"),
        CZECH("cs-CZ");

        private final String id;

        StoreLocale(String id) {
            this.id = id;
        }
    }

    private record Slide(
        String output,
        String headlineEnglish,
        String sublineEnglish,
        String headlineCzech,
        String sublineCzech
    ) {
        String headline(StoreLocale locale) {
            return locale == StoreLocale.CZECH ? headlineCzech : headlineEnglish;
        }

        String subline(StoreLocale locale) {
            return locale == StoreLocale.CZECH ? sublineCzech : sublineEnglish;
        }
    }
}
