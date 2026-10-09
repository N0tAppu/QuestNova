package ui;

import model.Module;
import model.Question;
import model.Subject;
import service.QuestionService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.List;

/**
 * Question Bank screen: add / edit / delete questions, search and filter
 * by subject, module, marks and difficulty.
 * All business logic is delegated to QuestionService.
 */
public class QuestionPanel extends JPanel {

    private final QuestionService questionService = new QuestionService();

    private final JComboBox<SubjectOption> subjectFilter = new JComboBox<>();
    private final JComboBox<ModuleOption> moduleFilter = new JComboBox<>();
    private final JComboBox<String> difficultyFilter = new JComboBox<>();
    private final JTextField marksFilter = new JTextField(6);
    private final JTextField searchText = new JTextField(14);

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "Question", "Answer", "Subject", "Module", "Marks", "Difficulty"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    private final JButton editButton = new JButton("Edit Question");
    private final JButton deleteButton = new JButton("Delete Question");
    private final JButton viewAnswerButton = new JButton("View Answer");
    private final JButton clearAllButton = new JButton("Clear All for Subject");
    private final JLabel emptyLabel = new JLabel("No questions found.");

    /** True while the constructor runs: startup errors must not open dialogs. */
    private boolean initializing = true;

    public QuestionPanel() {
        setLayout(new BorderLayout(UiTheme.GAP, UiTheme.GAP));
        setBorder(BorderFactory.createEmptyBorder(
                UiTheme.PAD, UiTheme.PAD, UiTheme.PAD, UiTheme.PAD));

        JPanel north = new JPanel(new BorderLayout());
        north.add(UiTheme.titleStrip("Question Bank"), BorderLayout.NORTH);
        north.add(buildFilterBar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UiTheme.styleTable(table);
        table.getAccessibleContext().setAccessibleName("Questions");
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(55);
        table.getColumnModel().getColumn(6).setPreferredWidth(75);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateRowActionStates();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        add(buildActionButtons(), BorderLayout.SOUTH);
        updateRowActionStates();
        updateClearAllState();
        emptyLabel.setVisible(tableModel.getRowCount() == 0);

        searchText.addActionListener(e -> search());
        marksFilter.addActionListener(e -> search());
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control F"), "focusSearch");
        getActionMap().put("focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchText.requestFocusInWindow();
                searchText.selectAll();
            }
        });

        try {
            loadSubjectsIntoFilter();
            search();
        } finally {
            initializing = false;
        }
    }

    // --- Filter bar ---

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 2));
        UiTheme.styleCard(bar, "Search / Filter");

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.setOpaque(false);
        filters.add(new JLabel("Subject:"));
        subjectFilter.addActionListener(e -> onSubjectFilterChanged());
        filters.add(subjectFilter);

        filters.add(new JLabel("Module:"));
        filters.add(moduleFilter);

        filters.add(new JLabel("Marks:"));
        marksFilter.setToolTipText("Leave empty to ignore");
        filters.add(marksFilter);

        filters.add(new JLabel("Difficulty:"));
        difficultyFilter.addItem("");
        difficultyFilter.addItem(Question.DIFFICULTY_EASY);
        difficultyFilter.addItem(Question.DIFFICULTY_MEDIUM);
        difficultyFilter.addItem(Question.DIFFICULTY_HARD);
        filters.add(difficultyFilter);

        filters.add(new JLabel("Search:"));
        searchText.setToolTipText("Search inside question text");
        filters.add(searchText);

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> search());
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> resetFilters());
        UiTheme.neutralButton(searchButton);
        UiTheme.neutralButton(resetButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        actions.add(searchButton);
        actions.add(resetButton);

        bar.add(filters, BorderLayout.CENTER);
        bar.add(actions, BorderLayout.SOUTH);
        return bar;
    }

    // --- Action buttons ---

    private JPanel buildActionButtons() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Question");

        addButton.addActionListener(e -> onAdd());
        editButton.addActionListener(e -> onEdit());
        deleteButton.addActionListener(e -> onDelete());
        viewAnswerButton.addActionListener(e -> onViewAnswer());
        clearAllButton.addActionListener(e -> onClearAll());

        UiTheme.primaryButton(addButton);
        UiTheme.neutralButton(editButton);
        UiTheme.neutralButton(deleteButton);
        UiTheme.neutralButton(viewAnswerButton);
        UiTheme.neutralButton(clearAllButton);
        emptyLabel.setForeground(UiTheme.MUTED);

        bar.add(emptyLabel);
        bar.add(addButton);
        bar.add(editButton);
        bar.add(deleteButton);
        bar.add(viewAnswerButton);
        bar.add(clearAllButton);
        return bar;
    }

    /** Row actions are only meaningful while a table row is selected. */
    private void updateRowActionStates() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        viewAnswerButton.setEnabled(hasSelection);
    }

    /** Clear-all is subject-scoped: never enabled on "(All Subjects)". */
    private void updateClearAllState() {
        SubjectOption s = (SubjectOption) subjectFilter.getSelectedItem();
        clearAllButton.setEnabled(s != null && s.id >= 0);
    }

    // --- CRUD actions ---

    private void onAdd() {
        QuestionForm form = showQuestionForm(null);
        if (form == null) {
            return;
        }
        try {
            questionService.addQuestion(form.toQuestion());
            search();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not add the question. Please check that MySQL is running.");
        }
    }

    private void onEdit() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a question to edit.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        try {
            Question current = questionService.getQuestionById(id);
            if (current == null) {
                showError("The selected question no longer exists.");
                search();
                return;
            }
            QuestionForm form = showQuestionForm(current);
            if (form == null) {
                return;
            }
            Question updated = form.toQuestion();
            updated.setQuestionId(id);
            questionService.updateQuestion(updated);
            search();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not update the question.");
        }
    }

    private void onDelete() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a question to delete.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete the selected question?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            questionService.deleteQuestion(id);
            search();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not delete the question.");
        }
    }

    private void onViewAnswer() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a question to view its answer.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        try {
            Question q = questionService.getQuestionById(id);
            if (q == null) {
                showError("The selected question no longer exists.");
                search();
                return;
            }
            JTextArea area = new JTextArea(q.getAnswerText(), 8, 50);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JScrollPane scroll = new JScrollPane(area);
            JOptionPane.showMessageDialog(this, scroll, "Answer",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showError("Could not load the answer.");
        }
    }

    /** Preview -> confirm -> delete every question of the selected subject. */
    private void onClearAll() {
        SubjectOption s = (SubjectOption) subjectFilter.getSelectedItem();
        if (s == null || s.id < 0) {
            showError("Select a subject first.");
            return;
        }
        try {
            List<Integer> ids = new java.util.ArrayList<>();
            for (Question q : questionService.searchQuestions(
                    s.id, null, null, null, null)) {
                ids.add(q.getQuestionId());
            }
            if (ids.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "The selected subject has no questions to delete.",
                        "Clear All for Subject", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            QuestionService.BulkDeletePlan plan = questionService.previewDelete(ids);
            if (!QuestionDeleteConfirm.confirmDeletion(this,
                    "Subject: " + s.name, plan, questionService)) {
                return;
            }
            QuestionService.BulkDeleteResult result =
                    questionService.deleteAllForSubject(s.id);
            QuestionDeleteConfirm.showResult(this, result);
            search();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not delete the questions. Please check that MySQL is running.");
        }
    }

    // --- Search / filter ---

    private void search() {
        Integer keepQuestionId = null;
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            keepQuestionId = (Integer) tableModel.getValueAt(selectedRow, 0);
        }
        Integer subjectId = null;
        Integer moduleId = null;
        Integer marks = null;
        String difficulty = (String) difficultyFilter.getSelectedItem();
        if (difficulty != null && difficulty.isEmpty()) {
            difficulty = null;
        }

        SubjectOption s = (SubjectOption) subjectFilter.getSelectedItem();
        if (s != null && s.id >= 0) {
            subjectId = s.id;
        }
        ModuleOption m = (ModuleOption) moduleFilter.getSelectedItem();
        if (m != null && m.id >= 0) {
            moduleId = m.id;
        }
        String marksText = marksFilter.getText().trim();
        if (!marksText.isEmpty()) {
            try {
                marks = Integer.parseInt(marksText);
                if (marks <= 0) {
                    showError("Marks filter must be a positive whole number.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showError("Marks filter must be a whole number.");
                return;
            }
        }

        try {
            List<Question> questions = questionService.searchQuestions(
                    subjectId, moduleId, marks, difficulty, searchText.getText());
            tableModel.setRowCount(0);
            for (Question q : questions) {
                tableModel.addRow(new Object[]{q.getQuestionId(), q.getQuestionText(),
                        q.getAnswerText(), q.getSubjectName(), q.getModuleName(),
                        q.getMarks(), q.getDifficulty()});
            }
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not load questions. Please check that MySQL is running.");
        }
        if (keepQuestionId != null) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (keepQuestionId.equals(tableModel.getValueAt(i, 0))) {
                    table.setRowSelectionInterval(i, i);
                    table.scrollRectToVisible(table.getCellRect(i, 0, true));
                    break;
                }
            }
        }
        // Refresh the row-action states for the surviving selection.
        updateRowActionStates();
        emptyLabel.setVisible(tableModel.getRowCount() == 0);
    }

    private void resetFilters() {
        subjectFilter.setSelectedIndex(0);
        difficultyFilter.setSelectedIndex(0);
        marksFilter.setText("");
        searchText.setText("");
        search();
    }

    private void loadSubjectsIntoFilter() {
        try {
            subjectFilter.removeAllItems();
            subjectFilter.addItem(new SubjectOption(-1, "(All Subjects)"));
            for (Subject sub : questionService.getAllSubjects()) {
                subjectFilter.addItem(new SubjectOption(sub.getSubjectId(), sub.getSubjectName()));
            }
            loadModulesIntoFilter(-1);
            updateClearAllState();
        } catch (SQLException ex) {
            showError("Could not load subjects.");
        }
    }

    private void onSubjectFilterChanged() {
        SubjectOption s = (SubjectOption) subjectFilter.getSelectedItem();
        loadModulesIntoFilter(s == null ? -1 : s.id);
        updateClearAllState();
        search();
    }

    private void loadModulesIntoFilter(int subjectId) {
        moduleFilter.removeAllItems();
        moduleFilter.addItem(new ModuleOption(-1, "(All Modules)"));
        if (subjectId < 0) {
            return;
        }
        try {
            for (Module m : questionService.getModulesBySubject(subjectId)) {
                moduleFilter.addItem(new ModuleOption(m.getModuleId(), m.getModuleName()));
            }
        } catch (SQLException ex) {
            showError("Could not load modules.");
        }
    }

    /**
     * Called by MainFrame when this panel becomes visible so the subject
     * list stays in sync with Subjects & Modules management.
     */
    public void refreshFilters() {
        loadSubjectsIntoFilter();
        search();
    }

    // =====================================================================
    // Public facade (used by the handlers and the GUI smoke test)
    // =====================================================================

    /** True while a question row is selected. */
    public boolean isEditEnabled() {
        return editButton.isEnabled();
    }

    /** True while a question row is selected. */
    public boolean isDeleteEnabled() {
        return deleteButton.isEnabled();
    }

    /** True while a question row is selected. */
    public boolean isViewAnswerEnabled() {
        return viewAnswerButton.isEnabled();
    }

    public int getQuestionRowCount() {
        return tableModel.getRowCount();
    }

    /**
     * True while a concrete subject is selected in the filter - the
     * "Clear All for Subject" action never runs against "(All Subjects)".
     */
    public boolean isClearAllEnabled() {
        return clearAllButton.isEnabled();
    }

    /**
     * Selects the subject filter by id (negative ids select "(All Subjects)").
     * Unknown ids fall back to "(All Subjects)".
     */
    public void selectQuestionSubject(int subjectId) {
        int index = 0;
        for (int i = 0; i < subjectFilter.getItemCount(); i++) {
            if (subjectFilter.getItemAt(i).id == subjectId) {
                index = i;
                break;
            }
        }
        if (subjectFilter.getItemCount() > 0) {
            subjectFilter.setSelectedIndex(index);
        }
        updateClearAllState();
    }

    /**
     * Selects a question row (or clears the selection when row &lt; 0).
     * Rows outside the table are ignored.
     */
    public void selectQuestionRow(int row) {
        if (row >= 0 && row < tableModel.getRowCount()) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        } else if (row < 0) {
            table.clearSelection();
        }
    }

    // --- Question add/edit form ---

    private QuestionForm showQuestionForm(Question existing) {
        JTextArea questionArea = new JTextArea(4, 40);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        JTextArea answerArea = new JTextArea(4, 40);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        if (existing != null) {
            questionArea.setText(existing.getQuestionText());
            answerArea.setText(existing.getAnswerText());
        }

        JComboBox<SubjectOption> subjectBox = new JComboBox<>();
        try {
            for (Subject sub : questionService.getAllSubjects()) {
                subjectBox.addItem(new SubjectOption(sub.getSubjectId(), sub.getSubjectName()));
            }
        } catch (SQLException ex) {
            showError("Could not load subjects.");
            return null;
        }
        JComboBox<ModuleOption> moduleBox = new JComboBox<>();
        JTextField marksField = new JTextField(5);
        JComboBox<String> difficultyBox = new JComboBox<>(
                QuestionService.getValidDifficulties().toArray(new String[0]));

        if (existing != null) {
            selectSubject(subjectBox, existing.getSubjectId());
            loadFormModules(moduleBox, existing.getSubjectId());
            selectModule(moduleBox, existing.getModuleId());
            marksField.setText(String.valueOf(existing.getMarks()));
            difficultyBox.setSelectedItem(existing.getDifficulty());
        }
        subjectBox.addActionListener(e -> {
            SubjectOption opt = (SubjectOption) subjectBox.getSelectedItem();
            loadFormModules(moduleBox, opt == null ? -1 : opt.id);
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        c.gridx = 0; c.gridy = row;
        form.add(new JLabel("Question:"), c);
        c.gridx = 1; c.weightx = 1.0;
        form.add(new JScrollPane(questionArea), c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Answer:"), c);
        c.gridx = 1; c.weightx = 1.0;
        form.add(new JScrollPane(answerArea), c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel("Subject:"), c);
        c.gridx = 1;
        form.add(subjectBox, c);

        row++;
        c.gridx = 0; c.gridy = row;
        form.add(new JLabel("Module:"), c);
        c.gridx = 1;
        form.add(moduleBox, c);

        row++;
        c.gridx = 0; c.gridy = row;
        form.add(new JLabel("Marks:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        form.add(marksField, c);

        row++;
        c.gridx = 0; c.gridy = row; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(new JLabel("Difficulty:"), c);
        c.gridx = 1;
        form.add(difficultyBox, c);

        int result = JOptionPane.showConfirmDialog(this, form,
                existing == null ? "Add Question" : "Edit Question",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        SubjectOption s = (SubjectOption) subjectBox.getSelectedItem();
        ModuleOption m = (ModuleOption) moduleBox.getSelectedItem();
        QuestionForm qf = new QuestionForm();
        qf.questionText = questionArea.getText();
        qf.answerText = answerArea.getText();
        qf.subjectId = s == null ? -1 : s.id;
        qf.moduleId = m == null ? -1 : m.id;
        qf.difficulty = (String) difficultyBox.getSelectedItem();
        try {
            qf.marks = Integer.parseInt(marksField.getText().trim());
        } catch (NumberFormatException ex) {
            qf.marks = -1;
        }
        return qf;
    }

    private void selectSubject(JComboBox<SubjectOption> box, int subjectId) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).id == subjectId) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectModule(JComboBox<ModuleOption> box, int moduleId) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).id == moduleId) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private void loadFormModules(JComboBox<ModuleOption> box, int subjectId) {
        box.removeAllItems();
        if (subjectId < 0) {
            return;
        }
        try {
            for (Module m : questionService.getModulesBySubject(subjectId)) {
                box.addItem(new ModuleOption(m.getModuleId(), m.getModuleName()));
            }
        } catch (SQLException ex) {
            showError("Could not load modules.");
        }
    }

    private void showError(String message) {
        if (initializing) {
            System.err.println("[startup] " + message);
            return;
        }
        JOptionPane.showMessageDialog(this, message, "Question Bank",
                JOptionPane.ERROR_MESSAGE);
    }

    // --- Small holder / option classes ---

    private static class QuestionForm {
        String questionText;
        String answerText;
        int subjectId;
        int moduleId;
        int marks;
        String difficulty;

        Question toQuestion() {
            Question q = new Question();
            q.setQuestionText(questionText);
            q.setAnswerText(answerText);
            q.setSubjectId(subjectId);
            q.setModuleId(moduleId);
            q.setMarks(marks);
            q.setDifficulty(difficulty);
            return q;
        }
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

    private static class ModuleOption {
        final int id;
        final String name;

        ModuleOption(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
