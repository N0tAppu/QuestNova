package ui;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
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

    private final JTabbedPane tabs = new JTabbedPane();
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
        tabs.addChangeListener(e -> refreshScreen(tabs.getSelectedIndex()));
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
