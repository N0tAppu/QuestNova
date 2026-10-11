import ui.Theme;
import ui.Ui;

import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;

/**
 * Diagnostic: build components under DARK, switch to LIGHT, and report any
 * component that still carries a DARK-only colour (stale after theme switch).
 * Also paints the resulting tree for manual inspection.
 */
public class ThemeSwitchProbe {

    public static void main(String[] args) throws Exception {
        Theme.set(Theme.Appearance.DARK, Theme.Accent.GREEN);
        SwingUtilities.invokeAndWait(() -> {
            try {
                JFrame f = new JFrame("probe");
                f.setSize(1000, 640);
                f.addNotify();

                JPanel root = new JPanel(new java.awt.BorderLayout());
                // Sidebar stand-in
                JPanel side = new JPanel();
                side.putClientProperty("qn.bg", "SIDE");
                Ui.bg(side, "SIDE");
                JLabel navSel = new JLabel("Selected screen");
                Ui.fg(navSel, "ACCENT_D");
                side.add(navSel);

                // Table with Ui.styleTable
                JTable t = new JTable(new Object[][]{{"a", "b"}, {"c", "d"}},
                        new Object[]{"Col1", "Col2"});
                Ui.styleTable(t);
                t.getSelectionModel().setSelectionInterval(0, 0);
                JScrollPane sp = Ui.table(t);

                // Buttons of every kind
                JButton prim = Ui.primary("Primary");
                JButton plain = Ui.plain("Plain");
                JButton dang = Ui.danger("Danger");
                JButton link = Ui.link("Link");

                // Combo, spinner, text field, tooltip host
                JComboBox<String> combo = new JComboBox<>(new String[]{"One", "Two"});
                JSpinner spin = new JSpinner(new javax.swing.SpinnerNumberModel(1, 0, 10, 1));
                JTextField tf = new JTextField("text");
                JLabel muted = Ui.muted("muted label");
                JLabel bold = Ui.bold("bold label");
                tf.setToolTipText("a tooltip");
                JCheckBox cb = new JCheckBox("check");
                JRadioButton rb = new JRadioButton("radio");

                JPanel mid = new JPanel(new java.awt.GridLayout(0, 2, 8, 8));
                mid.setBorder(new javax.swing.border.EmptyBorder(12, 12, 12, 12));
                mid.add(prim); mid.add(plain);
                mid.add(dang); mid.add(link);
                mid.add(combo); mid.add(spin);
                mid.add(tf); mid.add(muted);
                mid.add(bold); mid.add(cb);
                mid.add(rb);

                root.add(side, java.awt.BorderLayout.WEST);
                root.add(sp, java.awt.BorderLayout.CENTER);
                root.add(mid, java.awt.BorderLayout.SOUTH);
                f.setContentPane(root);
                f.validate();

                // ---- Switch to LIGHT on the SAME components ----
                System.out.println("Pre-switch: tf.getBackground()=" + Theme.hex(tf.getBackground()));
                System.out.println("Pre-switch: UIManager Panel.background="
                        + Theme.hex(javax.swing.UIManager.getColor("Panel.background")));
                java.awt.Window[] winsBefore = java.awt.Window.getWindows();
                System.out.println("Window.getWindows().length before=" + winsBefore.length);
                for (java.awt.Window w : winsBefore) {
                    System.out.println("  win " + w.getClass().getSimpleName()
                            + " displayable=" + w.isDisplayable()
                            + " visible=" + w.isVisible());
                }
                Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GREEN);
                f.validate();
                System.out.println("Post-switch: tf.getBackground()=" + Theme.hex(tf.getBackground()));
                System.out.println("Post-switch: UIManager Panel.background="
                        + Theme.hex(javax.swing.UIManager.getColor("Panel.background")));
                System.out.println("Post-switch: UIManager TextField.background="
                        + Theme.hex(javax.swing.UIManager.getColor("TextField.background")));
                // Manual updateComponentTreeUI to isolate whether the walker is the issue
                javax.swing.SwingUtilities.updateComponentTreeUI(f);
                System.out.println("After manual updateComponentTreeUI:");
                System.out.println("  tf.getBackground()=" + Theme.hex(tf.getBackground()));
                System.out.println("  combo.getBackground()=" + Theme.hex(combo.getBackground()));
                System.out.println("  root.getBackground()=" + Theme.hex(root.getBackground()));

                // ---- Report stale colours ----
                System.out.println("=== After dark -> light switch ===");
                int stale = 0;
                stale += report(f.getContentPane(), 0);
                System.out.println("stale_count=" + stale);
                System.out.println("LIGHT tokens: BG=" + Theme.hex(Theme.BG)
                        + " SIDE=" + Theme.hex(Theme.SIDE)
                        + " SURFACE=" + Theme.hex(Theme.SURFACE)
                        + " INK=" + Theme.hex(Theme.INK)
                        + " ROW_A=" + Theme.hex(Theme.ROW_A)
                        + " ROW_B=" + Theme.hex(Theme.ROW_B));

                // ---- Repeated transitions: dark -> light -> dark -> light ----
                Theme.set(Theme.Appearance.DARK, Theme.Accent.GREEN);
                f.validate();
                int staleDark = report(f.getContentPane(), 0);
                System.out.println("after 2nd switch (to DARK) stale_count=" + staleDark);

                Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GREEN);
                f.validate();
                int staleLight2 = report(f.getContentPane(), 0);
                System.out.println("after 3rd switch (to LIGHT) stale_count=" + staleLight2);

                Theme.set(Theme.Appearance.DARK, Theme.Accent.GREEN);
                f.validate();
                int staleDark2 = report(f.getContentPane(), 0);
                System.out.println("after 4th switch (to DARK) stale_count=" + staleDark2);

                Theme.set(Theme.Appearance.LIGHT, Theme.Accent.GREEN);
                f.validate();
                int staleFinal = report(f.getContentPane(), 0);
                System.out.println("FINAL LIGHT stale_count=" + staleFinal);
                System.out.println("FINAL LIGHT tokens: BG=" + Theme.hex(Theme.BG)
                        + " SIDE=" + Theme.hex(Theme.SIDE)
                        + " SURFACE=" + Theme.hex(Theme.SURFACE)
                        + " INK=" + Theme.hex(Theme.INK));

                // Paint for visual check
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                        1000, 640, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = img.createGraphics();
                root.paint(g);
                g.dispose();
                javax.imageio.ImageIO.write(img, "png",
                        new java.io.File("dark-to-light-probe.png"));
                System.out.println("wrote dark-to-light-probe.png");
                f.dispose();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** Returns how many components under root carry a colour from the OTHER mode. */
    private static int report(Component c, int depth) {
        int n = 0;
        String pad = "  ".repeat(depth);
        boolean lightActive = Theme.appearance == Theme.Appearance.LIGHT;
        if (c instanceof JComponent) {
            JComponent j = (JComponent) c;
            Color bg = j.getBackground();
            Color fg = j.getForeground();
            String cls = c.getClass().getSimpleName();
            String name = j.getName() != null ? j.getName() : "";
            if (lightActive ? isDarkOnly(bg) : isLightOnly(bg)) {
                System.out.println(pad + "STALE-BG  " + cls + " " + name
                        + " bg=" + Theme.hex(bg));
                n++;
            }
            if (lightActive ? isDarkOnly(fg) : isLightOnly(fg)) {
                System.out.println(pad + "STALE-FG  " + cls + " " + name
                        + " fg=" + Theme.hex(fg));
                n++;
            }
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                n += report(child, depth + 1);
            }
        }
        return n;
    }

    /** True if the colour matches a known Dark-mode-only token. */
    private static boolean isDarkOnly(Color c) {
        if (c == null) {
            return false;
        }
        int rgb = c.getRGB() & 0xFFFFFF;
        int[] dark = {
                0x1D232B, 0x0E141C, 0x252D37, 0x1B222A,
                0x202731, 0x252E39, 0x2F3945, 0x2C3644,
                0x303A46, 0x36414E, 0x2A3340, 0x16332A,
                0x3A2224, 0x1F3A32, 0x2A4A40,
        };
        for (int d : dark) {
            if (rgb == d) {
                return true;
            }
        }
        return false;
    }

    /** True if the colour matches a known Light-mode-only token. */
    private static boolean isLightOnly(Color c) {
        if (c == null) {
            return false;
        }
        int rgb = c.getRGB() & 0xFFFFFF;
        int[] light = {
                0xF7F8FA, // BG
                0xE4EAF1, // SIDE
                0xE8EDF2, // HEAD_BG / TRACK / TINT_IDLE
                0xF3F6F9, // ROW_B
                0xE8EDF3, // HOVER
                0xD6DDE5, // LINE / GRID
                0x8A94A6, // CTRL_LINE
                0x202833, // INK
                0x526071, // MUTED
                0x18794E, // OK
                0x946200, // WARN
                0xE6F3EA, // OK_BG
                0xFBEDEC, // BAD_BG
                0xCDE7E2, // GREEN light TINT
                0xD6ECE8, // GREEN light PRIMARY_OFF
        };
        for (int l : light) {
            if (rgb == l) {
                return true;
            }
        }
        return false;
    }
}
