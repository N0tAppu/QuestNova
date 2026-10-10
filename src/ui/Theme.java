package ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;

/**
 * Design tokens + Look and Feel setup.
 *
 * Single source of truth for every colour, spacing step and corner radius
 * used by the application. Screens and components read their values from
 * here - no raw colour literals anywhere else in src/ui.
 *
 * The palette is the warm light "Quest" system: off-white ground, white
 * surfaces, charcoal ink, teal accent.
 */
public final class Theme {

    private Theme() {
    }

    // --- Colour palette -------------------------------------------------
    /** Window + sidebar ground. */
    public static final Color BG = new Color(0xF6F5F2);
    /** Cards, tables, dialogs. */
    public static final Color SURFACE = Color.WHITE;
    /** Primary text. */
    public static final Color INK = new Color(0x1C2326);
    /** Secondary / hint text. */
    public static final Color MUTED = new Color(0x6A7479);
    /** Hairline separators, card borders. */
    public static final Color LINE = new Color(0xE3E1DB);
    /** Table header background. */
    public static final Color HEAD_BG = new Color(0xFBFAF8);
    /** Alternating row / hover wash. */
    public static final Color STRIPE = new Color(0xFAF9F6);
    /** Meter progress track. */
    public static final Color TRACK = new Color(0xECEAE4);
    /** Primary accent - buttons, links, selection edge, focus. */
    public static final Color ACCENT = new Color(0x0E6B7E);
    /** Accent pressed / emphasis text on tint. */
    public static final Color ACCENT_D = new Color(0x0A4F5E);
    /** Selection wash, answer panel. */
    public static final Color TINT = new Color(0xE4F1F3);
    /** Success text. */
    public static final Color OK = new Color(0x2E7D4F);
    /** Success panel background. */
    public static final Color OK_BG = new Color(0xE6F3EA);
    /** Warning text. */
    public static final Color WARN = new Color(0xA8741A);
    /** Danger text. */
    public static final Color BAD = new Color(0xB3261E);
    /** Danger panel background. */
    public static final Color BAD_BG = new Color(0xFBEDEC);

    /** Control border for inputs and plain buttons (>=3:1 on SURFACE). */
    public static final Color CTRL_LINE = new Color(0xC9C5BC);
    /** Inactive selection wash. */
    public static final Color TINT_IDLE = new Color(0xF1F6F7);
    /** Disabled fill for a primary action (text stays readable). */
    public static final Color PRIMARY_OFF = new Color(0xDCE8EB);
    /** Disabled fill for a destructive action. */
    public static final Color DANGER_OFF = new Color(0xF7E4E3);
    /** Label colour for disabled actions (readable on both disabled fills). */
    public static final Color DISABLED_FG = new Color(0x5A6467);

    // --- Spacing scale (px). Use these, never raw numbers. --------------
    public static final int XS = 4, S = 8, M = 14, L = 16, XL = 24;
    /** Table row height, card corner radius, control corner radius. */
    public static final int ROW = 32, CARD_ARC = 16, CONTROL_ARC = 8;

    /** Dot + label colour for a difficulty value. */
    public static Color difficulty(String d) {
        if ("Easy".equals(d)) {
            return OK;
        }
        if ("Medium".equals(d)) {
            return WARN;
        }
        return BAD;
    }

    /** Installs FlatLaf and every shared UI default. Called once at launch. */
    public static void install() {
        try {
            FlatLaf.setPreferredFontFamily("Segoe UI");
            FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#0E6B7E"));
        } catch (Throwable t) {
            // older FlatLaf - fall through to plain setup
        }
        try {
            FlatLightLaf.setup();
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep whatever look and feel is already active
            }
        }
        applyDefaults();
    }

    private static void applyDefaults() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Component.arc", CONTROL_ARC);
        UIManager.put("Button.arc", CONTROL_ARC);
        UIManager.put("TextComponent.arc", CONTROL_ARC);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusedBorderColor", ACCENT);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.gridColor", new Color(0xF0EEE9));
        UIManager.put("Table.selectionBackground", TINT);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("Table.inactiveSelectionBackground", TINT);
        UIManager.put("Table.inactiveSelectionForeground", INK);
        UIManager.put("TableHeader.background", HEAD_BG);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("List.background", SURFACE);
        UIManager.put("List.foreground", INK);
        UIManager.put("List.selectionBackground", TINT);
        UIManager.put("List.selectionForeground", INK);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("ComboBox.background", SURFACE);
    }
}
