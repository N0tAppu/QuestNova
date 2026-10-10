package ui;

import model.PaperReview;
import model.Question;
import model.ReviewQuestion;
import service.PDFExportService;
import service.PaperReviewService;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Generated Paper Review, Editing & Finalization screen (Stage 9) plus the
 * PDF export actions (Stage 10).
 *
 * Two strictly separated operations:
 *  - "Edit for This Paper", "Replace Question" and "Regenerate Question"
 *    change ONLY this in-memory review, until it is finalized.
 *  - "Update Question Bank" is the single explicit action that writes a
 *    change into the question bank.
 *
 * The status label is always visible. Finalization re-validates through the
 * service and saves atomically; afterwards every control is read-only
 * except the two PDF export buttons, which are enabled only for a
 * finalized paper and write the stored snapshot through PDFExportService.
 */
public class ReviewPaperPanel extends JPanel {

    private final PaperReviewService reviewService = new PaperReviewService();
    private final PDFExportService exportService = new PDFExportService();
    private PaperReview review;
    private Runnable closeAction = () -> {
    };

    private final JLabel titleLabel = new JLabel("Review Generated Paper");
    private final JLabel headerLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Q#", "Module", "Marks", "Difficulty", "Question"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    private final JLabel detailInfoLabel = new JLabel("Select a question to review it.");
    private final JTextArea questionArea = new JTextArea(4, 70);
    private final JTextArea answerArea = new JTextArea(4, 70);

    private final JButton editButton = Ui.plain("Edit for This Paper");
    private final JButton replaceButton = Ui.plain("Replace Question");
    private final JButton regenerateButton = Ui.plain("Regenerate Question");
    private final JButton updateBankButton = Ui.danger("Update Question Bank");
    private final JButton finalizeButton = Ui.primary("Finalize Paper");
    private final JButton exportPaperButton = Ui.plain("Export Question Paper PDF");
    private final JButton exportKeyButton = Ui.plain("Export Answer Key PDF");
    private final JButton closeButton = Ui.plain("Close");

    public ReviewPaperPanel() {
        setLayout(new BorderLayout(Theme.S, Theme.S));
        Ui.pad(this);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        titleLabel.putClientProperty("FlatLaf.style", "font:+7 bold");
        titleLabel.setForeground(Theme.INK);
        statusLabel.setBorder(new EmptyBorder(0, Theme.M, 0, Theme.M));
        header.add(titleLabel, BorderLayout.WEST);
        header.add(statusLabel, BorderLayout.EAST);
        JPanel headerBlock = new JPanel(new BorderLayout());
        headerBlock.setOpaque(false);
        headerBlock.add(header, BorderLayout.NORTH);
        headerLabel.setBorder(new EmptyBorder(Theme.XS, 0, 0, 0));
        headerBlock.add(headerLabel, BorderLayout.SOUTH);
        add(headerBlock, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Ui.styleTable(table);
        table.getAccessibleContext().setAccessibleName("Questions in this paper");
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setPreferredWidth(420);
        table.getSelectionModel().addListSelectionListener(e -> {
            showDetails();
            updateButtonStates();
        });

        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        detailInfoLabel.setForeground(Theme.MUTED);

        JPanel detail = new JPanel(new GridBagLayout());
        detail.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(Theme.XS, Theme.XS, Theme.XS, Theme.XS);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        detail.add(new JLabel("Question:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        detail.add(new JScrollPane(questionArea), c);
        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        detail.add(new JLabel("Answer:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        detail.add(new JScrollPane(answerArea), c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        detail.add(detailInfoLabel, c);

        JPanel south = new JPanel(new BorderLayout(Theme.XS, Theme.XS));
        south.setOpaque(false);
        south.add(detail, BorderLayout.CENTER);

        // Two explicit rows: wrapping in a single FlowLayout inside
        // BorderLayout.SOUTH would clip the second row (the container keeps
        // the preferred height of a single row).
        editButton.addActionListener(e -> onEdit());
        replaceButton.addActionListener(e -> onReplace());
        regenerateButton.addActionListener(e -> onRegenerate());
        updateBankButton.addActionListener(e -> onUpdateBank());
        finalizeButton.addActionListener(e -> onFinalize());
        exportPaperButton.addActionListener(e -> onExport(false));
        exportKeyButton.addActionListener(e -> onExport(true));
        closeButton.addActionListener(e -> closeAction.run());
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeReview");
        getActionMap().put("closeReview", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeAction.run();
            }
        });

        JPanel editRow = Ui.leftRow(editButton, replaceButton, regenerateButton,
                updateBankButton, finalizeButton);
        JPanel exportRow = Ui.leftRow(exportPaperButton, exportKeyButton, closeButton);
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, Theme.XS));
        buttons.setOpaque(false);
        buttons.add(editRow);
        buttons.add(exportRow);
        south.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                Ui.table(table), south);
        split.setResizeWeight(0.55);
        split.setDividerSize(6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        updateButtonStates();
    }

    // =====================================================================
    // Binding
    // =====================================================================

    /** Binds a generated paper to this screen and refreshes everything. */
    public void setReview(PaperReview review) {
        this.review = review;
        refreshAll();
    }

    /** The dialog closes the screen through this callback. */
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction == null ? () -> {
        } : closeAction;
    }

    // =====================================================================
    // Public facade (used by the button handlers and the GUI smoke test)
    // =====================================================================

    public int getRowCount() {
        return tableModel.getRowCount();
    }

    /** Sequence number of the selected row, or -1 when nothing is selected. */
    public int getSelectedSequenceNo() {
        int row = table.getSelectedRow();
        if (row < 0 || review == null || row >= review.getQuestions().size()) {
            return -1;
        }
        return review.getQuestions().get(row).getSequenceNo();
    }

    /** Selects the row of a blueprint position. */
    public void selectQuestion(int sequenceNo) {
        if (review == null) {
            return;
        }
        for (int i = 0; i < review.getQuestions().size(); i++) {
            if (review.getQuestions().get(i).getSequenceNo() == sequenceNo) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    public String getStatusText() {
        return statusLabel.getText();
    }

    public List<String> getValidationErrors() {
        return reviewService.validate(review);
    }

    public boolean isFinalized() {
        return review != null && review.isFinalized();
    }

    public int getPaperId() {
        return review == null ? -1 : review.getPaperId();
    }

    /** Edit for This Paper: in-memory only, the question bank is untouched. */
    public void editSelected(String questionText, String answerText) {
        reviewService.editQuestionText(review, requireSequence(), questionText, answerText);
        refreshAll();
        selectQuestion(requireSequence());
    }

    /** Eligible unused bank questions for the selected requirement. */
    public List<Question> getReplacementCandidates() throws SQLException {
        int sequenceNo = requireSequence();
        return reviewService.getReplacementCandidates(review, sequenceNo);
    }

    /** Swaps the selected question for an exact-eligible unused one. */
    public void replaceSelected(int questionId) throws SQLException {
        int sequenceNo = requireSequence();
        reviewService.replaceQuestion(review, sequenceNo, questionId);
        refreshAll();
        selectQuestion(sequenceNo);
    }

    /** Draws another unused question for the selected requirement. */
    public void regenerateSelected() throws SQLException {
        int sequenceNo = requireSequence();
        reviewService.regenerateQuestion(review, sequenceNo);
        refreshAll();
        selectQuestion(sequenceNo);
    }

    /** Explicitly writes the selected question's text into the bank. */
    public void updateBankSelected() throws SQLException {
        reviewService.updateQuestionBank(review, requireSequence());
        refreshAll();
        selectQuestion(requireSequence());
    }

    /**
     * Validates from scratch and saves the paper atomically.
     *
     * @return the persisted paper id
     */
    public int finalizeCurrentPaper() throws SQLException {
        int paperId = reviewService.finalizePaper(review);
        refreshAll();
        return paperId;
    }

    // =====================================================================
    // Review action facade (Stage 11 - used by the GUI state smoke test)
    // =====================================================================

    /** True while the four review edit actions are selectable. */
    public boolean isReviewActionEnabled() {
        return editButton.isEnabled();
    }

    /** True while the paper may be finalized. */
    public boolean isFinalizeEnabled() {
        return finalizeButton.isEnabled();
    }

    // =====================================================================
    // PDF export facade (Stage 10 - used by the handlers and the GUI test)
    // =====================================================================

    public boolean isExportQuestionPaperEnabled() {
        return exportPaperButton.isEnabled();
    }

    public boolean isExportAnswerKeyEnabled() {
        return exportKeyButton.isEnabled();
    }

    /** The suggested save name for one of the two documents. */
    public String getSuggestedFileName(boolean answerKey) {
        return PDFExportService.buildDefaultFileName(getPaperId(),
                review == null ? "" : review.getSubjectName(), answerKey);
    }

    /**
     * Writes the question paper PDF of this finalized paper - the same
     * code path the button uses, without the file chooser.
     *
     * @throws IllegalStateException if the paper is not finalized yet
     */
    public File exportQuestionPaper(File target) throws SQLException, IOException {
        return exportService.exportQuestionPaper(requireFinalizedPaperId(), target);
    }

    /**
     * Writes the answer key PDF of this finalized paper - the same code
     * path the button uses, without the file chooser.
     *
     * @throws IllegalStateException if the paper is not finalized yet
     */
    public File exportAnswerKey(File target) throws SQLException, IOException {
        return exportService.exportAnswerKey(requireFinalizedPaperId(), target);
    }

    private int requireFinalizedPaperId() {
        if (!isFinalized()) {
            throw new IllegalStateException(
                    "Only a finalized paper can be exported as a PDF.");
        }
        return getPaperId();
    }

    // =====================================================================
    // Button handlers (dialogs and confirmations)
    // =====================================================================

    private void onEdit() {
        if (getSelectedSequenceNo() < 0) {
            showError("Please select a question first.");
            return;
        }
        int sequenceNo = getSelectedSequenceNo();
        ReviewQuestion current = review.getQuestionBySequence(sequenceNo);

        JTextArea questionInput = new JTextArea(current.getQuestionText(), 5, 64);
        questionInput.setLineWrap(true);
        questionInput.setWrapStyleWord(true);
        JTextArea answerInput = new JTextArea(current.getAnswerText(), 5, 64);
        answerInput.setLineWrap(true);
        answerInput.setWrapStyleWord(true);
        JPanel form = new JPanel(new BorderLayout(4, 4));
        form.add(new JLabel("Question (for this paper only):"), BorderLayout.NORTH);
        form.add(new JScrollPane(questionInput), BorderLayout.CENTER);
        form.add(new JLabel("Answer (for this paper only):"), BorderLayout.SOUTH);
        JPanel stacked = new JPanel(new BorderLayout(4, 4));
        stacked.add(form, BorderLayout.NORTH);
        stacked.add(new JScrollPane(answerInput), BorderLayout.CENTER);

        int choice = JOptionPane.showConfirmDialog(this, stacked,
                "Edit for This Paper - Question " + sequenceNo,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            reviewService.editQuestionText(review, sequenceNo,
                    questionInput.getText(), answerInput.getText());
            refreshAll();
            selectQuestion(sequenceNo);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onReplace() {
        if (getSelectedSequenceNo() < 0) {
            showError("Please select a question first.");
            return;
        }
        int sequenceNo = getSelectedSequenceNo();
        try {
            List<Question> candidates = reviewService.getReplacementCandidates(review, sequenceNo);
            if (candidates.isEmpty()) {
                showError("No other eligible question is available for this requirement.");
                return;
            }
            JComboBox<CandidateOption> box = new JComboBox<>();
            for (Question candidate : candidates) {
                box.addItem(new CandidateOption(candidate));
            }
            JPanel form = new JPanel(new BorderLayout(4, 4));
            form.add(new JLabel("Replacement for question " + sequenceNo
                    + " (same module, marks and difficulty):"), BorderLayout.NORTH);
            form.add(box, BorderLayout.CENTER);
            int choice = JOptionPane.showConfirmDialog(this, form, "Replace Question",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            CandidateOption selected = (CandidateOption) box.getSelectedItem();
            if (selected == null) {
                return;
            }
            reviewService.replaceQuestion(review, sequenceNo, selected.questionId);
            refreshAll();
            selectQuestion(sequenceNo);
        } catch (SQLException ex) {
            showError("Could not read the question bank. Please check that MySQL is running.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onRegenerate() {
        if (getSelectedSequenceNo() < 0) {
            showError("Please select a question first.");
            return;
        }
        int sequenceNo = getSelectedSequenceNo();
        try {
            reviewService.regenerateQuestion(review, sequenceNo);
            refreshAll();
            selectQuestion(sequenceNo);
        } catch (SQLException ex) {
            showError("Could not read the question bank. Please check that MySQL is running.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onUpdateBank() {
        if (getSelectedSequenceNo() < 0) {
            showError("Please select a question first.");
            return;
        }
        int sequenceNo = getSelectedSequenceNo();
        int choice = JOptionPane.showConfirmDialog(this,
                "Write the current text and answer of question " + sequenceNo
                        + " into the question bank?\n"
                        + "This changes the bank - papers already finalized keep "
                        + "their stored text.",
                "Update Question Bank", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            reviewService.updateQuestionBank(review, sequenceNo);
            refreshAll();
            selectQuestion(sequenceNo);
            JOptionPane.showMessageDialog(this,
                    "The question bank has been updated.",
                    "Update Question Bank", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showError("Could not update the question bank. Please check that MySQL is running.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onFinalize() {
        List<String> errors = reviewService.validate(review);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("The paper cannot be finalized yet:\n");
            for (String error : errors) {
                sb.append("\n- ").append(error);
            }
            showError(sb.toString());
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Finalize this paper?\n\nIt will be saved permanently and becomes "
                        + "read-only. The question bank stays unchanged.",
                "Finalize Paper", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            int paperId = reviewService.finalizePaper(review);
            refreshAll();
            JOptionPane.showMessageDialog(this,
                    "Paper finalized and saved.\nPaper ID: " + paperId,
                    "Finalize Paper", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showError("Could not save the paper. Please check that MySQL is running.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    // =====================================================================
    // Export handlers: file chooser, overwrite confirmation, error dialogs
    // =====================================================================

    private void onExport(boolean answerKey) {
        if (!isFinalized()) {
            showError("Only a finalized paper can be exported as a PDF.");
            return;
        }
        String documentName = answerKey ? "Answer Key" : "Question Paper";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export " + documentName + " PDF");
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(),
                getSuggestedFileName(answerKey)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = withPdfExtension(chooser.getSelectedFile());
        if (target == null) {
            return;
        }
        if (target.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "The file \"" + target.getName() + "\" already exists.\n"
                            + "Replace it?",
                    "Export " + documentName + " PDF",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            File written = answerKey
                    ? exportService.exportAnswerKey(getPaperId(), target)
                    : exportService.exportQuestionPaper(getPaperId(), target);
            JOptionPane.showMessageDialog(this,
                    documentName + " saved as:\n" + written.getAbsolutePath(),
                    "Export " + documentName + " PDF",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showError("Could not read the finalized paper. Please check that MySQL is running.");
        } catch (IOException ex) {
            showError("Could not write the PDF. Please make sure the selected folder is writable.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private static File withPdfExtension(File file) {
        if (file == null || file.getName().toLowerCase().endsWith(".pdf")) {
            return file;
        }
        File parent = file.getParentFile();
        return parent == null
                ? new File(file.getName() + ".pdf")
                : new File(parent, file.getName() + ".pdf");
    }

    // =====================================================================
    // Refresh
    // =====================================================================

    private void refreshAll() {
        rebuildTable();
        showDetails();
        refreshHeader();
        refreshStatus();
        updateButtonStates();
    }

    private void rebuildTable() {
        int selected = table.getSelectedRow();
        tableModel.setRowCount(0);
        if (review == null) {
            return;
        }
        for (ReviewQuestion question : review.getQuestions()) {
            tableModel.addRow(new Object[]{question.getSequenceNo(), question.getModuleName(),
                    question.getMarks(), question.getDifficulty(), question.getQuestionText()});
        }
        if (selected >= 0 && selected < tableModel.getRowCount()) {
            table.setRowSelectionInterval(selected, selected);
        } else if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void showDetails() {
        ReviewQuestion question = selectedQuestion();
        if (question == null) {
            detailInfoLabel.setText("Select a question to review it.");
            questionArea.setText("");
            answerArea.setText("");
            questionArea.setEditable(false);
            answerArea.setEditable(false);
            return;
        }
        questionArea.setText(question.getQuestionText());
        answerArea.setText(question.getAnswerText());
        questionArea.setCaretPosition(0);
        answerArea.setCaretPosition(0);
        detailInfoLabel.setText("Sequence " + question.getSequenceNo()
                + "  |  Question id in bank: " + question.getQuestionId()
                + "  |  Module: " + question.getModuleName()
                + "  |  " + question.getMarks() + " marks"
                + "  |  " + question.getDifficulty());
        boolean editable = !isFinalized();
        questionArea.setEditable(editable);
        answerArea.setEditable(editable);
    }

    private void refreshHeader() {
        if (review == null) {
            headerLabel.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder("<html><b>").append(review.getSubjectName())
                .append("</b>");
        if (!review.getExamType().isEmpty()) {
            sb.append(" &nbsp;-&nbsp; ").append(review.getExamType());
        }
        sb.append("<br/>Questions: ").append(review.getQuestions().size())
                .append(" &nbsp;|&nbsp; Total marks: ").append(review.getTotalMarks());
        sb.append("</html>");
        headerLabel.setText(sb.toString());
    }

    private void refreshStatus() {
        if (review == null) {
            statusLabel.setText("");
            statusLabel.setToolTipText(null);
            return;
        }
        if (review.isFinalized()) {
            statusLabel.setText("FINALIZED - Paper ID " + review.getPaperId()
                    + " (read-only)");
            statusLabel.setForeground(Theme.MUTED);
            statusLabel.setToolTipText("This paper is saved and can no longer change.");
            return;
        }
        List<String> errors = reviewService.validate(review);
        if (errors.isEmpty()) {
            statusLabel.setText("VALID - ready to finalize");
            statusLabel.setForeground(Theme.OK);
            statusLabel.setToolTipText(null);
        } else {
            statusLabel.setText("INVALID - " + errors.size() + " problem(s)");
            statusLabel.setForeground(Theme.BAD);
            StringBuilder tip = new StringBuilder();
            for (String error : errors) {
                tip.append("- ").append(error).append('\n');
            }
            statusLabel.setToolTipText(tip.toString().trim());
        }
    }

    private void updateButtonStates() {
        boolean bound = review != null;
        boolean open = bound && !review.isFinalized();
        boolean finalized = bound && review.isFinalized();
        boolean editEnabled = open && getSelectedSequenceNo() >= 0;
        editButton.setEnabled(editEnabled);
        replaceButton.setEnabled(editEnabled);
        regenerateButton.setEnabled(editEnabled);
        updateBankButton.setEnabled(editEnabled);
        finalizeButton.setEnabled(open && reviewService.validate(review).isEmpty());
        exportPaperButton.setEnabled(finalized);
        exportKeyButton.setEnabled(finalized);
        closeButton.setEnabled(true);
        if (!bound) {
            questionArea.setEditable(false);
            answerArea.setEditable(false);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private ReviewQuestion selectedQuestion() {
        if (review == null) {
            return null;
        }
        int row = table.getSelectedRow();
        if (row < 0 || row >= review.getQuestions().size()) {
            return null;
        }
        return review.getQuestions().get(row);
    }

    private int requireSequence() {
        int sequenceNo = getSelectedSequenceNo();
        if (sequenceNo < 0) {
            throw new IllegalArgumentException("Please select a question first.");
        }
        return sequenceNo;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Review Generated Paper",
                JOptionPane.ERROR_MESSAGE);
    }

    private static class CandidateOption {

        private final int questionId;
        private final Question question;

        CandidateOption(Question question) {
            this.question = question;
            this.questionId = question.getQuestionId();
        }

        @Override
        public String toString() {
            return "[" + questionId + "] " + question.getQuestionText();
        }
    }
}
