package ui;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;

/**
 * Main application window: a light sidebar shell ({@link AppShell}) hosting
 * the five screens in reference order -
 * Subjects &amp; modules, Import questions, Question bank, Blueprint,
 * Generated papers - grouped under "Content" and "Papers".
 *
 * The shell reports every screen change (including a re-click of the
 * already active entry) so each screen keeps its refresh-on-enter
 * behaviour, and moves the keyboard focus into the screen that just
 * became visible.
 */
public class MainFrame extends JFrame {

    // Screen order here MUST match the AppShell registration order below.
    private final SubjectPanel subjectPanel = new SubjectPanel();
    private final ImportPanel importPanel = new ImportPanel();
    private final QuestionPanel questionPanel = new QuestionPanel();
    private final GeneratePaperPanel generatePaperPanel = new GeneratePaperPanel();
    private final GeneratedPapersPanel generatedPapersPanel = new GeneratedPapersPanel();

    private final Component[] screens = {
            subjectPanel, importPanel, questionPanel,
            generatePaperPanel, generatedPapersPanel
    };

    private final AppShell shell = new AppShell();

    public MainFrame() {
        setTitle("Question Paper Generator");
        setSize(1000, 640);
        setMinimumSize(new java.awt.Dimension(1000, 640));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        shell.group("Content");
        shell.screen("Subjects & modules", subjectPanel);
        shell.screen("Import questions", importPanel);
        shell.screen("Question bank", questionPanel);
        shell.group("Papers");
        shell.screen("Blueprint", generatePaperPanel);
        shell.screen("Generated papers", generatedPapersPanel);
        shell.setHost(this::onScreenShown);

        setContentPane(shell);
        shell.bindShortcuts();
    }

    /** Refreshes the screen that just became visible, then focuses it. */
    private void onScreenShown(int screenIndex) {
        refreshScreen(screenIndex);
        focusScreen(screenIndex);
    }

    /**
     * Moves the keyboard focus into the screen that just became visible.
     * Focus order then follows the visual order inside that screen.
     */
    private void focusScreen(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= screens.length) {
            return;
        }
        Component selected = screens[screenIndex];
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
    private void refreshScreen(int screenIndex) {
        switch (screenIndex) {
            case 0:
                subjectPanel.refreshIfChanged();
                shell.badge("Subjects & modules", subjectPanel.getSubjectRowCount());
                break;
            case 1:
                importPanel.refreshPanel();
                break;
            case 2:
                questionPanel.refreshFilters();
                shell.badge("Question bank", questionPanel.getQuestionRowCount());
                break;
            case 3:
                generatePaperPanel.refreshPanel();
                break;
            case 4:
                generatedPapersPanel.refreshPanel();
                shell.badge("Generated papers", generatedPapersPanel.getRowCount());
                break;
            default:
                break;
        }
    }

    public static void launch() {
        Theme.install();
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
