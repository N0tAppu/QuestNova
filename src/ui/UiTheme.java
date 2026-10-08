package ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Font;

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
    public static final Color BG = new Color(0xEDF0F4);
    public static final Color SURFACE = Color.WHITE;
    public static final Color STRIPE = new Color(0xF6F8FA);
    public static final Color HEADER_BG = new Color(0xF2F4F7);

    public static final Color TEXT = new Color(0x1C2530);
    public static final Color MUTED = new Color(0x566270);

    public static final Color BORDER = new Color(0xB9C2CC);
    public static final Color CTRL_BORDER = new Color(0x767F8B);

    public static final Color ACCENT = new Color(0x1B57B8);
    public static final Color ACCENT_DARK = new Color(0x14418C);
    public static final Color ON_ACCENT = Color.WHITE;

    public static final Color SUCCESS = new Color(0x0F7B3F);
    public static final Color DANGER = new Color(0xB3261E);
    public static final Color ON_DANGER = Color.WHITE;
    public static final Color WARN = new Color(0x8A5A00);

    public static final Color SEL_BG = new Color(0xCFE0FA);
    public static final Color SEL_FG = new Color(0x10243F);
    public static final Color SEL_BG_INACTIVE = new Color(0xE1EBFA);

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

    /** A titled section border with the shared title styling. */
    public static TitledBorder sectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER), title);
        border.setTitleColor(MUTED);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, SECTION_SIZE));
        return border;
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

    /** Styles a button as the primary action of a screen. */
    public static void primaryButton(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(ON_ACCENT);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_DARK),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
    }

    /** Styles a button as a destructive action of a screen. */
    public static void dangerButton(JButton button) {
        button.setBackground(DANGER);
        button.setForeground(ON_DANGER);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DANGER),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
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
