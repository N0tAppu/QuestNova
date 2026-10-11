package ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Design tokens + Look and Feel setup.
 *
 * Single source of truth for every colour, spacing step and corner radius
 * used by the application. Screens and components read their values from
 * here - no raw colour literals anywhere else in src/ui.
 *
 * Two independent axes:
 * - Appearance: {@link Appearance#LIGHT} (the approved baseline) or
 *   {@link Appearance#DARK} (layered charcoal/slate).
 * - Accent: {@link Accent#GREEN} (approved teal-green), {@link Accent#GRAPHITE}
 *   (cool silver-grey) or {@link Accent#PLUM} (muted purple).
 *
 * Every colour token is a mutable static field reassigned by
 * {@link #set(Appearance, Accent)}; custom renderers read them at paint
 * time, and eagerly-styled components are refreshed through the
 * {@code qn.*} client-property registry in {@link Ui#retheme}.
 * Selections persist in {@code java.util.prefs} (never in the database).
 */
public final class Theme {

    private Theme() {
    }

    // --- Appearance / accent state --------------------------------------

    public enum Appearance { LIGHT, DARK }

    public enum Accent { GREEN, GRAPHITE, PLUM }

    /** Current appearance. Mutated only through {@link #set}. */
    public static Appearance appearance = Appearance.LIGHT;
    /** Current accent scheme. Mutated only through {@link #set}. */
    public static Accent accent = Accent.GREEN;

    // Seed the default LIGHT/GREEN tokens at class load so early reads (and
    // tests that never call install()) always find non-null colours. set()
    // reassigns every token again.
    static {
        applyTokens();
    }

    // --- Colour palette (reassigned by set) -----------------------------
    /** Window + content ground. */
    public static Color BG;
    /** Sidebar + navigation ground (visibly distinct from BG). */
    public static Color SIDE;
    /** Cards, tables, dialogs. */
    public static Color SURFACE;
    /** Editable control background. */
    public static Color INPUT;
    /** Primary text. */
    public static Color INK;
    /** Secondary / hint text. */
    public static Color MUTED;
    /** Card/control boundaries, hairline separators. */
    public static Color LINE;
    /** Control border for inputs and plain buttons (>=3:1 on surfaces). */
    public static Color CTRL_LINE;
    /** Panel + table header background. */
    public static Color HEAD_BG;
    /** Table odd-row wash. */
    public static Color ROW_A;
    /** Table even-row wash (zebra stripe). */
    public static Color ROW_B;
    /** Table grid lines. */
    public static Color GRID;
    /** Hovered (unselected) table row / nav row wash. */
    public static Color HOVER;
    /** Meter progress track. */
    public static Color TRACK;
    /** Primary accent - buttons, links, selection edge, focus. */
    public static Color ACCENT;
    /** Accent hover / emphasis text on tint. */
    public static Color ACCENT_D;
    /** Accent pressed. */
    public static Color ACCENT_P;
    /** Foreground for text on a filled ACCENT surface. */
    public static Color ACCENT_FG;
    /** Selection wash, selected nav row. */
    public static Color TINT;
    /** Inactive selection wash. */
    public static Color TINT_IDLE;
    /** Success text (>=4.5:1 on OK_BG and surfaces). */
    public static Color OK;
    /** Success panel background. */
    public static Color OK_BG;
    /** Warning text. */
    public static Color WARN;
    /** Danger text. */
    public static Color BAD;
    /** Danger panel background. */
    public static Color BAD_BG;
    /** Disabled fill for a primary action. */
    public static Color PRIMARY_OFF;
    /** Disabled fill for a destructive action. */
    public static Color DANGER_OFF;
    /** Label colour for disabled actions. */
    public static Color DISABLED_FG;
    /** Keyboard focus indicator. */
    public static Color FOCUS;

    /** "#RRGGBB" for a colour, for FlatLaf style strings and UI defaults. */
    public static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

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

    // --- Persistence ----------------------------------------------------

    private static final String PREF_NODE = "/quest-nova";
    private static final String PREF_APPEARANCE = "appearance";
    private static final String PREF_ACCENT = "accent";

    public static Preferences prefs() {
        return Preferences.userRoot().node(PREF_NODE);
    }

    /** Reads persisted selections; invalid or missing values fall back safely. */
    public static void loadPrefs() {
        Preferences p = prefs();
        Appearance a = parseAppearance(p.get(PREF_APPEARANCE, "light"));
        Accent c = parseAccent(p.get(PREF_ACCENT, "green"));
        set(a, c, false);
    }

    /** Persists the current selections (called by {@link #set}). */
    public static void savePrefs() {
        Preferences p = prefs();
        p.put(PREF_APPEARANCE, appearance.name().toLowerCase());
        p.put(PREF_ACCENT, accent.name().toLowerCase());
        try {
            p.flush();
        } catch (Exception ignored) {
            // persistence is best-effort; the session still works
        }
    }

    /** Safe parse: anything unknown maps to LIGHT. */
    public static Appearance parseAppearance(String v) {
        try {
            return Appearance.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return Appearance.LIGHT;
        }
    }

    /** Safe parse: anything unknown maps to GREEN. */
    public static Accent parseAccent(String v) {
        try {
            return Accent.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return Accent.GREEN;
        }
    }

    // --- Theme application ----------------------------------------------

    /** Listener run after a live theme change (screen refresh hooks). */
    private static Runnable applyListener;

    public static void setApplyListener(Runnable r) {
        applyListener = r;
    }

    /** Installs the persisted theme. Called once at launch. */
    public static void install() {
        loadPrefs();
    }

    /**
     * Applies an appearance + accent combination, persists it, refreshes
     * every open window and notifies the apply listener. Appearance and
     * accent are independent - any accent works with either appearance.
     */
    public static void set(Appearance a, Accent c) {
        set(a, c, true);
    }

    private static void set(Appearance a, Accent c, boolean persist) {
        appearance = a;
        accent = c;
        applyTokens();
        setupLaf();
        applyDefaults();
        if (persist) {
            savePrefs();
        }
        refreshWindows();
        if (applyListener != null) {
            applyListener.run();
        }
    }

    /** Walks every open window: re-applies LaF delegates + qn.* registry. */
    static void refreshWindows() {
        for (Window w : Window.getWindows()) {
            if (w.isDisplayable()) {
                SwingUtilities.updateComponentTreeUI(w);
                Ui.retheme(w);
                w.repaint();
            }
        }
    }

    private static void setupLaf() {
        try {
            FlatLaf.setPreferredFontFamily("Segoe UI");
            FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", hex(ACCENT)));
        } catch (Throwable t) {
            // older FlatLaf - fall through to plain setup
        }
        try {
            if (appearance == Appearance.DARK) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep whatever look and feel is already active
            }
        }
    }

    // --- Token tables ----------------------------------------------------

    private static void applyTokens() {
        if (appearance == Appearance.DARK) {
            applyDarkTokens();
        } else {
            applyLightTokens();
        }
        applyAccentTokens();
    }

    private static void applyLightTokens() {
        BG = new Color(0xF7F8FA);
        // Sidebar edge must reach >=1.15:1 vs BG (ThemeSystemTest check);
        // #E4EAF1 measured 1.14 so darkened one step to #E1E7F0 (~1.16:1).
        SIDE = new Color(0xE1E7F0);
        SURFACE = Color.WHITE;
        INPUT = Color.WHITE;
        INK = new Color(0x202833);
        MUTED = new Color(0x526071);
        LINE = new Color(0xD6DDE5);
        // Interactive control borders (inputs, plain buttons) need >=3:1 on
        // white per WCAG 1.4.11; #D6DDE5 is only ~1.4:1 and is reserved for
        // the decorative LINE token. #8A94A6 measures ~3.1:1 on SURFACE.
        CTRL_LINE = new Color(0x8A94A6);
        HEAD_BG = new Color(0xE8EDF2);
        ROW_A = Color.WHITE;
        ROW_B = new Color(0xF3F6F9);
        GRID = new Color(0xD6DDE5);
        HOVER = new Color(0xE8EDF3);
        TRACK = new Color(0xE8EDF2);
        OK = new Color(0x18794E);
        OK_BG = new Color(0xE6F3EA);
        WARN = new Color(0x946200);
        BAD = new Color(0xB42318);
        BAD_BG = new Color(0xFBEDEC);
        TINT_IDLE = new Color(0xE8EDF2);
        DISABLED_FG = new Color(0x687586);
        FOCUS = new Color(0x2563EB);
    }

    private static void applyDarkTokens() {
        BG = new Color(0x1D232B);
        SIDE = new Color(0x0E141C);
        SURFACE = new Color(0x252D37);
        INPUT = new Color(0x1B222A);
        INK = new Color(0xF1F4F8);
        MUTED = new Color(0xB0BAC6);
        LINE = new Color(0x36414E);
        CTRL_LINE = new Color(0x7A8794);
        HEAD_BG = new Color(0x202731);
        ROW_A = new Color(0x202731);
        ROW_B = new Color(0x252E39);
        GRID = new Color(0x2F3945);
        HOVER = new Color(0x2C3644);
        TRACK = new Color(0x303A46);
        OK = new Color(0x58C99A);
        OK_BG = new Color(0x16332A);
        WARN = new Color(0xF0BF69);
        BAD = new Color(0xFF8585);
        BAD_BG = new Color(0x3A2224);
        TINT_IDLE = new Color(0x2A3340);
        DISABLED_FG = new Color(0x8B95A3);
        FOCUS = new Color(0x80B7FF);
    }

    private static void applyAccentTokens() {
        switch (accent) {
            case GRAPHITE:
                if (appearance == Appearance.DARK) {
                    ACCENT = new Color(0xA3AFBF);
                    ACCENT_D = new Color(0xC3CDDA);
                    ACCENT_P = new Color(0x8B98A9);
                    TINT = new Color(0x333C48);
                    PRIMARY_OFF = new Color(0x3A4452);
                    ACCENT_FG = new Color(0x14181E);
                } else {
                    ACCENT = new Color(0x475467);
                    ACCENT_D = new Color(0x34404F);
                    ACCENT_P = new Color(0x2A3542);
                    TINT = new Color(0xDDE3EA);
                    PRIMARY_OFF = new Color(0xE4E8ED);
                    ACCENT_FG = Color.WHITE;
                }
                break;
            case PLUM:
                if (appearance == Appearance.DARK) {
                    ACCENT = new Color(0xB99AE8);
                    ACCENT_D = new Color(0xC9A9F0);
                    ACCENT_P = new Color(0xA07FD4);
                    TINT = new Color(0x322A44);
                    PRIMARY_OFF = new Color(0x3C3350);
                    ACCENT_FG = new Color(0x14181E);
                } else {
                    ACCENT = new Color(0x6F4E9E);
                    ACCENT_D = new Color(0x5C3F86);
                    ACCENT_P = new Color(0x4E3572);
                    TINT = new Color(0xE7DDF4);
                    PRIMARY_OFF = new Color(0xEEE6F7);
                    ACCENT_FG = Color.WHITE;
                }
                break;
            case GREEN:
            default:
                if (appearance == Appearance.DARK) {
                    ACCENT = new Color(0x42B883);
                    ACCENT_D = new Color(0x5FD1A0);
                    ACCENT_P = new Color(0x35996D);
                    TINT = new Color(0x1F3A32);
                    PRIMARY_OFF = new Color(0x2A4A40);
                    ACCENT_FG = new Color(0x14181E);
                } else {
                    ACCENT = new Color(0x0F766E);
                    ACCENT_D = new Color(0x0B5751);
                    ACCENT_P = new Color(0x094A45);
                    TINT = new Color(0xCDE7E2);
                    PRIMARY_OFF = new Color(0xD6ECE8);
                    ACCENT_FG = Color.WHITE;
                }
                break;
        }
        DANGER_OFF = appearance == Appearance.DARK
                ? new Color(0x3A2426) : new Color(0xF7E4E3);
    }

    // --- UI defaults -----------------------------------------------------

    private static void applyDefaults() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Panel.foreground", INK);
        UIManager.put("Component.arc", CONTROL_ARC);
        UIManager.put("Button.arc", CONTROL_ARC);
        UIManager.put("TextComponent.arc", CONTROL_ARC);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusedBorderColor", FOCUS);
        UIManager.put("Component.borderColor", CTRL_LINE);
        UIManager.put("Component.disabledBackground", SURFACE);
        UIManager.put("Component.disabledForeground", DISABLED_FG);
        UIManager.put("Component.disabledBorderColor", LINE);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbColor", TRACK);
        UIManager.put("ScrollBar.trackColor", BG);
        UIManager.put("TextField.background", INPUT);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextArea.background", INPUT);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("FormattedTextField.background", INPUT);
        UIManager.put("FormattedTextField.foreground", INK);
        UIManager.put("ComboBox.background", INPUT);
        UIManager.put("ComboBox.foreground", INK);
        UIManager.put("ComboBox.buttonBackground", HEAD_BG);
        UIManager.put("Spinner.background", INPUT);
        UIManager.put("Spinner.foreground", INK);
        UIManager.put("Spinner.editorBackground", INPUT);
        UIManager.put("ToolTip.background", SURFACE);
        UIManager.put("ToolTip.foreground", INK);
        UIManager.put("PopupMenu.background", SURFACE);
        UIManager.put("PopupMenu.foreground", INK);
        UIManager.put("CheckBox.background", BG);
        UIManager.put("CheckBox.foreground", INK);
        UIManager.put("RadioButton.background", BG);
        UIManager.put("RadioButton.foreground", INK);
        UIManager.put("Button.disabledBackground", SURFACE);
        UIManager.put("Button.disabledForeground", DISABLED_FG);
        UIManager.put("Table.background", ROW_A);
        UIManager.put("Table.foreground", INK);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.gridColor", GRID);
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
    }
}
