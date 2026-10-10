package ui;

import model.Module;
import model.Question;
import model.Subject;
import service.QuestionService;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Question Bank screen: master-detail with live filtering (v5 redesign).
 *
 * Left card: filter bar, "Showing X of Y questions" row and the question
 * table. Right card: the selected question with its answer, meta data and
 * the Edit / Delete actions. Typing in Search or Marks (or moving any
 * combo) refilters immediately - there is no Search button.
 * All business logic is delegated to {@link QuestionService}.
 */
public class QuestionPanel extends JPanel {

    private final QuestionService questionService = new QuestionService();

    /** Rows currently listed, in table order - the detail pane reads them. */
    private final List<Question> questions = new ArrayList<>();

    private final JComboBox<SubjectOption> subjectFilter = new JComboBox<>();
    private final JComboBox<ModuleOption> moduleFilter = new JComboBox<>();
    private final JComboBox<String> difficultyFilter = new JComboBox<>();
    private final JTextField marksFilter = new JTextField(6);
    private final JTextField searchText = new JTextField(14);
    private final JButton clearFiltersButton = Ui.link("Clear filters");

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "Question", "Module", "Marks", "Difficulty"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            // Integer columns right-align through Ui.table(...).
            return column == 0 || column == 3 ? Integer.class : String.class;
        }
    };
    private final JTable table = new JTable(tableModel);

    private final JButton clearAllButton = Ui.danger("Clear subject");
    private final JButton addButton = Ui.primary("Add question");
    private final JButton editButton = Ui.plain("Edit");
    private final JButton deleteButton = Ui.danger("Delete");

    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailPane = new JPanel(detailCards);
    private final JTextArea detailQuestion = prose(true);
    private final JTextArea detailAnswer = prose(false);
    private final JLabel detailMeta = Ui.muted("");
    private final JScrollPane detailScroll = new JScrollPane();

    private final JLabel countLabel = Ui.muted("");
    private final JLabel emptyLabel = Ui.muted("No questions found.");

    /** Size of the whole bank, i.e. the "of Y" in the count row. */
    private int totalQuestions = 0;

    /** True when the bank changed and the total above has to be reloaded. */
    private boolean totalStale = true;

    /** True while the module filter is rebuilt: no search per list change. */
    private boolean loadingModules = false;

    /** True while the constructor runs: startup errors must not open dialogs. */
    private boolean initializing = true;

    public QuestionPanel() {
        setLayout(new BorderLayout(0, Theme.L));
        setBackground(Theme.BG);

        addButton.addActionListener(e -> onAdd());
        clearAllButton.addActionListener(e -> onClearAll());
        editButton.addActionListener(e -> onEdit());
        deleteButton.addActionListener(e -> onDelete());
        add(Ui.pageHeader("Question bank",
                "Filter the list, select a question to read its answer.",
                clearAllButton, addButton), BorderLayout.NORTH);

        table.getAccessibleContext().setAccessibleName("Questions");
        JScrollPane listScroll = Ui.table(table);
        // Fixed column widths + horizontal scroll: the master card is narrow
        // next to the detail card, and proportional squeezing used to crush
        // one column to "..." at 1000px.
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setMinWidth(68);
        table.getColumnModel().getColumn(0).setPreferredWidth(68);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);
        table.getColumnModel().getColumn(2).setMinWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setMinWidth(68);
        table.getColumnModel().getColumn(3).setPreferredWidth(68);
        table.getColumnModel().getColumn(4).setMinWidth(96);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        Ui.difficultyColumn(table, 4);
        Ui.onSelect(table, modelRow -> {
            showDetail(modelRow);
            updateRowActionStates();
        });

        JPanel listCard = Ui.bareCard();
        listCard.add(buildListTop(), BorderLayout.NORTH);
        listCard.add(listScroll, BorderLayout.CENTER);

        detailPane.setOpaque(false);
        detailPane.add(buildDetail(), "detail");
        JLabel emptyDetail = Ui.muted("Select a question to read its answer.");
        emptyDetail.setHorizontalAlignment(SwingConstants.CENTER);
        detailPane.add(emptyDetail, "empty");
        JPanel detailCard = Ui.bareCard();
        detailCard.setPreferredSize(new Dimension(340, 0));
        detailCard.add(detailPane);

        JPanel body = new JPanel(new BorderLayout(Theme.L, 0));
        body.setOpaque(false);
        body.add(listCard, BorderLayout.CENTER);
        body.add(detailCard, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);

        updateCountLabel();
        updateRowActionStates();
        updateClearAllState();

        // Live filtering: Enter is optional, never required.
        searchText.addActionListener(e -> search());
        marksFilter.addActionListener(e -> search());
        DocumentListener liveFilter = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search();
            }
        };
        searchText.getDocument().addDocumentListener(liveFilter);
        marksFilter.getDocument().addDocumentListener(liveFilter);

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

    // --- Filter bar / count row ---

    /** Filter bar + "Showing X of Y questions" row that tops the list card. */
    private JPanel buildListTop() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(buildFilterBar(), BorderLayout.NORTH);

        JPanel countRow = new JPanel(new BorderLayout());
        countRow.setOpaque(false);
        countRow.setBorder(new EmptyBorder(Theme.XS, Theme.M, Theme.XS, Theme.M));
        countRow.add(countLabel, BorderLayout.WEST);
        countRow.add(emptyLabel, BorderLayout.EAST);

        JPanel sub = new JPanel(new BorderLayout());
        sub.setOpaque(false);
        sub.add(Ui.rule(), BorderLayout.NORTH);
        sub.add(countRow, BorderLayout.CENTER);
        sub.add(Ui.rule(), BorderLayout.SOUTH);
        top.add(sub, BorderLayout.SOUTH);
        return top;
    }

    /**
     * Search, Subject, Module, Marks and Difficulty filters, then the
     * "Clear filters" link. Wraps like the reference bar so nothing is cut
     * off on a 1000x640 window.
     */
    private JPanel buildFilterBar() {
        clearFiltersButton.addActionListener(e -> resetFilters());

        searchText.putClientProperty("JTextField.placeholderText", "Question or answer");
        searchText.putClientProperty("JTextField.showClearButton", true);
        marksFilter.setToolTipText("Leave empty to ignore");

        difficultyFilter.addItem("All difficulties");
        difficultyFilter.addItem(Question.DIFFICULTY_EASY);
        difficultyFilter.addItem(Question.DIFFICULTY_MEDIUM);
        difficultyFilter.addItem(Question.DIFFICULTY_HARD);

        subjectFilter.addActionListener(e -> onSubjectFilterChanged());
        moduleFilter.addActionListener(e -> {
            if (!loadingModules) {
                search();
            }
        });
        difficultyFilter.addActionListener(e -> search());

        JPanel filters = new JPanel(new WrappingRow());
        filters.setOpaque(false);
        filters.add(labeled("Search", searchText));
        filters.add(labeled("Subject", subjectFilter));
        filters.add(labeled("Module", moduleFilter));
        filters.add(labeled("Marks", marksFilter));
        filters.add(labeled("Difficulty", difficultyFilter));

        // The link is pinned to the trailing edge while the filters wrap
        // onto extra rows on a narrow window (the reference's flexible gap).
        JPanel bar = new JPanel(new BorderLayout(Theme.S, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(Theme.S, Theme.M, Theme.S, Theme.M));
        bar.add(filters, BorderLayout.CENTER);
        // Same stacked cell as the filters so the link lines up with them.
        bar.add(labeled("", clearFiltersButton), BorderLayout.EAST);
        return bar;
    }

    /** A small muted label stacked above its control. */
    private static JComponent labeled(String label, JComponent control) {
        JPanel cell = new JPanel(new BorderLayout(0, Theme.XS));
        cell.setOpaque(false);
        cell.add(Ui.muted(label), BorderLayout.NORTH);
        cell.add(control, BorderLayout.SOUTH);
        return cell;
    }

    private void updateCountLabel() {
        countLabel.setText("Showing " + tableModel.getRowCount()
                + " of " + totalQuestions + " questions");
        emptyLabel.setVisible(tableModel.getRowCount() == 0);
    }

    /** Row actions are only meaningful while a table row is selected. */
    private void updateRowActionStates() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    /** Clear-all is subject-scoped: never enabled on "(All Subjects)". */
    private void updateClearAllState() {
        SubjectOption s = (SubjectOption) subjectFilter.getSelectedItem();
        clearAllButton.setEnabled(s != null && s.id >= 0);
    }

    // --- Detail pane ---

    /** Master-detail: the selected question and its answer, or the empty state. */
    private JComponent buildDetail() {
        JPanel answer = new JPanel(new BorderLayout(0, Theme.XS)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.TINT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            Theme.CONTROL_ARC, Theme.CONTROL_ARC);
                } finally {
                    g2.dispose();
                }
            }
        };
        answer.setOpaque(false);
        answer.setBorder(new EmptyBorder(Theme.S, Theme.M, Theme.S, Theme.M));
        JLabel answerTitle = Ui.bold("Answer");
        answerTitle.setForeground(Theme.ACCENT_D);
        answer.add(answerTitle, BorderLayout.NORTH);
        answer.add(detailAnswer, BorderLayout.CENTER);

        JPanel buttons = Ui.leftRow(editButton, deleteButton);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(Theme.L, Theme.L, Theme.L, Theme.L));
        for (JComponent c : new JComponent[]{detailQuestion, answer, detailMeta, buttons}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(c);
            col.add(Box.createVerticalStrut(Theme.S));
        }
        // Pin the stack to the top so the answer panel keeps its content
        // height instead of stretching to fill the pane and pushing the
        // Edit / Delete buttons to the bottom edge.
        col.add(Box.createVerticalGlue());

        detailScroll.setViewportView(col);
        detailScroll.setBorder(null);
        detailScroll.setOpaque(false);
        detailScroll.getViewport().setOpaque(false);
        detailScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return detailScroll;
    }

    /** Question prose: read-only, wraps on words, no chrome of its own. */
    private static JTextArea prose(boolean heading) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        if (heading) {
            area.putClientProperty("FlatLaf.style", "font:+2 bold");
        }
        return area;
    }

    /** Shows the question of the given model row, or the empty state. */
    private void showDetail(int modelRow) {
        Question q = modelRow >= 0 && modelRow < questions.size()
                ? questions.get(modelRow) : null;
        if (q == null) {
            detailCards.show(detailPane, "empty");
            return;
        }
        detailQuestion.setText(q.getQuestionText());
        detailAnswer.setText(q.getAnswerText());
        detailMeta.setText("<html>" + q.getSubjectName() + " \u00B7 " + q.getModuleName()
                + "<br>" + q.getMarks() + " marks \u00B7 " + q.getDifficulty()
                + " \u00B7 ID " + q.getQuestionId() + "</html>");
        detailCards.show(detailPane, "detail");
        detailScroll.getVerticalScrollBar().setValue(0);
    }

    // --- CRUD actions ---

    private void onAdd() {
        QuestionForm form = showQuestionForm(null);
        if (form == null) {
            return;
        }
        try {
            questionService.addQuestion(form.toQuestion());
            totalStale = true;
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
            totalStale = true;
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
            totalStale = true;
            search();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not delete the question.");
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
            totalStale = true;
            search();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not delete the questions. Please check that MySQL is running.");
        }
    }

    // --- Search / filter ---

    /** Reloads the table from the service. Called by every filter change. */
    private void search() {
        Integer keepQuestionId = null;
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            keepQuestionId = (Integer) tableModel.getValueAt(selectedRow, 0);
        }
        Integer subjectId = null;
        Integer moduleId = null;
        Integer marks = null;
        // Index 0 is "All difficulties"; never hand that label to the service.
        String difficulty = difficultyFilter.getSelectedIndex() <= 0
                ? null : (String) difficultyFilter.getSelectedItem();

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
                int parsed = Integer.parseInt(marksText);
                if (parsed > 0) {
                    marks = parsed;
                }
                // 0 or negative: no marks filter (never an error dialog).
            } catch (NumberFormatException ex) {
                // Still typing: ignore the partial value, no dialog.
            }
        }

        try {
            List<Question> found = questionService.searchQuestions(
                    subjectId, moduleId, marks, difficulty, searchText.getText());
            questions.clear();
            questions.addAll(found);
            tableModel.setRowCount(0);
            for (Question q : questions) {
                tableModel.addRow(new Object[]{q.getQuestionId(), q.getQuestionText(),
                        q.getModuleName(), q.getMarks(), q.getDifficulty()});
            }
            if (totalStale) {
                try {
                    totalQuestions = questionService.searchQuestions(
                            null, null, null, null, null).size();
                    totalStale = false;
                } catch (SQLException | IllegalArgumentException ex) {
                    // Keep the last known total; the search above reports real errors.
                }
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
        // Refresh the detail pane and row-action states for the surviving selection.
        updateRowActionStates();
        updateCountLabel();
    }

    private void resetFilters() {
        subjectFilter.setSelectedIndex(0);
        moduleFilter.setSelectedIndex(0);
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
        // Rebuilding the items must not fire a search per list change.
        loadingModules = true;
        try {
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
        } finally {
            loadingModules = false;
        }
    }

    /**
     * Called by MainFrame when this panel becomes visible so the subject
     * list stays in sync with Subjects & Modules management.
     */
    public void refreshFilters() {
        totalStale = true;
        loadSubjectsIntoFilter();
        search();
    }

    // =====================================================================
    // Public facade (used by the handlers and the GUI smoke test)
    // =====================================================================

    /**
     * True while a question row is selected - the detail pane then shows
     * its Edit action.
     */
    public boolean isEditEnabled() {
        return table.getSelectedRow() >= 0;
    }

    /**
     * True while a question row is selected - the detail pane then shows
     * its Delete action.
     */
    public boolean isDeleteEnabled() {
        return table.getSelectedRow() >= 0;
    }

    /**
     * True while a question row is selected - the answer is then readable
     * in the detail pane (it replaced the old "View Answer" dialog).
     */
    public boolean isViewAnswerEnabled() {
        return table.getSelectedRow() >= 0;
    }

    public int getQuestionRowCount() {
        return tableModel.getRowCount();
    }

    /**
     * True while a concrete subject is selected in the filter - the
     * "Clear subject" action never runs against "(All Subjects)".
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

    /**
     * A left-aligned FlowLayout that wraps onto extra rows and reports a
     * preferred height matching the real row count. Plain FlowLayout always
     * reports a single-row height, so on a 1000x640 window the wrapped
     * filter controls would spill over the count row below them.
     */
    private static class WrappingRow extends FlowLayout {
        WrappingRow() {
            super(FlowLayout.LEFT, Theme.S, Theme.S);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return measure(target);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return measure(target);
        }

        /** Preferred size for the target's current width, counting wrapped rows. */
        private Dimension measure(Container target) {
            synchronized (target.getTreeLock()) {
                Insets insets = target.getInsets();
                int horizontal = insets.left + insets.right + getHgap() * 2;
                int vertical = insets.top + insets.bottom + getVgap();
                int maxWidth = target.getSize().width - horizontal;
                if (maxWidth <= 0) {
                    maxWidth = Integer.MAX_VALUE;
                }

                int rowWidth = 0;
                int rowHeight = 0;
                int widest = 0;
                for (Component child : target.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    Dimension size = child.getPreferredSize();
                    if (rowWidth + size.width > maxWidth && rowWidth > 0) {
                        widest = Math.max(widest, rowWidth - getHgap());
                        vertical += rowHeight + getVgap();
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    rowWidth += size.width + getHgap();
                    rowHeight = Math.max(rowHeight, size.height);
                }
                widest = Math.max(widest, rowWidth - getHgap());
                vertical += rowHeight;
                return new Dimension(horizontal + widest, vertical);
            }
        }
    }
}
