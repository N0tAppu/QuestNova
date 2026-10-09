package ui;

import model.Question;
import model.Subject;
import service.ExcelImportService;
import service.QuestionService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Import Excel Question Bank screen.
 * Flow: select subject -> select .xlsx -> validate entire file ->
 * show summary -> Import All / Import Valid Rows Only / Cancel.
 * All SQL and business logic live in the service layer.
 */
public class ImportPanel extends JPanel {

    private final ExcelImportService importService = new ExcelImportService();
    private final QuestionService questionService = new QuestionService();

    private final JComboBox<SubjectOption> subjectBox = new JComboBox<>();
    private final JLabel fileLabel = new JLabel("No file selected");
    private final JButton importButton = new JButton("Import Question Bank");
    private final JButton deleteFromFileButton =
            new JButton("Delete Questions from This File");
    private final JButton clearSubjectButton = new JButton("Clear Entire Subject");

    /** True while the constructor runs: startup errors must not open dialogs. */
    private boolean initializing = true;

    public ImportPanel() {
        setLayout(new BorderLayout(UiTheme.GAP, UiTheme.GAP));
        setBorder(BorderFactory.createEmptyBorder(
                UiTheme.PAD, UiTheme.PAD, UiTheme.PAD, UiTheme.PAD));

        JPanel titleLabel = UiTheme.titleStrip("Import Excel Question Bank");

        JPanel card = new JPanel(new BorderLayout());
        UiTheme.styleCard(card, "Import Setup");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Subject:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        subjectBox.addActionListener(e -> updateImportButtonState());
        form.add(subjectBox, c);

        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(new JLabel("Excel File:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(fileLabel, c);

        c.gridx = 1; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);
        JButton chooseButton = new JButton("Select Excel File (.xlsx)");
        JButton templateButton = new JButton("Download Excel Template");
        chooseButton.addActionListener(e -> onChooseFile());
        importButton.addActionListener(e -> onImport());
        templateButton.addActionListener(e -> onDownloadTemplate());
        deleteFromFileButton.addActionListener(e -> onDeleteFromFile());
        clearSubjectButton.addActionListener(e -> onClearSubject());
        UiTheme.primaryButton(importButton);
        UiTheme.neutralButton(chooseButton);
        UiTheme.neutralButton(templateButton);
        UiTheme.neutralButton(deleteFromFileButton);
        UiTheme.neutralButton(clearSubjectButton);
        buttons.add(chooseButton);
        buttons.add(importButton);
        buttons.add(templateButton);
        buttons.add(deleteFromFileButton);
        buttons.add(clearSubjectButton);
        form.add(buttons, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        JTextArea info = new JTextArea(
                "Expected columns: Question | Answer | Module | Marks | Difficulty\n"
                + "Subject is NOT part of the file - it is selected above.\n"
                + "The whole file is validated first. Nothing is inserted until you confirm.");
        info.setEditable(false);
        info.setOpaque(false);
        info.setForeground(UiTheme.MUTED);
        form.add(info, c);

        card.add(form, BorderLayout.CENTER);
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(titleLabel);
        top.add(Box.createVerticalStrut(UiTheme.GAP));
        top.add(card);
        add(top, BorderLayout.NORTH);

        try {
            loadSubjects();
            updateImportButtonState();
        } finally {
            initializing = false;
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
        JOptionPane.showMessageDialog(this, message, "Import Excel Question Bank",
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
