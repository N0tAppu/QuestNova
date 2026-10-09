package ui;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Main application window with navigation between panels.
 * Subjects & Modules, Question Bank, Excel Import, Blueprint editor,
 * Generated Papers history.
 *
 * The navigation is a tabbed pane: the selected screen is obvious, the
 * screens keep their own refresh-on-enter behaviour, and re-clicking the
 * already active tab refreshes that screen like the old button bar did.
 */
public class MainFrame extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int sel = getSelectedIndex();
            if (sel < 0 || sel >= getTabCount() || getUI() == null) {
                return;
            }
            Rectangle r = getBoundsAt(sel);
            if (r == null || r.width <= 0 || r.height <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int bar = Math.min(4, r.height);
                g2.setPaint(UiTheme.brandGradient(r.x, r.x + r.width, 0));
                g2.fillRect(r.x, r.y + r.height - bar, r.width, bar);
            } finally {
                g2.dispose();
            }
        }
    };
    private final SubjectPanel subjectPanel = new SubjectPanel();
    private final QuestionPanel questionPanel = new QuestionPanel();
    private final ImportPanel importPanel = new ImportPanel();
    private final GeneratePaperPanel generatePaperPanel = new GeneratePaperPanel();
    private final GeneratedPapersPanel generatedPapersPanel = new GeneratedPapersPanel();

    public MainFrame() {
        setTitle("Question Paper Generator");
        setSize(1000, 650);
        setMinimumSize(new Dimension(1000, 650));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        tabs.addTab("Subjects & Modules", subjectPanel);
        tabs.addTab("Question Bank", questionPanel);
        tabs.addTab("Import Excel Question Bank", importPanel);
        tabs.addTab("Examination Blueprint", generatePaperPanel);
        tabs.addTab("Generated Papers", generatedPapersPanel);
        tabs.addChangeListener(e -> {
            refreshScreen(tabs.getSelectedIndex());
            focusCurrentScreen();
        });
        tabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = tabs.indexAtLocation(e.getX(), e.getY());
                if (index >= 0 && index == tabs.getSelectedIndex()) {
                    refreshScreen(index);
                }
            }
        });

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }

    /** Moves the keyboard focus into the screen that just became visible. */
    private void focusCurrentScreen() {
        Component selected = tabs.getSelectedComponent();
        SwingUtilities.invokeLater(() -> {
            if (!(selected instanceof Container)) {
                return;
            }
            Component target = firstInput((Container) selected);
            if (target != null) {
                target.requestFocusInWindow();
            }
        });
    }

    /** First data-entry control of the screen in traversal order. */
    private static Component firstInput(Container container) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JTextComponent || child instanceof JComboBox
                    || child instanceof JSpinner || child instanceof JList
                    || child instanceof JTable) {
                return child;
            }
            if (child instanceof Container) {
                Component nested = firstInput((Container) child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /** Refreshes the screen that is becoming visible. */
    private void refreshScreen(int index) {
        switch (index) {
            case 0:
                subjectPanel.refreshIfChanged();
                break;
            case 1:
                questionPanel.refreshFilters();
                break;
            case 2:
                importPanel.refreshPanel();
                break;
            case 3:
                generatePaperPanel.refreshPanel();
                break;
            case 4:
                generatedPapersPanel.refreshPanel();
                break;
            default:
                break;
        }
    }

    public static void launch() {
        UiTheme.install();
        SwingUtilities.invokeLater(() -> {
            warnIfDatabaseUnavailable();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    /** One startup connection check - at most one dialog when MySQL is down. */
    private static void warnIfDatabaseUnavailable() {
        try (java.sql.Connection ignored = database.DatabaseConnection.getConnection()) {
            // connected - silent
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Question Paper Generator could not connect to MySQL.\n\n"
                    + "Please start MySQL (for example through XAMPP) and then use the\n"
                    + "navigation to load the data again.\n"
                    + "The application will open, but it cannot show any data until the\n"
                    + "connection works.",
                    "Database Connection", JOptionPane.WARNING_MESSAGE);
        }
    }
}
