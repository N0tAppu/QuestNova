package ui;

import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.IntConsumer;

/**
 * Reusable presentation components. Screens compose these; they never style
 * raw Swing widgets themselves.
 *
 * Rules encoded here (see DESIGN.md):
 * - one primary button per card / screen area
 * - danger buttons are red text, never a red fill outside a confirm dialog
 * - link buttons are text-only, for low-weight actions
 * - grouped regions are cards with a header row - never TitledBorder
 * - numeric table columns must return a Number from getColumnClass so they
 *   right-align automatically
 */
public final class Ui {

    private Ui() {
    }

    // --- buttons --------------------------------------------------------

    /** The one primary action of a card or screen area. */
    public static JButton primary(String t) {
        return btn(t, "background:#0E6B7E;foreground:#FFFFFF;"
                + "hoverBackground:#0A4F5E;pressedBackground:#083F4B;"
                + "borderWidth:0;font:bold");
    }

    /** Destructive action: red text, never a red fill. */
    public static JButton danger(String t) {
        return btn(t, "foreground:#B3261E;hoverBackground:#FBEDEC;"
                + "pressedBackground:#F7E4E3");
    }

    /** Neutral action: white with a hairline border. */
    public static JButton plain(String t) {
        return btn(t, "background:#FFFFFF;foreground:#1C2326;"
                + "borderColor:#C9C5BC;borderWidth:1;"
                + "hoverBackground:#F6F5F2;pressedBackground:#ECEAE4");
    }

    /** Text-only action, e.g. "Clear filters", "Download template". */
    public static JButton link(String t) {
        return btn(t, "foreground:#0E6B7E;borderWidth:0;background:null;"
                + "hoverBackground:#E4F1F3");
    }

    private static JButton btn(String t, String style) {
        JButton b = new JButton(t);
        b.putClientProperty("FlatLaf.style", "arc:8;margin:5,14,5,14;" + style);
        b.setMnemonic(0);
        return b;
    }

    // --- text -----------------------------------------------------------

    public static JLabel muted(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.MUTED);
        return l;
    }

    public static JLabel bold(String t) {
        JLabel l = new JLabel(t);
        l.putClientProperty("FlatLaf.style", "font:bold");
        l.setForeground(Theme.INK);
        return l;
    }

    // --- page header ----------------------------------------------------

    /**
     * Title + hint on the left, optional actions on the right. Sits at the
     * top of every screen and replaces the old coloured banner strip.
     */
    public static JPanel pageHeader(String title, String hint, JComponent... actions) {
        JLabel t = new JLabel(title);
        t.putClientProperty("FlatLaf.style", "font:+7 bold");
        t.setForeground(Theme.INK);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(t);
        text.add(muted(hint == null ? "" : hint));
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(text, BorderLayout.WEST);
        if (actions != null && actions.length > 0) {
            p.add(row(actions), BorderLayout.EAST);
        }
        return p;
    }

    /** A right-aligned flow row of components (toolbar / header actions). */
    public static JPanel row(JComponent... items) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.S, 0));
        r.setOpaque(false);
        if (items != null) {
            for (JComponent c : items) {
                if (c != null) {
                    r.add(c);
                }
            }
        }
        return r;
    }

    /** A left-aligned flow row of components (button rows inside cards). */
    public static JPanel leftRow(JComponent... items) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.S, 0));
        r.setOpaque(false);
        if (items != null) {
            for (JComponent c : items) {
                if (c != null) {
                    r.add(c);
                }
            }
        }
        return r;
    }

    // --- cards ----------------------------------------------------------

    /**
     * A white card with a header row (title left, actions right) and a
     * hairline rule under the header.
     */
    public static JPanel card(JComponent title, JComponent body, JComponent... actions) {
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(Theme.S, Theme.M, Theme.S, Theme.S));
        head.add(title, BorderLayout.WEST);
        if (actions != null && actions.length > 0) {
            head.add(row(actions), BorderLayout.EAST);
        }
        head.setPreferredSize(new Dimension(0, 46));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.CENTER);
        top.add(rule(), BorderLayout.SOUTH);
        JPanel c = bareCard();
        c.add(top, BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    /** A card with a plain string title. */
    public static JPanel card(String title, JComponent body, JComponent... actions) {
        return card(bold(title), body, actions);
    }

    /** Card with no header (filter bars, detail panes, tables). */
    public static JPanel bareCard() {
        JPanel c = new JPanel(new BorderLayout());
        c.setBackground(Theme.SURFACE);
        c.setBorder(new FlatLineBorder(new Insets(0, 0, 0, 0), Theme.LINE, 1f,
                Theme.CARD_ARC));
        return c;
    }

    /** A 1px hairline separator. */
    public static JComponent rule() {
        JPanel r = new JPanel();
        r.setBackground(Theme.LINE);
        r.setPreferredSize(new Dimension(0, 1));
        return r;
    }

    // --- tables ---------------------------------------------------------

    /**
     * Applies the shared table presentation. Numeric columns must return a
     * Number from getColumnClass - they right-align and their headers follow.
     */
    public static void styleTable(JTable t) {
        t.setRowHeight(Theme.ROW);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xF0EEE9));
        t.setBackground(Theme.SURFACE);
        t.setForeground(Theme.INK);
        t.setSelectionBackground(Theme.TINT);
        t.setSelectionForeground(Theme.INK);
        t.setDefaultRenderer(Object.class, cell(SwingConstants.LEFT, false));
        t.setDefaultRenderer(String.class, cell(SwingConstants.LEFT, false));
        t.setDefaultRenderer(Number.class, cell(SwingConstants.RIGHT, false));
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(0, 34));
        h.setDefaultRenderer(new HeaderRenderer());
    }

    /** Convenience: shared table presentation wrapped in a borderless scroll pane. */
    public static JScrollPane table(JTable t) {
        styleTable(t);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        return sp;
    }

    /** Applies the coloured-dot renderer to a Difficulty column. */
    public static void difficultyColumn(JTable t, int modelCol) {
        int view = t.convertColumnIndexToView(modelCol);
        if (view < 0) {
            return;
        }
        t.getColumnModel().getColumn(view)
                .setCellRenderer(cell(SwingConstants.LEFT, true));
    }

    /**
     * Selection listener that hands back the MODEL row index (or -1).
     * Sorting-safe. Use this instead of reading getSelectedRow() directly.
     */
    public static void onSelect(JTable t, IntConsumer cb) {
        t.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int v = t.getSelectedRow();
            cb.accept(v < 0 ? -1 : t.convertRowIndexToModel(v));
        });
    }

    private static DefaultTableCellRenderer cell(int align, boolean dot) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                setHorizontalAlignment(align);
                setBorder(new EmptyBorder(0, Theme.M, 0, Theme.M));
                if (dot && v != null) {
                    String hex = String.format("#%06X",
                            Theme.difficulty(v.toString()).getRGB() & 0xFFFFFF);
                    setText("<html><font color='" + hex + "'>&#9679;</font>&nbsp; "
                            + v + "</html>");
                }
                if (!sel) {
                    setBackground(Theme.SURFACE);
                }
                if (v != null) {
                    String s = v.toString();
                    setToolTipText(s.length() > 40 ? s : null);
                }
                return this;
            }
        };
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, false, false, r, c);
            boolean num = Number.class.isAssignableFrom(t.getColumnClass(c));
            setHorizontalAlignment(num ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setForeground(Theme.MUTED);
            setBackground(Theme.HEAD_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                    new EmptyBorder(0, Theme.M, 0, Theme.M)));
            return this;
        }
    }

    // --- meter ----------------------------------------------------------

    /**
     * Allocation progress bar for the Blueprint screen: green when the
     * marks add up exactly, red when over, teal while still short.
     */
    public static final class Meter extends JComponent {
        private int value;
        private int target = 1;

        public Meter() {
            setPreferredSize(new Dimension(200, 8));
        }

        public void set(int value, int target) {
            this.value = value;
            this.target = Math.max(1, target);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g.setColor(Theme.TRACK);
                g.fillRoundRect(0, 0, w, h, h, h);
                g.setColor(value == target ? Theme.OK
                        : value > target ? Theme.BAD : Theme.ACCENT);
                int fill = Math.max(h, Math.min(w, w * value / target));
                g.fillRoundRect(0, 0, fill, h, h, h);
            } finally {
                g.dispose();
            }
        }
    }

    // --- dialogs --------------------------------------------------------

    /**
     * Confirmation dialog whose button text names the action
     * ("Delete question", never "OK"). Returns true when confirmed.
     */
    public static boolean confirm(Component parent, String title, String body,
            String okText) {
        Object[] opts = {okText, "Cancel"};
        return JOptionPane.showOptionDialog(parent, body, title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                opts, opts[1]) == 0;
    }

    // --- misc -----------------------------------------------------------

    /** Applies the shared panel padding used by every screen. */
    public static void pad(JComponent c) {
        c.setBorder(new EmptyBorder(Theme.XL, 28, Theme.L, 28));
    }
}
