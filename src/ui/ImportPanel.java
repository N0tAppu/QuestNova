package ui;

import com.formdev.flatlaf.ui.FlatLineBorder;

import model.Question;
import model.Subject;
import service.ExcelImportService;
import service.QuestionService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Import Excel Question Bank screen, laid out as the three numbered steps of
 * the reference design: choose the subject, choose the Excel file, review and
 * import. A display-only check previews the validation result; the destructive
 * actions sit collapsed below the card.
 *
 * Flow: select subject -> select .xlsx -> validate entire file ->
 * show summary -> Import All / Import Valid Rows Only / Cancel.
 * All SQL and business logic live in the service layer.
 */
public class ImportPanel extends JPanel {

    /** Card width cap from the reference ("max-width:760px"). */
    private static final int CARD_MAX_WIDTH = 760;
    /** Subject combo width from the reference ("width:280px"). */
    private static final int SUBJECT_FIELD_WIDTH = 280;

    private final ExcelImportService importService = new ExcelImportService();
    private final QuestionService questionService = new QuestionService();

    private final JComboBox<SubjectOption> subjectBox = new JComboBox<>();
    private final JLabel fileLabel = Ui.muted("No file chosen");
    private final JButton importButton = Ui.primary("Import questions");
    private final JButton deleteFromFileButton =
            Ui.danger("Delete questions from this file");
    private final JButton clearSubjectButton = Ui.danger("Clear entire subject");

    /** Slot that swaps between the idle text, the summary and a failure text. */
    private final JPanel checkArea = new JPanel(new BorderLayout());
    private final JPanel summaryPanel = new JPanel(new BorderLayout());
    private final JLabel idleLabel =
            Ui.muted("Choose a subject and a file to run the check.");
    private final JLabel checkFailureLabel = Ui.muted("");
    private final JLabel checkSummaryLabel = Ui.bold("");

    /** Destructive actions, hidden until "Remove questions instead" is pressed. */
    private final JPanel removeActions = new JPanel(new BorderLayout(0, Theme.S));

    private JPanel card;

    /** True while the constructor runs: startup errors must not open dialogs. */
    private boolean initializing = true;

    public ImportPanel() {
        setLayout(new BorderLayout(0, Theme.L));
        setOpaque(false);
        add(Ui.pageHeader("Import questions",
                "The whole file is checked first. Nothing is saved until you confirm."),
                BorderLayout.NORTH);

        checkArea.add(idleLabel, BorderLayout.CENTER);
        Ui.bg(summaryPanel, "OK_BG");
        summaryPanel.setBorder(new EmptyBorder(Theme.S, Theme.M, Theme.S, Theme.M));
        Ui.fg(checkSummaryLabel, "OK");
        summaryPanel.add(checkSummaryLabel, BorderLayout.CENTER);

        JButton chooseButton = Ui.plain("Choose file (.xlsx)");
        JButton templateButton = Ui.link("Download template");
        chooseButton.addActionListener(e -> onChooseFile());
        templateButton.addActionListener(e -> onDownloadTemplate());
        importButton.addActionListener(e -> onImport());
        deleteFromFileButton.addActionListener(e -> onDeleteFromFile());
        clearSubjectButton.addActionListener(e -> onClearSubject());
        subjectBox.addActionListener(e -> updateImportButtonState());
        subjectBox.setPreferredSize(new Dimension(SUBJECT_FIELD_WIDTH,
                subjectBox.getPreferredSize().height));

        // Step 1 - choose the subject.
        JPanel subjectRow = Ui.leftRow(subjectBox);

        // Step 2 - choose the Excel file, plus the expected-columns note and
        // the decorative sample table from the reference.
        JPanel fileRow = new JPanel(new BorderLayout(Theme.S, 0));
        fileRow.setOpaque(false);
        fileRow.add(Ui.leftRow(chooseButton, fileLabel), BorderLayout.WEST);
        fileRow.add(templateButton, BorderLayout.EAST);
        JPanel step2Body = column(fileRow,
                Ui.muted("Columns in this order. Subject comes from step 1, not the file."),
                sampleTable());

        // Step 3 - live check result and the one primary action.
        JPanel reviewBody = new JPanel(new BorderLayout(0, Theme.S));
        reviewBody.setOpaque(false);
        reviewBody.add(checkArea, BorderLayout.NORTH);
        reviewBody.add(Ui.leftRow(importButton), BorderLayout.CENTER);

        card = Ui.bareCard();
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel steps = new JPanel();
        steps.setOpaque(false);
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        steps.add(stepRow(1, "Choose the subject", subjectRow));
        steps.add(Ui.rule());
        steps.add(stepRow(2, "Choose the Excel file", step2Body));
        steps.add(Ui.rule());
        steps.add(stepRow(3, "Review and import", reviewBody));
        card.add(steps, BorderLayout.CENTER);

        // Collapsed destructive section below the card.
        JButton removeToggle = Ui.danger("Remove questions instead");
        removeToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        removeActions.setOpaque(false);
        removeActions.setVisible(false);
        removeActions.add(Ui.leftRow(deleteFromFileButton, clearSubjectButton,
                Ui.muted("Permanent. Cannot be undone.")), BorderLayout.CENTER);
        removeToggle.addActionListener(e ->
                removeActions.setVisible(!removeActions.isVisible()));
        JPanel removeSection = new JPanel();
        removeSection.setOpaque(false);
        removeSection.setLayout(new BoxLayout(removeSection, BoxLayout.Y_AXIS));
        removeActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        removeSection.add(removeToggle);
        removeSection.add(removeActions);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        removeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(card);
        content.add(Box.createVerticalStrut(Theme.L));
        content.add(removeSection);
        add(content, BorderLayout.CENTER);

        try {
            loadSubjects();
            updateImportButtonState();
        } finally {
            initializing = false;
        }
    }

    // =====================================================================
    // Step layout helpers
    // =====================================================================

    /** One numbered step: round badge on the left, title + content on the right. */
    private static JPanel stepRow(int number, String title, JComponent body) {
        JPanel row = new JPanel(new BorderLayout(Theme.M, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(Theme.L, Theme.M, Theme.L, Theme.M));
        JPanel badgeSlot = new JPanel(new BorderLayout());
        badgeSlot.setOpaque(false);
        badgeSlot.add(stepBadge(number), BorderLayout.NORTH);
        row.add(badgeSlot, BorderLayout.WEST);
        JPanel text = new JPanel(new BorderLayout(0, Theme.S));
        text.setOpaque(false);
        text.add(Ui.bold(title), BorderLayout.NORTH);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(body, BorderLayout.CENTER);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /** The outlined round step number of the reference (24px accent ring). */
    private static JLabel stepBadge(int number) {
        JLabel badge = new JLabel(String.valueOf(number), SwingConstants.CENTER);
        badge.setOpaque(false);
        Ui.fg(badge, "ACCENT_D");
        badge.putClientProperty("FlatLaf.style", "font:bold");
        badge.setPreferredSize(new Dimension(Theme.XL, Theme.XL));
        badge.setMinimumSize(new Dimension(Theme.XL, Theme.XL));
        badge.setMaximumSize(new Dimension(Theme.XL, Theme.XL));
        badge.setBorder(new FlatLineBorder(new Insets(0, 0, 0, 0),
                Theme.ACCENT, 1.5f, Theme.ROW));
        return badge;
    }

    /** A vertical stack of left-aligned components with a small gap. */
    private static JPanel column(JComponent... items) {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        boolean first = true;
        for (JComponent item : items) {
            if (!first) {
                stack.add(Box.createVerticalStrut(Theme.S));
            }
            first = false;
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(item);
        }
        return stack;
    }

    /**
     * The decorative "expected columns" table of the reference: one muted
     * sample row, never editable and not part of the tab order.
     */
    private static JComponent sampleTable() {
        String[] columns = {"Question", "Answer", "Module", "Marks", "Difficulty"};
        Object[][] sample = {{"What is merge sort?", "Divide, sort, merge",
                "Sorting and Searching", Integer.valueOf(2), "Easy"}};
        DefaultTableModel model = new DefaultTableModel(sample, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 3 ? Integer.class : String.class;
            }
        };
        JTable table = new JTable(model);
        table.setDefaultEditor(Object.class, null);
        table.setRowSelectionAllowed(false);
        table.setFocusable(false);
        JScrollPane scroll = Ui.table(table);
        table.setDefaultRenderer(Object.class, mutedCell(SwingConstants.LEFT));
        table.setDefaultRenderer(String.class, mutedCell(SwingConstants.LEFT));
        table.setDefaultRenderer(Number.class, mutedCell(SwingConstants.RIGHT));
        scroll.setBorder(new FlatLineBorder(new Insets(0, 0, 0, 0),
                Theme.LINE, 1f, Theme.CONTROL_ARC));
        int rows = table.getTableHeader().getPreferredSize().height
                + (table.getRowHeight() + Theme.XS) * Math.max(1, table.getRowCount());
        scroll.setPreferredSize(new Dimension(
                scroll.getPreferredSize().width, rows));
        return scroll;
    }

    /** Muted cell renderer for the decorative sample row. */
    private static DefaultTableCellRenderer mutedCell(int align) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, false, false, row, column);
                setHorizontalAlignment(align);
                setForeground(Theme.MUTED);
                setBackground(Theme.ROW_A);
                setBorder(new EmptyBorder(0, Theme.M, 0, Theme.M));
                return this;
            }
        };
    }

    /**
     * Caps the card at the reference width without freezing its height, so
     * the live-check summary can still grow the card.
     */
    private void sizeCard() {
        if (card == null) {
            return;
        }
        card.setPreferredSize(null);
        Dimension pref = card.getPreferredSize();
        card.setPreferredSize(new Dimension(CARD_MAX_WIDTH, pref.height));
        card.setMaximumSize(new Dimension(CARD_MAX_WIDTH, Integer.MAX_VALUE));
    }

    // =====================================================================
    // Live check (display only - onImport still validates on its own)
    // =====================================================================

    private void setCheckView(JComponent view) {
        checkArea.removeAll();
        checkArea.add(view, BorderLayout.CENTER);
        checkArea.revalidate();
        checkArea.repaint();
    }

    private void setCheckFailure(String message) {
        importButton.setText("Import questions");
        checkFailureLabel.setText(message);
        setCheckView(checkFailureLabel);
    }

    /** Shows a validation summary; N &gt; 0 renames the primary action. */
    private void setCheckSummary(String summary, int rows) {
        checkSummaryLabel.setText(summary);
        importButton.setText(rows > 0
                ? "Import " + rows + " questions" : "Import questions");
        setCheckView(summaryPanel);
    }

    /**
     * Runs the existing validation for DISPLAY ONLY and shows its summary.
     * Never inserts anything and never opens a dialog; problems are reported
     * as muted text in the step.
     */
    private void updateCheckPreview() {
        SubjectOption subject = (SubjectOption) subjectBox.getSelectedItem();
        boolean ready = subject != null && subject.id >= 0
                && chosenFile != null && chosenFile.exists();
        if (!ready) {
            importButton.setText("Import questions");
            setCheckView(idleLabel);
            return;
        }
        ExcelImportService.ImportReport report;
        try {
            report = importService.validate(chosenFile, subject.id);
        } catch (IOException ex) {
            setCheckFailure("Check could not run: the file could not be read.");
            return;
        } catch (SQLException ex) {
            setCheckFailure("Check could not run: the question bank is not reachable.");
            return;
        } catch (Exception ex) {
            setCheckFailure("Check could not run: the file could not be processed.");
            return;
        }
        if (!report.isHeaderValid()) {
            setCheckSummary("Check failed: " + report.getHeaderError(), 0);
            return;
        }
        int rows = report.getTotalRows();
        int errors = report.getInvalidCount();
        if (errors == 0) {
            setCheckSummary("Check passed: " + rows + " rows, 0 errors.", rows);
        } else {
            setCheckSummary("Check failed: " + errors + " errors.", 0);
        }
    }

    private File chosenFile;

    /** Import needs a real subject AND an existing Excel file. */
    private void updateImportButtonState() {
        SubjectOption subject = (SubjectOption) subjectBox.getSelectedItem();
        boolean subjectSelected = subject != null && subject.id >= 0;
        boolean fileChosen = chosenFile != null && chosenFile.exists();
        importButton.setEnabled(subjectSelected && fileChosen);
        deleteFromFileButton.setEnabled(subjectSelected && fileChosen);
        clearSubjectButton.setEnabled(subjectSelected);
        updateCheckPreview();
        sizeCard();
    }

    private void onChooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel files (*.xlsx)", "xlsx"));
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".xlsx")) {
                showError("Please select a .xlsx file.");
                return;
            }
            chosenFile = f;
            fileLabel.setText(f.getName());
            Ui.fg(fileLabel, "INK");
            updateImportButtonState();
        }
    }

    private void onImport() {
        SubjectOption subject = (SubjectOption) subjectBox.getSelectedItem();
        if (subject == null || subject.id < 0) {
            showError("Please select a subject first.");
            return;
        }
        if (chosenFile == null || !chosenFile.exists()) {
            showError("Please select an Excel file first.");
            return;
        }

        ExcelImportService.ImportReport report;
        try {
            report = importService.validate(chosenFile, subject.id);
        } catch (IOException ex) {
            showError("Could not read the Excel file. Please make sure it is a valid .xlsx file.");
            return;
        } catch (SQLException ex) {
            showError("Could not check the question bank. Please check that MySQL is running.");
            return;
        } catch (Exception ex) {
            showError("Could not process the Excel file. Please check its format.");
            return;
        }

        if (!report.isHeaderValid()) {
            JTextArea area = new JTextArea(report.summaryText(), 12, 60);
            area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area),
                    "Import Validation", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (report.getTotalRows() == 0) {
            showError("The Excel file contains no data rows.");
            return;
        }

        // Show validation summary and ask for confirmation. Never insert invalid rows.
        JTextArea summary = new JTextArea(report.summaryText(), 16, 64);
        summary.setEditable(false);
        JScrollPane scroll = new JScrollPane(summary);

        if (report.allValid()) {
            int choice = JOptionPane.showConfirmDialog(this, scroll,
                    "Confirm Import", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.OK_OPTION) {
                int imported = importService.importValidRows(report);
                showImportResult(imported, report.getValidCount(), report);
            }
            // Cancel -> nothing inserted
        } else if (report.anyValid()) {
            Object[] options = {"Import Valid Rows Only", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this, scroll,
                    "Question Bank Import - Invalid Rows Found",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[1]);
            if (choice == 0) {
                int imported = importService.importValidRows(report);
                showImportResult(imported, report.getValidCount(), report);
            }
            // Cancel -> nothing inserted
        } else {
            JOptionPane.showMessageDialog(this, scroll,
                    "Question Bank Import - No Valid Rows",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showImportResult(int imported, int valid,
            ExcelImportService.ImportReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Import complete.\n\n");
        sb.append("Imported: ").append(imported).append('\n');
        if (imported < valid) {
            sb.append("(some rows could not be inserted)\n");
            if (!report.getInsertFailures().isEmpty()) {
                sb.append('\n');
                for (String failure : report.getInsertFailures()) {
                    sb.append(failure).append('\n');
                }
            }
        }
        JTextArea area = new JTextArea(sb.toString(), 8, 44);
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Import Result",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Matches the chosen file's texts against the selected subject's bank,
     * previews the matches and deletes them after confirmation. Questions
     * pinned by finalized papers are kept.
     */
    private void onDeleteFromFile() {
        SubjectOption subject = (SubjectOption) subjectBox.getSelectedItem();
        if (subject == null || subject.id < 0) {
            showError("Please select a subject first.");
            return;
        }
        if (chosenFile == null || !chosenFile.exists()) {
            showError("Please select an Excel file first.");
            return;
        }
        ExcelImportService.MatchReport match;
        try {
            match = importService.matchExistingByFile(chosenFile, subject.id);
        } catch (IOException ex) {
            showError("Could not read the Excel file. Please make sure it is a valid .xlsx file.");
            return;
        } catch (SQLException ex) {
            showError("Could not check the question bank. Please check that MySQL is running.");
            return;
        }
        if (!match.isHeaderValid()) {
            showError("Cannot match questions: " + match.getHeaderError());
            return;
        }
        if (match.getMatched().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No questions in this file match the selected subject's question bank.",
                    "Delete from File", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            QuestionService.BulkDeletePlan plan =
                    questionService.previewDelete(match.getMatched());
            String scope = "File: " + chosenFile.getName()
                    + "  |  Subject: " + subject.name;
            if (!QuestionDeleteConfirm.confirmDeletion(this, scope, plan, questionService)) {
                return;
            }
            QuestionService.BulkDeleteResult result =
                    questionService.deleteQuestions(match.getMatched());
            QuestionDeleteConfirm.showResult(this, result);
        } catch (SQLException ex) {
            showError("Could not delete the questions. Please check that MySQL is running.");
        }
    }

    /**
     * Deletes every question of the selected subject (preview first,
     * finalized papers untouched).
     */
    private void onClearSubject() {
        SubjectOption subject = (SubjectOption) subjectBox.getSelectedItem();
        if (subject == null || subject.id < 0) {
            showError("Please select a subject first.");
            return;
        }
        try {
            List<Integer> ids = new ArrayList<>();
            for (Question q : questionService.searchQuestions(
                    subject.id, null, null, null, null)) {
                ids.add(q.getQuestionId());
            }
            if (ids.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "The selected subject has no questions to delete.",
                        "Clear Entire Subject", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            QuestionService.BulkDeletePlan plan = questionService.previewDelete(ids);
            String scope = "Subject: " + subject.name;
            if (!QuestionDeleteConfirm.confirmDeletion(this, scope, plan, questionService)) {
                return;
            }
            QuestionService.BulkDeleteResult result =
                    questionService.deleteAllForSubject(subject.id);
            QuestionDeleteConfirm.showResult(this, result);
        } catch (SQLException ex) {
            showError("Could not delete the questions. Please check that MySQL is running.");
        }
    }

    private void onDownloadTemplate() {        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("question_bank_template.xlsx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel files (*.xlsx)", "xlsx"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".xlsx")) {
            f = new File(f.getAbsolutePath() + ".xlsx");
        }
        if (f.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "Overwrite existing file?", "Confirm",
                    JOptionPane.YES_NO_OPTION);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            importService.saveTemplate(f);
            JOptionPane.showMessageDialog(this,
                    "Template saved to:\n" + f.getAbsolutePath(),
                    "Download Template", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError("Could not save the template. Please make sure the selected folder is writable.");
        }
    }

    private void loadSubjects() {
        try {
            subjectBox.removeAllItems();
            subjectBox.addItem(new SubjectOption(-1, "-- Select Subject --"));
            for (Subject s : questionService.getAllSubjects()) {
                subjectBox.addItem(new SubjectOption(s.getSubjectId(), s.getSubjectName()));
            }
        } catch (SQLException ex) {
            showError("Could not load subjects.");
        }
        updateImportButtonState();
    }

    /**
     * Called by MainFrame when this panel becomes visible.
     */
    public void refreshPanel() {
        loadSubjects();
    }

    // =====================================================================
    // Public facade (used by MainFrame and the GUI smoke test)
    // =====================================================================

    /** True while a real subject and an existing Excel file are chosen. */
    public boolean isImportEnabled() {
        return importButton.isEnabled();
    }

    /**
     * True while a subject AND a chosen file can drive
     * "Delete Questions from This File".
     */
    public boolean isDeleteFromFileEnabled() {
        return deleteFromFileButton.isEnabled();
    }

    /** True while a concrete subject can drive "Clear Entire Subject". */
    public boolean isClearSubjectEnabled() {
        return clearSubjectButton.isEnabled();
    }

    /**
     * Selects the subject with the given id in the combo box; unknown ids
     * fall back to the "-- Select Subject --" placeholder.
     */
    public void selectImportSubject(int subjectId) {
        int index = 0;
        for (int i = 0; i < subjectBox.getItemCount(); i++) {
            if (subjectBox.getItemAt(i).id == subjectId) {
                index = i;
                break;
            }
        }
        if (subjectBox.getItemCount() > 0) {
            subjectBox.setSelectedIndex(index);
        }
        updateImportButtonState();
    }

    private void showError(String message) {
        if (initializing) {
            System.err.println("[startup] " + message);
            return;
        }
        JOptionPane.showMessageDialog(this, message, "Import questions",
                JOptionPane.ERROR_MESSAGE);
    }

    private static class SubjectOption {
        final int id;
        final String name;

        SubjectOption(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
