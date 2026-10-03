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
    private static final int PHONE_Y = 340;
    private static final int SCREEN_Y = PHONE_Y + 26;
    private static final int SCREEN_WIDTH = 756;
    private static final int MAX_SCREEN_HEIGHT = 1478;
    private static final Color BACKGROUND = new Color(0xF7, 0xF5, 0xF1);
    private static final Color TEXT = new Color(0x11, 0x13, 0x10);
    private static final Color SECONDARY = new Color(0x5F, 0x65, 0x60);
    private static final Path CAPTURE_DIR = Path.of("docs", "store", "source-captures", "2026-10-03");
    private static final Path OUTPUT_DIR = Path.of("fastlane", "metadata", "android");
    private static final Path REVIEW_DIR = Path.of(".reference", "tmp", "store-review-2026-10-03");
    private static final Path ICON_PATH = Path.of("docs", "assets", "icon.png");

    private static final Slide[] SLIDES = {
        new Slide("1_tuner.png", "Find your note",
            "Guitar, bass and ukulele. See when you are in tune.",
            "Nalaďte svůj nástroj", "Kytara, baskytara i ukulele. Hned vidíte, jak ladíte."),
        new Slide("2_chromatic.png", "Tune any note",
            "Chromatic mode. Adjustable A4. Light or dark.",
            "Nalaďte každý tón", "Chromatická ladička. Vlastní A4. Světlý i tmavý režim."),
        new Slide("3_tunings.png", "Make it your tuning",
            "Built-in presets, favorites and your own tunings.",
            "Ladění podle vás", "Hotové předvolby, oblíbená i vlastní ladění."),
        new Slide("4_metronome.png", "Keep your rhythm",
            "Set the tempo. Choose your meter. Start playing.",
            "Držte svůj rytmus", "Nastavte tempo a takt. Pak už jen hrajte."),
        new Slide("5_chords.png", "Find your next chord",
            "Guitar and ukulele diagrams, always offline.",
            "Najděte další akord", "Hmaty pro kytaru a ukulele. Vždy offline."),
        new Slide("6_song_chords.png", "Explore a song",
            "Estimate chords and notes from your audio files.",
            "Prozkoumejte skladbu", "Odhad akordů a tónů z vašich zvukových souborů."),
        new Slide("7_trainer.png", "Train your ear",
            "Listen, learn and practice notes and chords.",
            "Trénujte svůj sluch", "Poslouchejte a procvičujte tóny i akordy."),
        new Slide("8_auto_scroll.png", "Keep your hands free",
            "Set up automatic scrolling for your music.",
            "Ruce patří nástroji", "Nastavte si automatické posouvání při hraní."),
    };

    private RenderStoreScreenshots() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: java \"-Dfile.encoding=UTF-8\" tools/RenderStoreScreenshots.java");
        }
        BufferedImage icon = ImageIO.read(ICON_PATH.toFile());
        if (icon == null || icon.getWidth() != 512 || icon.getHeight() != 512) {
            throw new IOException("Invalid store icon: " + ICON_PATH);
        }
        for (StoreLocale locale : StoreLocale.values()) {
            Path localeDir = OUTPUT_DIR.resolve(locale.id).resolve("images").resolve("phoneScreenshots");
            Files.createDirectories(localeDir);
            List<BufferedImage> rendered = new ArrayList<>();
            for (Slide slide : SLIDES) {
                BufferedImage capture = ImageIO.read(CAPTURE_DIR.resolve(locale.id).resolve(slide.output()).toFile());
                if (capture == null || capture.getWidth() != 1080 || capture.getHeight() < 1900) {
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
            Path reviewDir = REVIEW_DIR.resolve(locale.id);
            Files.createDirectories(reviewDir);
            writeContactSheet(rendered, reviewDir);
            writeFeatureGraphic(locale, icon);
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
        graphics.drawImage(icon, 76, 62, 50, 50, null);
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 26));
        graphics.setColor(TEXT);
        graphics.drawString("INTONIVA", 144, 97);

        graphics.setFont(new Font("Segoe UI", Font.BOLD, 68));
        graphics.setColor(TEXT);
        int headlineBottom = drawWrapped(graphics, slide.headline(locale), 76, 214, 928, 76, 1);

        graphics.setFont(new Font("Segoe UI", Font.PLAIN, 30));
        graphics.setColor(SECONDARY);
        drawWrapped(graphics, slide.subline(locale), 78, headlineBottom + 59, 924, 40, 1);
    }

    private static void drawPhone(Graphics2D graphics, BufferedImage capture) {
        double scale = Math.min(SCREEN_WIDTH / (double) capture.getWidth(), MAX_SCREEN_HEIGHT / (double) capture.getHeight());
        int screenWidth = (int) Math.round(capture.getWidth() * scale);
        int screenHeight = (int) Math.round(capture.getHeight() * scale);
        int screenX = (WIDTH - screenWidth) / 2;
        int phoneX = screenX - 20;
        int phoneWidth = screenWidth + 40;
        int phoneHeight = screenHeight + 52;
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.08f));
        graphics.setColor(Color.BLACK);
        graphics.fill(new RoundRectangle2D.Double(
            phoneX + 8, PHONE_Y + 16, phoneWidth, phoneHeight, 76, 76
        ));
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(0x05, 0x06, 0x05));
        graphics.fill(new RoundRectangle2D.Double(phoneX, PHONE_Y, phoneWidth, phoneHeight, 76, 76));
        graphics.setStroke(new BasicStroke(3f));
        graphics.setColor(new Color(0x28, 0x2A, 0x28));
        graphics.draw(new RoundRectangle2D.Double(phoneX, PHONE_Y, phoneWidth, phoneHeight, 76, 76));

        Shape oldClip = graphics.getClip();
        graphics.clip(new RoundRectangle2D.Double(screenX, SCREEN_Y, screenWidth, screenHeight, 40, 40));
        graphics.drawImage(
            capture,
            screenX,
            SCREEN_Y,
            screenX + screenWidth,
            SCREEN_Y + screenHeight,
            0,
            0,
            capture.getWidth(),
            capture.getHeight(),
            null
        );
        graphics.setClip(oldClip);

        graphics.setColor(new Color(0x38, 0x3A, 0x38));
        graphics.fillRoundRect(480, PHONE_Y + 9, 120, 6, 6, 6);
    }

    private static void writeFeatureGraphic(StoreLocale locale, BufferedImage icon) throws IOException {
        BufferedImage feature = new BufferedImage(1024, 500, BufferedImage.TYPE_INT_RGB);
        BufferedImage capture = ImageIO.read(CAPTURE_DIR.resolve(locale.id).resolve("1_tuner.png").toFile());
        Graphics2D graphics = feature.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, 1024, 500);
            graphics.drawImage(icon, 58, 48, 56, 56, null);
            graphics.setColor(TEXT);
            graphics.setFont(new Font("Segoe UI", Font.BOLD, 30));
            graphics.drawString("INTONIVA", 130, 87);
            graphics.setFont(new Font("Segoe UI", Font.BOLD, 62));
            graphics.drawString(locale == StoreLocale.CZECH ? "Nalaďte se." : "Tune in.", 58, 228);
            graphics.drawString(locale == StoreLocale.CZECH ? "A hrajte." : "Play on.", 58, 302);
            graphics.setColor(SECONDARY);
            graphics.setFont(new Font("Segoe UI", Font.PLAIN, 27));
            graphics.drawString(locale == StoreLocale.CZECH ? "Ladička a pomocník při hraní" : "Tuner and music tools", 60, 364);
            graphics.setColor(TEXT);
            graphics.fillRoundRect(620, 38, 342, 720, 44, 44);
            graphics.setClip(new RoundRectangle2D.Double(632, 52, 318, 690, 28, 28));
            graphics.drawImage(capture, 632, 52, 318, (int) Math.round(capture.getHeight() * 318.0 / capture.getWidth()), null);
        } finally {
            graphics.dispose();
        }
        Path path = OUTPUT_DIR.resolve(locale.id).resolve("images").resolve("featureGraphic.png");
        if (!ImageIO.write(feature, "png", path.toFile())) throw new IOException("PNG writer is unavailable");
        System.out.println(path.toAbsolutePath());
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
