import ui.Theme;
import ui.Ui;

import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.util.prefs.Preferences;

/**
 * Headless test for the Light/Dark + accent theme system.
 * Covers: token completeness per appearance x accent combo, WCAG contrast,
 * prefs round-trip + invalid fallback, zebra/selection/hover precedence,
 * form-value preservation across a theme switch, and accent/appearance
 * independence. No database access - pure Theme/Ui logic.
 */
public class ThemeSystemTest {

    static int checks;
    static int failures;

    public static void main(String[] args) throws Exception {
        // Run on the EDT so Swing components are constructed safely.
        SwingUtilities.invokeAndWait(() -> {
            try {
                tokenCompletenessAllCombos();
                contrastAllCombos();
                prefsRoundTrip();
                prefsInvalidFallback();
                zebraSelectionHover();
                formPreservation();
                accentAppearanceIndependence();
                System.out.println("RESULT: " + checks + " passed, " + failures + " failed");
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
        if (failures > 0) {
            System.exit(1);
        }
    }

    static void tokenCompletenessAllCombos() {
        Theme.Appearance[] appearances = Theme.Appearance.values();
        Theme.Accent[] accents = Theme.Accent.values();
        for (Theme.Appearance a : appearances) {
            for (Theme.Accent c : accents) {
                Theme.set(a, c);
                // Set must never leave a token null.
                Color[] tokens = {
                        Theme.BG, Theme.SIDE, Theme.SURFACE, Theme.INPUT,
                        Theme.INK, Theme.MUTED, Theme.LINE, Theme.CTRL_LINE,
                        Theme.HEAD_BG, Theme.ROW_A, Theme.ROW_B, Theme.GRID,
                        Theme.HOVER, Theme.TRACK, Theme.ACCENT, Theme.ACCENT_D,
                        Theme.ACCENT_P, Theme.ACCENT_FG, Theme.TINT,
                        Theme.TINT_IDLE, Theme.OK, Theme.OK_BG, Theme.WARN,
                        Theme.BAD, Theme.BAD_BG, Theme.PRIMARY_OFF,
                        Theme.DANGER_OFF, Theme.DISABLED_FG, Theme.FOCUS
                };
                for (Color t : tokens) {
                    check(a + "/" + c + " token not null", t != null);
                }
                // Sidebar must be visibly distinct from content ground.
                check(a + "/" + c + " SIDE != BG", !Theme.SIDE.equals(Theme.BG));
                // Zebra stripe: ROW_A and ROW_B must differ.
                check(a + "/" + c + " ROW_A != ROW_B", !Theme.ROW_A.equals(Theme.ROW_B));
                // ACCENT_FG must differ from ACCENT (text on button).
                check(a + "/" + c + " ACCENT_FG != ACCENT",
                        !Theme.ACCENT_FG.equals(Theme.ACCENT));
            }
        }
    }

    static void contrastAllCombos() {
        Theme.Appearance[] appearances = Theme.Appearance.values();
        Theme.Accent[] accents = Theme.Accent.values();
        for (Theme.Appearance a : appearances) {
            for (Theme.Accent c : accents) {
                Theme.set(a, c);
                // Body text pairs (>= 4.5:1)
                pair(a + "/" + c, "INK on SURFACE", Theme.INK, Theme.SURFACE, 4.5);
                pair(a + "/" + c, "INK on BG", Theme.INK, Theme.BG, 4.5);
                pair(a + "/" + c, "INK on HEAD_BG", Theme.INK, Theme.HEAD_BG, 4.5);
                pair(a + "/" + c, "MUTED on SURFACE", Theme.MUTED, Theme.SURFACE, 4.5);
                pair(a + "/" + c, "MUTED on BG", Theme.MUTED, Theme.BG, 4.5);
                pair(a + "/" + c, "OK on OK_BG", Theme.OK, Theme.OK_BG, 4.5);
                pair(a + "/" + c, "OK on SURFACE", Theme.OK, Theme.SURFACE, 4.5);
                pair(a + "/" + c, "WARN on SURFACE", Theme.WARN, Theme.SURFACE, 4.5);
                pair(a + "/" + c, "BAD on BAD_BG", Theme.BAD, Theme.BAD_BG, 4.5);
                pair(a + "/" + c, "BAD on SURFACE", Theme.BAD, Theme.SURFACE, 4.5);
                pair(a + "/" + c, "ACCENT_FG on ACCENT", Theme.ACCENT_FG, Theme.ACCENT, 4.5);
                pair(a + "/" + c, "ACCENT_D on SURFACE", Theme.ACCENT_D, Theme.SURFACE, 4.5);
                pair(a + "/" + c, "INK on ROW_A", Theme.INK, Theme.ROW_A, 4.5);
                pair(a + "/" + c, "INK on ROW_B", Theme.INK, Theme.ROW_B, 4.5);
                pair(a + "/" + c, "INK on HOVER", Theme.INK, Theme.HOVER, 4.5);
                pair(a + "/" + c, "INK on TINT", Theme.INK, Theme.TINT, 4.5);
                // Non-text UI elements (>= 3:1)
                pair(a + "/" + c, "CTRL_LINE on SURFACE", Theme.CTRL_LINE, Theme.SURFACE, 3.0);
                pair(a + "/" + c, "CTRL_LINE on INPUT", Theme.CTRL_LINE, Theme.INPUT, 3.0);
                // LINE and GRID are decorative hairlines (not interactive
                // boundaries) - WCAG 1.4.11 exempts them; only require that
                // they are perceptibly different from the surface.
                pair(a + "/" + c, "LINE on SURFACE (decorative)", Theme.LINE, Theme.SURFACE, 1.1);
                pair(a + "/" + c, "GRID on ROW_A (decorative)", Theme.GRID, Theme.ROW_A, 1.1);
                pair(a + "/" + c, "FOCUS on SURFACE", Theme.FOCUS, Theme.SURFACE, 3.0);
                pair(a + "/" + c, "FOCUS on BG", Theme.FOCUS, Theme.BG, 3.0);
                pair(a + "/" + c, "ACCENT on SURFACE", Theme.ACCENT, Theme.SURFACE, 3.0);
                pair(a + "/" + c, "ACCENT on BG", Theme.ACCENT, Theme.BG, 3.0);
                pair(a + "/" + c, "SIDE on BG (sidebar edge)", Theme.SIDE, Theme.BG, 1.15);
            }
        }
    }

    static void prefsRoundTrip() {
        // Save DARK/PLUM then load it back.
        Theme.set(Theme.Appearance.DARK, Theme.Accent.PLUM);
        Theme.savePrefs();
        Theme.loadPrefs();
        check("prefs round-trip appearance", Theme.appearance == Theme.Appearance.DARK);
        check("prefs round-trip accent", Theme.accent == Theme.Accent.PLUM);
    }

    static void prefsInvalidFallback() {
        Preferences p = Theme.prefs();
        p.put("appearance", "not-a-mode");
        p.put("accent", "neon");
        Theme.loadPrefs();
        check("invalid appearance falls back to LIGHT",
                Theme.appearance == Theme.Appearance.LIGHT);
        check("invalid accent falls back to GREEN",
                Theme.accent == Theme.Accent.GREEN);
    }

    static void zebraSelectionHover() {
        // Use the real cell renderer to verify precedence.
        Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GREEN);
        JTable table = new JTable(3, 2);
        Ui.styleTable(table);
        table.putClientProperty("qn.hoverRow", 1);
        table.changeSelection(0, 0, false, false);

        Component renderer = table.prepareRenderer(
                table.getDefaultRenderer(Object.class), 0, 0);
        Color selectedBg = renderer.getBackground();
        check("selected row uses TINT", selectedBg.equals(Theme.TINT));

        renderer = table.prepareRenderer(
                table.getDefaultRenderer(Object.class), 1, 0);
        Color hoverBg = renderer.getBackground();
        check("hover row uses HOVER", hoverBg.equals(Theme.HOVER));

        renderer = table.prepareRenderer(
                table.getDefaultRenderer(Object.class), 2, 0);
        Color stripeBg = renderer.getBackground();
        check("even row (2) uses ROW_A", stripeBg.equals(Theme.ROW_A));

        table.putClientProperty("qn.hoverRow", -1);
        renderer = table.prepareRenderer(
                table.getDefaultRenderer(Object.class), 2, 0);
        check("no-hover even row still ROW_A",
                renderer.getBackground().equals(Theme.ROW_A));
        renderer = table.prepareRenderer(
                table.getDefaultRenderer(Object.class), 1, 0);
        check("odd row (1) uses ROW_B",
                renderer.getBackground().equals(Theme.ROW_B));
    }

    static void formPreservation() {
        // A text field and spinner must keep their values across a theme switch.
        Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GREEN);
        JTextField field = new JTextField("hello");
        JSpinner spinner = new JSpinner(new javax.swing.SpinnerNumberModel(50, 0, 100, 1));
        Theme.set(Theme.Appearance.DARK, Theme.Accent.PLUM);
        check("text field value survives theme switch",
                "hello".equals(field.getText()));
        check("spinner value survives theme switch",
                ((Integer) spinner.getValue()) == 50);
    }

    static void accentAppearanceIndependence() {
        // Switching accent must not change appearance, and vice versa.
        Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GREEN);
        Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GRAPHITE);
        check("accent switch keeps LIGHT",
                Theme.appearance == Theme.Appearance.LIGHT);
        check("accent is GRAPHITE", Theme.accent == Theme.Accent.GRAPHITE);
        Theme.set(Theme.Appearance.DARK, Theme.Accent.GRAPHITE);
        check("appearance switch keeps GRAPHITE",
                Theme.accent == Theme.Accent.GRAPHITE);
        check("appearance is DARK", Theme.appearance == Theme.Appearance.DARK);
        // And tokens differ between the two appearances.
        Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GRAPHITE);
        Color lightBg = Theme.BG;
        Theme.set(Theme.Appearance.DARK, Theme.Accent.GRAPHITE);
        check("BG differs light vs dark", !lightBg.equals(Theme.BG));
    }

    // --- helpers ---------------------------------------------------------

    static double luminance(Color c) {
        double r = c.getRed() / 255.0, g = c.getGreen() / 255.0, b = c.getBlue() / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    static void pair(String combo, String name, Color fg, Color bg, double min) {
        double r = contrast(fg, bg);
        check(combo + " " + name + " " + String.format("%.2f", r) + " >= " + min,
                r >= min);
    }

    static void check(String label, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("PASS " + label);
        } else {
            failures++;
            System.out.println("FAIL " + label);
        }
    }
}
