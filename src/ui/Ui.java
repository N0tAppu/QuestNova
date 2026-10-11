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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
        return btn(t, "primary");
    }

    /** Destructive action: red text, never a red fill. */
    public static JButton danger(String t) {
        return btn(t, "danger");
    }

    /** Neutral action: white with a hairline border. */
    public static JButton plain(String t) {
        return btn(t, "plain");
    }

    /** Text-only action, e.g. "Clear filters", "Download template". */
    public static JButton link(String t) {
        return btn(t, "link");
    }

    /** FlatLaf style string for a button kind, built from the live tokens. */
    private static String btnStyle(String kind) {
        switch (kind) {
            case "primary":
                return "arc:8;margin:5,14,5,14;background:" + Theme.hex(Theme.ACCENT)
                        + ";foreground:" + Theme.hex(Theme.ACCENT_FG)
                        + ";hoverBackground:" + Theme.hex(Theme.ACCENT_D)
                        + ";pressedBackground:" + Theme.hex(Theme.ACCENT_P)
                        + ";borderWidth:0;font:bold";
            case "danger":
                return "arc:8;margin:5,14,5,14;foreground:" + Theme.hex(Theme.BAD)
                        + ";hoverBackground:" + Theme.hex(Theme.BAD_BG)
                        + ";pressedBackground:" + Theme.hex(Theme.DANGER_OFF);
            case "plain":
                return "arc:8;margin:5,14,5,14;background:" + Theme.hex(Theme.SURFACE)
                        + ";foreground:" + Theme.hex(Theme.INK)
                        + ";borderColor:" + Theme.hex(Theme.CTRL_LINE) + ";borderWidth:1"
                        + ";hoverBackground:" + Theme.hex(Theme.BG)
                        + ";pressedBackground:" + Theme.hex(Theme.HEAD_BG);
            case "sidetoggle":
                return "arc:8;margin:6,12,6,12;foreground:" + Theme.hex(Theme.INK)
                        + ";background:null;hoverBackground:" + Theme.hex(Theme.TINT)
                        + ";borderWidth:0";
            case "link":
            default:
                return "arc:8;margin:5,14,5,14;foreground:" + Theme.hex(Theme.ACCENT_D)
                        + ";borderWidth:0;background:null"
                        + ";hoverBackground:" + Theme.hex(Theme.TINT);
        }
    }

    private static JButton btn(String t, String kind) {
        JButton b = new JButton(t);
        applyButtonStyle(b, kind);
        b.setMnemonic(0);
        return b;
    }

    /** Registers the button kind and applies its current-token style. */
    public static void applyButtonStyle(JButton b, String kind) {
        b.putClientProperty("qn.btn", kind);
        b.putClientProperty("FlatLaf.style", btnStyle(kind));
    }

    // --- token-bound colours ---------------------------------------------

    /** Resolves a token name to its live colour (unknown names -> INK). */
    public static Color token(String name) {
        switch (name) {
            case "BG": return Theme.BG;
            case "SIDE": return Theme.SIDE;
            case "SURFACE": return Theme.SURFACE;
            case "INPUT": return Theme.INPUT;
            case "INK": return Theme.INK;
            case "MUTED": return Theme.MUTED;
            case "LINE": return Theme.LINE;
            case "CTRL_LINE": return Theme.CTRL_LINE;
            case "HEAD_BG": return Theme.HEAD_BG;
            case "ROW_A": return Theme.ROW_A;
            case "ROW_B": return Theme.ROW_B;
            case "GRID": return Theme.GRID;
            case "HOVER": return Theme.HOVER;
            case "TRACK": return Theme.TRACK;
            case "ACCENT": return Theme.ACCENT;
            case "ACCENT_D": return Theme.ACCENT_D;
            case "ACCENT_P": return Theme.ACCENT_P;
            case "ACCENT_FG": return Theme.ACCENT_FG;
            case "TINT": return Theme.TINT;
            case "TINT_IDLE": return Theme.TINT_IDLE;
            case "OK": return Theme.OK;
            case "OK_BG": return Theme.OK_BG;
            case "WARN": return Theme.WARN;
            case "BAD": return Theme.BAD;
            case "BAD_BG": return Theme.BAD_BG;
            case "PRIMARY_OFF": return Theme.PRIMARY_OFF;
            case "DANGER_OFF": return Theme.DANGER_OFF;
            case "DISABLED_FG": return Theme.DISABLED_FG;
            case "FOCUS": return Theme.FOCUS;
            default: return Theme.INK;
        }
    }

    /**
     * Sets a foreground from a token AND registers the token name, so a
     * later theme switch can re-apply the fresh colour ({@link #retheme}).
     */
    public static <T extends JComponent> T fg(T c, String token) {
        c.setForeground(token(token));
        c.putClientProperty("qn.fg", token);
        return c;
    }

    /**
     * Sets a background from a token AND registers the token name, so a
     * later theme switch can re-apply the fresh colour ({@link #retheme}).
     */
    public static <T extends JComponent> T bg(T c, String token) {
        c.setBackground(token(token));
        c.putClientProperty("qn.bg", token);
        return c;
    }

    /**
     * Re-applies every token-bound property (buttons, foregrounds,
     * backgrounds, card borders, table colours) under {@code root} after a
     * theme switch. Renderers read Theme at paint time and need no help.
     */
    public static void retheme(Component root) {
        if (root instanceof JComponent) {
            rethemeOne((JComponent) root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                retheme(child);
            }
        }
    }

    private static void rethemeOne(JComponent j) {
        String kind = (String) j.getClientProperty("qn.btn");
        if (kind != null) {
            j.putClientProperty("FlatLaf.style", btnStyle(kind));
        }
        String fgTok = (String) j.getClientProperty("qn.fg");
        if (fgTok != null) {
            j.setForeground(token(fgTok));
        }
        String bgTok = (String) j.getClientProperty("qn.bg");
        if (bgTok != null) {
            j.setBackground(token(bgTok));
        }
        if (j.getClientProperty("qn.card") != null) {
            j.setBackground(Theme.SURFACE);
            j.setBorder(new FlatLineBorder(new Insets(0, 0, 0, 0),
                    Theme.LINE, 1f, Theme.CARD_ARC));
        }
        if (j instanceof JTable && j.getClientProperty("qn.table") != null) {
            applyTableColors((JTable) j);
        }
        if (j instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) j;
            String vp = (String) sp.getClientProperty("qn.viewport");
            if (vp != null && sp.getViewport() != null) {
                sp.getViewport().setBackground(token(vp));
            } else if (sp.getViewport() != null
                    && sp.getViewport().getClientProperty("qn.bg") == null) {
                // Plain scroll pane (e.g. syllabus JLists): FlatLaf caches
                // the viewport background at install time; reset it so the
                // list body follows the current mode after a switch.
                Color uiVp = javax.swing.UIManager.getColor("List.background");
                if (uiVp == null) {
                    uiVp = javax.swing.UIManager.getColor("Panel.background");
                }
                if (uiVp != null) {
                    sp.getViewport().setBackground(uiVp);
                }
            }
        }
        // Fallback: FlatLaf UI delegates cache background at install time and
        // updateComponentTreeUI does not re-read UIManager for them. Reset
        // unregistered common component types from the live UIManager values
        // so an already-constructed tree never retains the previous theme's
        // colours after a switch. Skip components already owned by a qn.*
        // registration (qn.bg, qn.card, qn.btn, qn.table). Not gated on
        // isOpaque(): FlatLaf inputs report false but still carry a stale
        // background field that some UI delegates consult when painting.
        if (bgTok == null
                && j.getClientProperty("qn.card") == null
                && j.getClientProperty("qn.btn") == null
                && j.getClientProperty("qn.table") == null) {
            Color uiBg = lookupUiBackground(j);
            if (uiBg != null) {
                j.setBackground(uiBg);
            }
        }
        if (fgTok == null && j.getClientProperty("qn.btn") == null) {
            Color uiFg = lookupUiForeground(j);
            if (uiFg != null) {
                j.setForeground(uiFg);
            }
        }
    }

    /** UIManager background key for a common component type, or null. */
    private static Color lookupUiBackground(JComponent j) {
        if (j instanceof javax.swing.JComboBox) {
            return javax.swing.UIManager.getColor("ComboBox.background");
        }
        if (j instanceof javax.swing.JSpinner) {
            return javax.swing.UIManager.getColor("Spinner.background");
        }
        if (j instanceof javax.swing.JTextField
                || j instanceof javax.swing.JTextArea
                || j instanceof javax.swing.JFormattedTextField) {
            return javax.swing.UIManager.getColor("TextField.background");
        }
        if (j instanceof javax.swing.JCheckBox) {
            return javax.swing.UIManager.getColor("CheckBox.background");
        }
        if (j instanceof javax.swing.JRadioButton) {
            return javax.swing.UIManager.getColor("RadioButton.background");
        }
        if (j instanceof javax.swing.JList) {
            return javax.swing.UIManager.getColor("List.background");
        }
        if (j instanceof javax.swing.table.JTableHeader) {
            return javax.swing.UIManager.getColor("TableHeader.background");
        }
        if (j instanceof javax.swing.JPanel) {
            return javax.swing.UIManager.getColor("Panel.background");
        }
        if (j instanceof javax.swing.JToolTip) {
            return javax.swing.UIManager.getColor("ToolTip.background");
        }
        return null;
    }

    /** UIManager foreground key for a common component type, or null. */
    private static Color lookupUiForeground(JComponent j) {
        if (j instanceof javax.swing.JComboBox) {
            return javax.swing.UIManager.getColor("ComboBox.foreground");
        }
        if (j instanceof javax.swing.JTextField
                || j instanceof javax.swing.JTextArea
                || j instanceof javax.swing.JFormattedTextField) {
            return javax.swing.UIManager.getColor("TextField.foreground");
        }
        if (j instanceof javax.swing.JCheckBox) {
            return javax.swing.UIManager.getColor("CheckBox.foreground");
        }
        if (j instanceof javax.swing.JRadioButton) {
            return javax.swing.UIManager.getColor("RadioButton.foreground");
        }
        if (j instanceof javax.swing.JList) {
            return javax.swing.UIManager.getColor("List.foreground");
        }
        if (j instanceof javax.swing.table.JTableHeader) {
            return javax.swing.UIManager.getColor("TableHeader.foreground");
        }
        if (j instanceof javax.swing.JPanel) {
            return javax.swing.UIManager.getColor("Panel.foreground");
        }
        if (j instanceof javax.swing.JToolTip) {
            return javax.swing.UIManager.getColor("ToolTip.foreground");
        }
        return null;
    }

    // --- text -----------------------------------------------------------

    public static JLabel muted(String t) {
        return fg(new JLabel(t), "MUTED");
    }

    public static JLabel bold(String t) {
        TruncLabel l = new TruncLabel(t);
        fg(l, "INK");
        return l;
    }

    /**
     * A bold heading that ellipsizes instead of hard-clipping when its cell
     * is narrower than the text, so long card titles ("Modules in &lt;subject&gt;")
     * degrade gracefully instead of overlapping the actions beside them.
     */
    public static final class TruncLabel extends JLabel {

        private TruncLabel(String text) {
            super(text);
            putClientProperty("FlatLaf.style", "font:bold");
            setForeground(Theme.INK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            String text = getText();
            if (text == null || text.isEmpty() || text.startsWith("<html>")) {
                super.paintComponent(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.clipRect(0, 0, getWidth(), getHeight());
                FontMetrics fm = g2.getFontMetrics(getFont());
                Insets in = getInsets();
                int avail = getWidth() - in.left - in.right;
                String draw = text;
                if (fm.stringWidth(draw) > avail) {
                    String ell = "...";
                    int end = text.length();
                    while (end > 0
                            && fm.stringWidth(text.substring(0, end) + ell) > avail) {
                        end--;
                    }
                    draw = end > 0 ? text.substring(0, end) + ell : ell;
                }
                int x = in.left;
                int y = in.top + Math.max(0, (getHeight() - in.top - in.bottom
                        - fm.getHeight()) / 2) + fm.getAscent();
                g2.setColor(getForeground());
                g2.setFont(getFont());
                g2.drawString(draw, x, y);
            } finally {
                g2.dispose();
            }
        }
    }

    // --- page header ----------------------------------------------------

    /**
     * Title + hint on the left, optional actions on the right. Sits at the
     * top of every screen and replaces the old coloured banner strip.
     */
    public static JPanel pageHeader(String title, String hint, JComponent... actions) {
        JLabel t = new JLabel(title);
        t.putClientProperty("FlatLaf.style", "font:+7 bold");
        fg(t, "INK");
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
     * A white card with a tinted header row (title left, actions right) and a
     * hairline rule under the header. The HEAD_BG tint separates the panel
     * header from the card body.
     */
    public static JPanel card(JComponent title, JComponent body, JComponent... actions) {
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(true);
        bg(head, "HEAD_BG");
        head.setBorder(new EmptyBorder(Theme.S, Theme.M, Theme.S, Theme.S));
        // CENTER, not WEST: WEST never shrinks, so a long title paints over
        // the actions when the header is narrow. CENTER yields to EAST.
        head.add(title, BorderLayout.CENTER);
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
        c.putClientProperty("qn.cardBody", body);
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
        c.putClientProperty("qn.card", "1");
        bg(c, "SURFACE");
        c.setBorder(new FlatLineBorder(new Insets(0, 0, 0, 0), Theme.LINE, 1f,
                Theme.CARD_ARC));
        return c;
    }

    /** A 1px hairline separator. */
    public static JComponent rule() {
        JPanel r = new JPanel();
        bg(r, "LINE");
        r.setPreferredSize(new Dimension(0, 1));
        return r;
    }

    // --- tables ---------------------------------------------------------

    /**
     * Applies the shared table presentation. Numeric columns must return a
     * Number from getColumnClass - they right-align and their headers follow.
     */
    public static void styleTable(JTable t) {
        t.putClientProperty("qn.table", "1");
        applyTableColors(t);
        t.setRowHeight(Theme.ROW);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setDefaultRenderer(Object.class, cell(SwingConstants.LEFT, false));
        t.setDefaultRenderer(String.class, cell(SwingConstants.LEFT, false));
        t.setDefaultRenderer(Number.class, cell(SwingConstants.RIGHT, false));
        // Hover wash on the row under the cursor (subordinate to selection).
        t.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                markHover(t, t.rowAtPoint(e.getPoint()));
            }
        });
        t.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                markHover(t, -1);
            }
        });
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(0, 34));
        h.setDefaultRenderer(new HeaderRenderer());
    }

    private static void markHover(JTable t, int row) {
        Integer prev = (Integer) t.getClientProperty("qn.hoverRow");
        if (prev == null ? row != -1 : prev != row) {
            t.putClientProperty("qn.hoverRow", row);
            t.repaint();
        }
    }

    /** Sets a scroll viewport's background from a token, registered for retheme. */
    public static JScrollPane viewport(JScrollPane sp, String tokenName) {
        sp.putClientProperty("qn.viewport", tokenName);
        if (sp.getViewport() != null) {
            sp.getViewport().setBackground(token(tokenName));
        }
        return sp;
    }

    /** Re-applies token-driven table colours (used by styleTable + retheme). */
    private static void applyTableColors(JTable t) {
        t.setBackground(Theme.ROW_A);
        t.setForeground(Theme.INK);
        t.setSelectionBackground(Theme.TINT);
        t.setSelectionForeground(Theme.INK);
        t.setGridColor(Theme.GRID);
        JTableHeader h = t.getTableHeader();
        if (h != null) {
            h.repaint();
        }
    }

    /** Convenience: shared table presentation wrapped in a borderless scroll pane. */
    public static JScrollPane table(JTable t) {
        styleTable(t);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.putClientProperty("qn.viewport", "ROW_A");
        sp.getViewport().setBackground(Theme.ROW_A);
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
                // Selected rows carry a 3px accent bar on their first cell:
                // a positional (non-colour) cue that survives greyscale
                // perception and any wash colour. Alternating rows stripe.
                if (sel && c == 0) {
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0,
                                    Theme.ACCENT),
                            new EmptyBorder(0, Theme.M - 3, 0, Theme.M)));
                } else {
                    setBorder(new EmptyBorder(0, Theme.M, 0, Theme.M));
                }
                if (dot && v != null) {
                    setText("<html><font color='" + Theme.hex(
                            Theme.difficulty(v.toString())) + "'>&#9679;</font>&nbsp; "
                            + v + "</html>");
                }
                if (sel) {
                    setBackground(Theme.TINT);
                } else {
                    Integer hover = (Integer) t.getClientProperty("qn.hoverRow");
                    if (hover != null && hover == r) {
                        setBackground(Theme.HOVER);
                    } else {
                        setBackground((r & 1) == 1 ? Theme.ROW_B : Theme.ROW_A);
                    }
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
