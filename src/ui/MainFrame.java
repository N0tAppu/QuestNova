package ui;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Main application window with navigation between panels.
 * Subjects & Modules, Question Bank, Excel Import, Blueprint editor,
 * Generated Papers history.
 */
public class MainFrame extends JFrame {

    private static final String CARD_SUBJECTS = "subjects";
    private static final String CARD_QUESTIONS = "questions";
    private static final String CARD_IMPORT = "import";
    private static final String CARD_GENERATE = "generate";
    private static final String CARD_PAPERS = "papers";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
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

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton subjectsButton = new JButton("Subjects & Modules");
        JButton questionsButton = new JButton("Question Bank");
        JButton importButton = new JButton("Import Excel Question Bank");
        JButton generateButton = new JButton("Examination Blueprint");
        JButton papersButton = new JButton("Generated Papers");
        subjectsButton.addActionListener(e -> {
            cardLayout.show(cards, CARD_SUBJECTS);
            subjectPanel.refreshIfChanged();
        });
        questionsButton.addActionListener(e -> {
            cardLayout.show(cards, CARD_QUESTIONS);
            questionPanel.refreshFilters();
        });
        importButton.addActionListener(e -> {
            cardLayout.show(cards, CARD_IMPORT);
            importPanel.refreshPanel();
        });
        generateButton.addActionListener(e -> {
            cardLayout.show(cards, CARD_GENERATE);
            generatePaperPanel.refreshPanel();
        });
        papersButton.addActionListener(e -> {
            cardLayout.show(cards, CARD_PAPERS);
            generatedPapersPanel.refreshPanel();
        });
        nav.add(subjectsButton);
        nav.add(questionsButton);
        nav.add(importButton);
        nav.add(generateButton);
        nav.add(papersButton);

        cards.add(subjectPanel, CARD_SUBJECTS);
        cards.add(questionPanel, CARD_QUESTIONS);
        cards.add(importPanel, CARD_IMPORT);
        cards.add(generatePaperPanel, CARD_GENERATE);
        cards.add(generatedPapersPanel, CARD_PAPERS);

        setLayout(new BorderLayout());
        add(nav, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, CARD_SUBJECTS);
    }

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to the default look and feel.
        }
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
                    + "navigation buttons to load the data again.\n"
                    + "The application will open, but it cannot show any data until the\n"
                    + "connection works.",
                    "Database Connection", JOptionPane.WARNING_MESSAGE);
        }
    }
}
