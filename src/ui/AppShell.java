package ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Light sidebar navigation with grouped entries, count badges and a status
 * bar. Replaces the old JTabbedPane shell.
 *
 * Screens are plain panels that already carry their own {@link Ui#pageHeader}
 * at the top; AppShell only provides padding, navigation and the status bar.
 * Ctrl+1..5 jump between screens in the order they were added.
 */
public class AppShell extends JPanel {

    /** Notified whenever a screen becomes visible (also on a re-click). */
    public interface Host {
        void onScreenShown(int screenIndex);
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel status = Ui.muted("Ready");
    private final JLabel where = Ui.muted("");
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> nav = new JList<>(model);
    private final Set<String> groups = new HashSet<>();
    private final Map<String, Integer> badges = new HashMap<>();
    private final List<String> screenNames = new ArrayList<>();
    private Host host;

    public AppShell() {
        super(new BorderLayout());
        setBackground(Theme.BG);

        nav.setBackground(Theme.BG);
        nav.setFixedCellHeight(34);
        nav.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nav.setCellRenderer(new NavRenderer());
        nav.setOpaque(true);
        nav.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            String v = nav.getSelectedValue();
            if (v == null) {
                return;
            }
            if (groups.contains(v)) {
                // A group heading is not a screen: step to the first screen below it.
                int next = nav.getSelectedIndex() + 1;
                if (next < model.size()) {
                    nav.setSelectedIndex(next);
                }
                return;
            }
            activate(v);
        });
        nav.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = nav.locationToIndex(e.getPoint());
                if (row >= 0 && row == nav.getSelectedIndex()) {
                    // Re-clicking the current screen refreshes it, like the old tab bar.
                    activate(nav.getSelectedValue());
                }
            }
        });

        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.BG);
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));
        side.setPreferredSize(new Dimension(224, 0));
        side.add(buildBrand(), BorderLayout.NORTH);
        side.add(nav, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                new EmptyBorder(5, 28, 5, 28)));
        bar.add(status, BorderLayout.WEST);
        bar.add(where, BorderLayout.EAST);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        main.add(content, BorderLayout.CENTER);
        main.add(bar, BorderLayout.SOUTH);

        add(side, BorderLayout.WEST);
        add(main, BorderLayout.CENTER);
    }

    public void setHost(Host host) {
        this.host = host;
    }

    /** Adds a non-selectable group heading to the navigation. */
    public void group(String name) {
        model.addElement(name);
        groups.add(name);
    }

    /**
     * Adds a screen. The view already contains its own page header; AppShell
     * pads it and, once {@link #bindShortcuts()} runs, assigns Ctrl+(n+1).
     */
    public void screen(String name, JComponent view) {
        JPanel pad = new JPanel(new BorderLayout());
        pad.setBackground(Theme.BG);
        pad.setBorder(new EmptyBorder(Theme.XL, 28, Theme.L, 28));
        pad.add(view);
        content.add(pad, name);
        model.addElement(name);
        screenNames.add(name);
        if (screenNames.size() == 1) {
            // Select the first entry so the sidebar matches the card that
            // CardLayout shows first. The host is not wired yet, so no
            // refresh fires during construction.
            nav.setSelectedIndex(0);
        }
    }

    /**
     * Binds Ctrl+1..5 to the screens in the order they were added.
     * Must be called after the shell is inside a frame (it needs a root pane).
     */
    public void bindShortcuts() {
        if (getRootPane() == null) {
            return;
        }
        for (int i = 0; i < screenNames.size(); i++) {
            String name = screenNames.get(i);
            String key = "appshell.go" + (i + 1);
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke('0' + (i + 1),
                            java.awt.event.InputEvent.CTRL_DOWN_MASK), key);
            getRootPane().getActionMap().put(key, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    show(name);
                }
            });
        }
    }

    /** Shows the named screen as if it had been clicked in the sidebar. */
    public void show(String name) {
        nav.setSelectedValue(name, true);
    }

    /** The 0-based index of a screen name in visual order, or -1. */
    public int indexOf(String name) {
        return screenNames.indexOf(name);
    }

    public void say(String message) {
        status.setText(message == null ? "" : message);
    }

    public void badge(String screen, int n) {
        badges.put(screen, n);
        nav.repaint();
    }

    private void activate(String name) {
        int index = screenNames.indexOf(name);
        if (index < 0) {
            return;
        }
        cards.show(content, name);
        where.setText(name);
        say("Ready");
        if (host != null) {
            host.onScreenShown(index);
        }
    }

    // --- brand ----------------------------------------------------------

    private JPanel buildBrand() {
        JLabel mark = new JLabel("QP", JLabel.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    g2.setColor(Theme.ACCENT);
                    // Rounded on three corners, square on the bottom-right
                    // (7 7 7 2 in the HTML reference).
                    g2.fillRoundRect(0, 0, w, h, 14, 14);
                    g2.fillRect(w - 8, h - 8, 8, 8);
                    g2.fillRect(w - 8, h / 2, 8, h);
                    g2.fillRect(w / 2, h - 8, w, 8);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        mark.setOpaque(false);
        mark.setForeground(Color.WHITE);
        mark.setFont(mark.getFont().deriveFont(Font.BOLD, 12f));
        mark.setPreferredSize(new Dimension(30, 30));

        JLabel name = new JLabel("<html>Question Paper<br>Generator</html>");
        name.putClientProperty("FlatLaf.style", "font:bold");
        name.setForeground(Theme.INK);

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(18, 12, 12, 10));
        brand.add(mark);
        brand.add(name);
        return brand;
    }

    // --- nav renderer ---------------------------------------------------

    private final class NavRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean sel, boolean foc) {
            JLabel text = (JLabel) super.getListCellRendererComponent(
                    list, value, index, sel, foc);
            String v = value == null ? "" : value.toString();

            JPanel p = new JPanel(new BorderLayout());
            if (groups.contains(v)) {
                p.setOpaque(false);
                text.setForeground(Theme.MUTED);
                text.putClientProperty("FlatLaf.style", "font:-1");
                p.setBorder(new EmptyBorder(16, 20, 4, 8));
                p.add(text, BorderLayout.CENTER);
                return p;
            }

            p.setBackground(sel ? Theme.SURFACE : Theme.BG);
            text.setForeground(sel ? Theme.ACCENT_D : Theme.INK);
            text.putClientProperty("FlatLaf.style", sel ? "font:bold" : "font:normal");
            p.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(1, 8, 1, 8),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, sel ? 3 : 0, 0, 0,
                                    Theme.ACCENT),
                            new EmptyBorder(0, sel ? 8 : 11, 0, 10))));
            p.add(text, BorderLayout.CENTER);

            Integer n = badges.get(v);
            if (n != null) {
                JLabel b = Ui.muted(String.valueOf(n));
                b.setFont(b.getFont().deriveFont(11f));
                p.add(b, BorderLayout.EAST);
            }
            return p;
        }
    }
}
