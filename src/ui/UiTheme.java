package ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Central design system for the Question Paper Generator.
 *
 * Single source of truth for the colour palette, spacing, typography and the
 * small styling helpers shared by every screen. Every colour pair used for
 * text (4.5:1) or for control boundaries (3:1) was verified against its
 * background with a computed contrast check before being admitted here.
 *
 * The look and feel is installed once from MainFrame.launch(). Panels can be
 * constructed without it (the test suites do exactly that), so every helper
 * works with explicit values and never requires FlatLaf to be installed.
 */
public final class UiTheme {

    private UiTheme() {
    }

    // --- Colour palette -------------------------------------------------
    // Indigo/violet primary, teal secondary, emerald success, coral/red
    // destructive, amber warning, violet accent, tinted surfaces. Every
    // pair below was contrast-checked (4.5:1 text, 3:1 controls) before
    // being admitted; see artifacts/blueprint_ui_acceptance.md section F4.
    public static final Color BG = new Color(0xECF0FB);
    public static final Color SURFACE = Color.WHITE;
    public static final Color STRIPE = new Color(0xF6F8FE);
    public static final Color HEADER_BG = new Color(0xE4E9FA);

    public static final Color TEXT = new Color(0x16182D);
    public static final Color MUTED = new Color(0x545C7A);

    public static final Color BORDER = new Color(0xC3C9DE);
    public static final Color CTRL_BORDER = new Color(0x6E7591);

    /** Primary brand colour (indigo) - headers, links, primary actions. */
    public static final Color ACCENT = new Color(0x4F46E5);
    public static final Color ACCENT_DARK = new Color(0x4338CA);
    /** Violet accent - pairs with ACCENT in the intentional gradients. */
    public static final Color VIOLET = new Color(0x7C3AED);
    /** Teal secondary - secondary actions and informational emphasis. */
    public static final Color SECONDARY = new Color(0x0F766E);
    public static final Color ON_ACCENT = Color.WHITE;

    public static final Color SUCCESS = new Color(0x047857);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color ON_DANGER = Color.WHITE;
    public static final Color WARN = new Color(0xB45309);

    public static final Color SEL_BG = new Color(0xDCE1FD);
    public static final Color SEL_FG = new Color(0x1E1B4B);
    public static final Color SEL_BG_INACTIVE = new Color(0xE9ECFD);

    /** Disabled fill for primary actions - muted indigo tint (never looks active). */
    public static final Color ACCENT_DISABLED_BG = new Color(0xDCE1FD);
    /** Disabled fill for destructive actions - muted coral tint. */
    public static final Color DANGER_DISABLED_BG = new Color(0xFCE8E8);

    // --- Spacing --------------------------------------------------------
    public static final int GAP = 10;
    public static final int PAD = 12;
    public static final int ROW_HEIGHT = 26;

    // --- Typography -----------------------------------------------------
    public static final float TITLE_SIZE = 20f;
    public static final float SECTION_SIZE = 12.5f;

    /** Installs the application look and feel and the shared ui defaults. */
    public static void install() {
        if (System.getProperty("flatlaf.useNativeLibrary") == null) {
            System.setProperty("flatlaf.useNativeLibrary", "false");
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
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", SEL_BG);
        UIManager.put("Table.selectionForeground", SEL_FG);
        UIManager.put("Table.inactiveSelectionBackground", SEL_BG_INACTIVE);
        UIManager.put("Table.inactiveSelectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.alternateRowColor", STRIPE);
        UIManager.put("TableHeader.background", HEADER_BG);
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("List.background", SURFACE);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", SEL_BG);
        UIManager.put("List.selectionForeground", SEL_FG);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("ComboBox.background", SURFACE);
    }

    /** The screen title label used at the top of every panel. */
    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, TITLE_SIZE));
        label.setForeground(TEXT);
        return label;
    }

    // --- Intentional gradients -----------------------------------------
    // Java2D gradients appear in exactly three places (design mandate):
    // the screen header strip, the active navigation tab indicator, and
    // the section title bands. Text endpoints were contrast-checked:
    // white on ACCENT 6.29:1, white on VIOLET 5.70:1 (both >= 4.5:1).

    /** A horizontal brand gradient (indigo -> violet) spanning x1..x2. */
    public static GradientPaint brandGradient(int x1, int x2, int y) {
        return new GradientPaint(x1, y, ACCENT, Math.max(x1 + 1, x2), y, VIOLET);
    }

    /**
     * The screen header strip: a brand-gradient band carrying the white
     * bold screen title. Drop-in replacement for {@link #title(String)}
     * at BorderLayout.NORTH - the title label keeps its text.
     */
    public static JPanel titleStrip(String text) {
        JPanel strip = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setPaint(brandGradient(0, Math.max(1, getWidth()), 0));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } finally {
                    g2.dispose();
                }
            }
        };
        strip.setOpaque(true);
        strip.setBackground(ACCENT);
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, TITLE_SIZE));
        label.setForeground(ON_ACCENT);
        label.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        strip.add(label, BorderLayout.CENTER);
        return strip;
    }

    /**
     * A titled section border whose title sits on the brand-gradient band.
     * Extends TitledBorder so existing instanceof(TitledBorder)/getTitle()
     * checks (test harnesses, section scrolling) keep working unchanged.
     */
    private static final class GradientBandBorder extends TitledBorder {

        private GradientBandBorder(String title) {
            super(BorderFactory.createLineBorder(BORDER), title);
            setTitleColor(ON_ACCENT);
            setTitleFont(getTitleFont().deriveFont(Font.BOLD, SECTION_SIZE));
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y,
                                int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int band = Math.max(1, getBorderInsets(c).top);
                g2.setPaint(brandGradient(x, x + Math.max(1, width), y));
                g2.fillRect(x, y, width, band);
            } finally {
                g2.dispose();
            }
            super.paintBorder(c, g, x, y, width, height);
        }
    }

    /** A titled section border with the shared gradient-band title styling. */
    public static TitledBorder sectionBorder(String title) {
        return new GradientBandBorder(title);
    }

    /** Styles a panel as a white section card with the shared titled border. */
    public static void styleCard(JPanel card, String title) {
        card.setBorder(sectionBorder(title));
        card.setBackground(SURFACE);
    }

    /** Applies the shared table presentation (never touches selection mode). */
    public static void styleTable(JTable table) {
        table.setRowHeight(Math.max(ROW_HEIGHT, table.getRowHeight()));
        table.setGridColor(BORDER);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setBackground(SURFACE);
        table.setForeground(TEXT);
        table.setSelectionBackground(SEL_BG);
        table.setSelectionForeground(SEL_FG);
    }

    /**
     * Styles a button as the primary action of a screen. When the button is
     * disabled the fill switches to a muted tint so a disabled action can
     * never be mistaken for an active one (acceptance F7).
     */
    public static void primaryButton(JButton button) {
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_DARK),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        applyActionColors(button, ACCENT, ON_ACCENT, ACCENT_DISABLED_BG);
    }

    /** Styles a button as a destructive action of a screen (see F7 above). */
    public static void dangerButton(JButton button) {
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DANGER),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        applyActionColors(button, DANGER, ON_DANGER, DANGER_DISABLED_BG);
    }

    /** Applies enabled/disabled fills and keeps them in sync with setEnabled. */
    private static void applyActionColors(JButton button, Color on, Color onFg,
                                          Color off) {
        Runnable apply = () -> {
            boolean enabled = button.isEnabled();
            button.setOpaque(true);
            button.setBackground(enabled ? on : off);
            button.setForeground(enabled ? onFg : MUTED);
        };
        apply.run();
        button.addPropertyChangeListener("enabled", e -> apply.run());
    }

    /** Styles a button as a neutral (secondary) action of a screen. */
    public static void neutralButton(JButton button) {
        button.setBackground(SURFACE);
        button.setForeground(TEXT);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CTRL_BORDER),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
    }
}
