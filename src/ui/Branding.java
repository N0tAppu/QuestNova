package ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Quest Nova brand asset loading.
 *
 * Loads the shipped application icon (transparent PNG, square) from the
 * build output classpath first, then from the project resource folder as a
 * file. Never throws: when no source yields an image the callers keep their
 * built-in fallbacks (painted mark / default window icon), but {@link #source()}
 * reports "none" so verification can tell a real asset load from a fallback.
 */
public final class Branding {

    /** Resource path on the runtime classpath (build\resources after a build). */
    public static final String RESOURCE = "/resources/Orbiting Quiz Sheet Icon.png";

    /** File-system fallbacks, relative to the project folder. */
    private static final String[] FILE_FALLBACKS = {
            "resources/Orbiting Quiz Sheet Icon.png",
            "build/resources/Orbiting Quiz Sheet Icon.png"
    };

    /** Window icon sizes used for {@code JFrame#setIconImages}. */
    private static final int[] WINDOW_SIZES = {16, 24, 32, 48, 64, 128};

    private static boolean attempted;
    private static BufferedImage base;
    private static String source = "none";

    private Branding() {
    }

    /** True when the real asset image was loaded. */
    public static boolean available() {
        load();
        return base != null;
    }

    /**
     * Where the image came from: "classpath", "file:&lt;absolute path&gt;",
     * or "none" when the asset could not be loaded at all.
     */
    public static String source() {
        load();
        return source;
    }

    /**
     * The asset scaled to a square of the requested size (aspect preserved;
     * the source is square). Returns null when unavailable.
     */
    public static Image icon(int size) {
        load();
        if (base == null) {
            return null;
        }
        return base.getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }

    /** The window icon set. Empty when the asset is unavailable. */
    public static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>();
        if (!available()) {
            return icons;
        }
        for (int size : WINDOW_SIZES) {
            icons.add(icon(size));
        }
        return icons;
    }

    private static synchronized void load() {
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            URL url = Branding.class.getResource(RESOURCE);
            if (url != null) {
                BufferedImage img = ImageIO.read(url);
                if (img != null) {
                    base = img;
                    source = "classpath";
                    return;
                }
            }
        } catch (Throwable ignored) {
            // fall through to the file sources
        }
        for (String candidate : FILE_FALLBACKS) {
            try {
                Path path = Paths.get(candidate);
                if (Files.isRegularFile(path)) {
                    BufferedImage img = ImageIO.read(path.toFile());
                    if (img != null) {
                        base = img;
                        source = "file:" + path.toAbsolutePath();
                        return;
                    }
                }
            } catch (Throwable ignored) {
                // try the next source
            }
        }
    }
}
