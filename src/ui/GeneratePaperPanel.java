package ui;

import model.BlueprintSlot;
import model.ExamBlueprint;
import model.ExamPatternRow;
import model.FeasibilityResult;
import model.GenerationResult;
import model.Module;
import model.PaperReview;
import model.Subject;
import service.BlueprintService;
import service.BlueprintValidator;
import service.ExamDifficultyPolicy;
import service.ModuleService;
import service.PaperGenerator;
import service.PaperReviewService;
import service.PatternPolicy;
import service.QuestionService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Examination Blueprint Editor (Stage 7).
 *
 * Lets the faculty define an examination freely - subject, examination type,
 * examination difficulty, total marks, which syllabus modules are covered,
 * how the marks are divided between them, and the exact question pattern of
 * every module (marks x count). No examination rule is hard-coded anywhere:
 * every number comes from the faculty.
 *
 * Stage 12: difficulty is NOT authored per pattern row. The faculty picks
 * ONE examination-level difficulty (Easy / Medium / Hard) and the
 * per-question difficulties are assigned randomly - availability-aware -
 * at generation time (ExamDifficultyPolicy).
 *
 * The derived question list (BlueprintSlot) is shown as a live preview
 * (with a sample difficulty assignment) and is what Stage 8 consumes for
 * feasibility checking and generation. Nothing is persisted at this stage.
 */
public class GeneratePaperPanel extends JPanel {

    /** Fixed seed for the preview sample only - real generation re-rolls. */
    private static final long PREVIEW_SEED = 20261004L;

    private final BlueprintService blueprintService = new BlueprintService();
    private final ModuleService moduleService = new ModuleService();
    private final QuestionService questionService = new QuestionService();
    private final BlueprintValidator availabilityValidator = new BlueprintValidator();
    private final PaperGenerator paperGenerator = new PaperGenerator();
    private final PaperReviewService reviewService = new PaperReviewService();

    private final ExamBlueprint exam = new ExamBlueprint();
    private final List<Module> allModules = new ArrayList<>();
    private int currentModuleId = -1;
    private boolean updating;

    /** Slots of the last successful generation (difficulty assigned). */
    private List<BlueprintSlot> lastGeneratedSlots;

    // --- Section A: examination details ---
    private final JComboBox<SubjectOption> subjectBox = new JComboBox<>();
    private final JComboBox<String> examTypeBox = new JComboBox<>();
    private final JComboBox<String> examDifficultyBox = new JComboBox<>();
    private final JSpinner totalMarksSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 10000, 1));

    // --- Section B: syllabus modules ---
    private final DefaultListModel<ModuleOption> availableModel = new DefaultListModel<>();
    private final JList<ModuleOption> availableList = new JList<>(availableModel);
    private final DefaultListModel<ModuleOption> selectedModel = new DefaultListModel<>();
    private final JList<ModuleOption> selectedList = new JList<>(selectedModel);

    // --- Section C: mark allocation ---
    private final DefaultTableModel allocModel = new DefaultTableModel(
            new String[]{"Module", "Allocated Marks", "Percentage",
                    "Pattern Total", "Difference"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 1) {
                super.setValueAt(value, row, column);
                return;
            }
            Integer parsed = parseMarks(value);
            if (parsed == null) {
                super.setValueAt(getValueAt(row, column), row, column);
                showError("Allocated marks must be a non-negative whole number.");
                return;
            }
            super.setValueAt(parsed, row, column);
            int moduleId = syllabusIds().get(row);
            exam.setAllocation(moduleId, parsed);
            refreshAll();
        }
    };
    private final JTable allocTable = new JTable(allocModel);
    private final JLabel allocFooterLabel = new JLabel();

    // --- Section D: question pattern ---
    private final DefaultTableModel patternModel = new DefaultTableModel(
            new String[]{"Marks", "Questions", "Subtotal"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable patternTable = new JTable(patternModel);
    private final JLabel patternTotalLabel = new JLabel();
    private final JButton addRowButton = Ui.plain("Add Row");
    private final JButton editRowButton = Ui.plain("Edit Row");
    private final JButton removeRowButton = Ui.danger("Remove Row");

    // --- Section E: pattern policy (Stage 12) ---
    private final JTextField policyAllocationField = new JTextField(6);
    private final JTextField policyCompositionField = new JTextField(10);
    private final JButton setRuleButton = Ui.plain("Set Rule");
    private final JButton applyPolicyButton = Ui.plain("Apply Pattern Policy");
    private final JLabel policyPreviewLabel =
            new JLabel("No pattern policy applied yet.");
    private final PatternPolicy patternPolicy = new PatternPolicy();

    // --- Derived preview ---
    private final DefaultTableModel previewModel = new DefaultTableModel(
            new String[]{"Q#", "Module", "Marks", "Difficulty"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable previewTable = new JTable(previewModel);
    private final JLabel summaryLabel = new JLabel();

    // --- Action buttons ---
    private final JButton generateButton = Ui.primary("Generate Paper");
    private final JButton clearButton = Ui.danger("Clear Blueprint");

    // --- Allocation meter (pinned card under the page header) ---
    private final Ui.Meter meter = new Ui.Meter();
    private final JLabel meterText = Ui.bold("0 of 0 marks");
    private final JLabel meterHint = Ui.muted("");

    /** Suppresses error dialogs while the constructor loads initial data. */
    private boolean initializing = true;

    public GeneratePaperPanel() {
        setLayout(new BorderLayout(Theme.S, Theme.S));
        setOpaque(false);

        add(Ui.pageHeader("Examination blueprint",
                "Define the subject, syllabus, mark split and question pattern."),
                BorderLayout.NORTH);
        Ui.styleTable(allocTable);
        Ui.styleTable(patternTable);
        Ui.styleTable(previewTable);
        allocTable.getAccessibleContext().setAccessibleName("Module mark allocation");
        patternTable.getAccessibleContext().setAccessibleName("Question pattern");
        previewTable.getAccessibleContext().setAccessibleName("Paper preview");
        allocTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        allocTable.getColumnModel().getColumn(1).setPreferredWidth(95);
        allocTable.getColumnModel().getColumn(2).setPreferredWidth(85);
        allocTable.getColumnModel().getColumn(3).setPreferredWidth(95);
        allocTable.getColumnModel().getColumn(4).setPreferredWidth(85);
        patternTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        patternTable.getColumnModel().getColumn(1).setPreferredWidth(85);
        patternTable.getColumnModel().getColumn(2).setPreferredWidth(95);
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(140);

        JPanel body = new ScrollBody();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(buildSectionA());
        body.add(buildSectionB());
        body.add(buildSectionC());
        body.add(buildSectionD());
        body.add(buildSectionE());
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);

        // Header + meter stay pinned; the section body scrolls only when the
        // window is shorter than the cards, so nothing is ever clipped away.
        JPanel mid = new JPanel(new BorderLayout(Theme.S, Theme.S));
        mid.setOpaque(false);
        mid.add(buildMeterCard(), BorderLayout.NORTH);
        mid.add(bodyScroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buildPreviewSection(), BorderLayout.CENTER);
        south.add(buildActions(), BorderLayout.SOUTH);
        add(mid, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        updating = true;
        try {
            examTypeBox.addItem("-- Select Examination Type --");
            for (String type : ExamBlueprint.EXAM_TYPES) {
                examTypeBox.addItem(type);
            }
            examDifficultyBox.addItem("-- Select Difficulty --");
            for (String difficulty : ExamBlueprint.EXAM_DIFFICULTIES) {
                examDifficultyBox.addItem(difficulty);
            }
        } finally {
            updating = false;
        }

        wireEvents();
        loadSubjects(-1);
        refreshAll();
        initializing = false;
    }

    // =====================================================================
    // Sections
    // =====================================================================

    /**
     * Scroll container for the section body: fills the viewport horizontally
     * whenever the widest section still fits, so a slightly wide section never
     * forces a stray horizontal scrollbar. When the window is too narrow for
     * the sections' minimum width, horizontal scrolling appears as before -
     * every control stays reachable.
     */
    private static final class ScrollBody extends JPanel implements Scrollable {

        ScrollBody() {
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect,
                                              int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? 16 : 10;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect,
                                               int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(16, visibleRect.height - 16)
                    : Math.max(10, visibleRect.width - 10);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport
                    && ((JViewport) getParent()).getWidth()
                       >= getMinimumSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JPanel buildSectionA() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        p.add(new JLabel("Subject:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        p.add(subjectBox, c);

        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        p.add(new JLabel("Examination Type:"), c);
        c.gridx = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        p.add(examTypeBox, c);

        c.gridx = 4; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        p.add(new JLabel("Difficulty:"), c);
        c.gridx = 5; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        p.add(examDifficultyBox, c);

        c.gridx = 6; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        p.add(new JLabel("Total Marks:"), c);
        c.gridx = 7;
        p.add(totalMarksSpinner, c);
        // The weighted fields must be allowed to compress below their
        // preferred width so the row fits a 1000px window without forcing
        // a stray horizontal scrollbar (acceptance F1/F6).
        subjectBox.setMinimumSize(new Dimension(
                Math.min(90, subjectBox.getPreferredSize().width),
                subjectBox.getPreferredSize().height));
        examTypeBox.setMinimumSize(new Dimension(
                Math.min(90, examTypeBox.getPreferredSize().width),
                examTypeBox.getPreferredSize().height));
        examDifficultyBox.setMinimumSize(new Dimension(
                Math.min(90, examDifficultyBox.getPreferredSize().width),
                examDifficultyBox.getPreferredSize().height));
        return card("A. Examination details", p);
    }

    private JPanel buildSectionB() {
        JPanel p = new JPanel(new BorderLayout(Theme.S, Theme.S));
        p.setOpaque(false);
        JLabel hint = Ui.muted("Select the modules this examination covers.");
        hint.setBorder(new EmptyBorder(0, Theme.XS, 0, Theme.XS));
        p.add(hint, BorderLayout.NORTH);

        JPanel availablePanel = new JPanel(new BorderLayout(Theme.XS, Theme.XS));
        availablePanel.setOpaque(false);
        JLabel availableCaption = Ui.muted("Modules of the selected subject:");
        availablePanel.add(availableCaption, BorderLayout.NORTH);
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane availableScroll = new JScrollPane(availableList);
        availableScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        availableScroll.getViewport().setBackground(Theme.SURFACE);
        availablePanel.add(availableScroll, BorderLayout.CENTER);
        JButton addButton = Ui.plain("Add to Syllabus");
        addButton.addActionListener(e -> onAddToSyllabus());
        availablePanel.add(Ui.leftRow(addButton), BorderLayout.SOUTH);
        availablePanel.setPreferredSize(new Dimension(420, 170));

        JPanel selectedPanel = new JPanel(new BorderLayout(Theme.XS, Theme.XS));
        selectedPanel.setOpaque(false);
        JLabel selectedCaption = Ui.muted("Selected for this examination:");
        selectedPanel.add(selectedCaption, BorderLayout.NORTH);
        selectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane selectedScroll = new JScrollPane(selectedList);
        selectedScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        selectedScroll.getViewport().setBackground(Theme.SURFACE);
        selectedPanel.add(selectedScroll, BorderLayout.CENTER);
        JButton removeButton = Ui.danger("Remove");
        removeButton.addActionListener(e -> onRemoveFromSyllabus());
        selectedPanel.add(Ui.leftRow(removeButton), BorderLayout.SOUTH);
        selectedPanel.setPreferredSize(new Dimension(420, 170));

        p.add(availablePanel, BorderLayout.WEST);
        p.add(selectedPanel, BorderLayout.CENTER);
        return card("B. Syllabus modules", p);
    }

    private JPanel buildSectionC() {
        JPanel p = new JPanel(new BorderLayout(Theme.XS, Theme.XS));
        p.setOpaque(false);
        JLabel hint = Ui.muted(
                "Edit Allocated Marks; percentage and totals are derived.");
        hint.setBorder(new EmptyBorder(0, Theme.XS, 0, Theme.XS));
        p.add(hint, BorderLayout.NORTH);
        allocTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        allocTable.setRowSelectionAllowed(true);
        JScrollPane scroll = new JScrollPane(allocTable);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setBackground(Theme.SURFACE);
        p.add(scroll, BorderLayout.CENTER);
        allocFooterLabel.setBorder(new EmptyBorder(2, Theme.M, 2, Theme.M));
        allocFooterLabel.setForeground(Theme.MUTED);
        p.add(allocFooterLabel, BorderLayout.SOUTH);
        return card("C. Module mark allocation", p);
    }

    private JPanel buildSectionD() {
        JPanel p = new JPanel(new BorderLayout(Theme.XS, Theme.XS));
        p.setOpaque(false);
        JLabel hint = Ui.muted("How each module is examined.");
        hint.setBorder(new EmptyBorder(0, Theme.XS, 0, Theme.XS));
        p.add(hint, BorderLayout.NORTH);
        patternTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(patternTable);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setBackground(Theme.SURFACE);
        p.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        addRowButton.addActionListener(e -> showPatternDialog(false));
        editRowButton.addActionListener(e -> showPatternDialog(true));
        removeRowButton.addActionListener(e -> onRemovePatternRow());
        south.add(Ui.leftRow(addRowButton, editRowButton, removeRowButton),
                BorderLayout.WEST);
        patternTotalLabel.setBorder(new EmptyBorder(2, Theme.M, 2, Theme.M));
        south.add(patternTotalLabel, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return card("D. Question pattern", p);
    }

    private JPanel buildSectionE() {
        JPanel p = new JPanel(new BorderLayout(Theme.XS, Theme.XS));
        p.setOpaque(false);
        JLabel hint = Ui.muted(
                "One global rule (for example 12 -> 4 + 8), applied to every"
                        + " allocated module.");
        hint.setBorder(new EmptyBorder(0, Theme.XS, 0, Theme.XS));
        JPanel form = Ui.leftRow(
                Ui.muted("Allocation marks:"), policyAllocationField,
                Ui.muted("Composes as:"), policyCompositionField,
                setRuleButton, applyPolicyButton);
        setRuleButton.addActionListener(e -> onSetPatternRule());
        applyPolicyButton.addActionListener(e -> onApplyPatternPolicy());
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(hint);
        north.add(form);
        p.add(north, BorderLayout.NORTH);
        policyPreviewLabel.setBorder(new EmptyBorder(2, Theme.M, 2, Theme.M));
        policyPreviewLabel.setForeground(Theme.MUTED);
        p.add(policyPreviewLabel, BorderLayout.SOUTH);
        return card("E. Pattern policy", p);
    }

    /** Pinned meter card: "X of Y marks" + progress bar + status hint. */
    private JPanel buildMeterCard() {
        meterText.putClientProperty("FlatLaf.style", "font:+4 bold");
        meterText.setAlignmentX(LEFT_ALIGNMENT);
        meter.setMinimumSize(new Dimension(100, 8));
        meter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        meter.setAlignmentX(LEFT_ALIGNMENT);
        meterHint.setAlignmentX(LEFT_ALIGNMENT);
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBorder(new EmptyBorder(Theme.M, Theme.M, Theme.M, Theme.M));
        stack.add(meterText);
        stack.add(Box.createVerticalStrut(Theme.XS));
        stack.add(meter);
        stack.add(Box.createVerticalStrut(Theme.XS));
        stack.add(meterHint);
        JPanel c = Ui.bareCard();
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.add(stack, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildPreviewSection() {
        previewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = Ui.table(previewTable);
        scroll.setPreferredSize(new Dimension(100, 140));
        JPanel body = new JPanel(new BorderLayout(Theme.XS, 0));
        body.setOpaque(false);
        body.add(scroll, BorderLayout.CENTER);
        summaryLabel.setBorder(new EmptyBorder(
                Theme.XS, Theme.M, Theme.XS, Theme.M));
        body.add(summaryLabel, BorderLayout.SOUTH);
        return card("Paper preview", body);
    }

    private JPanel buildActions() {
        JButton availabilityButton = Ui.plain("Check availability");
        availabilityButton.addActionListener(e -> onValidateAvailability());
        generateButton.addActionListener(e -> onGeneratePaper());
        clearButton.addActionListener(e -> onClear());
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
        bar.add(Ui.leftRow(clearButton), BorderLayout.WEST);
        bar.add(Ui.row(availabilityButton, generateButton), BorderLayout.EAST);
        return bar;
    }

    /** Wraps a section body in the shared card chrome (title + hairline). */
    private JPanel card(String title, JPanel body) {
        JPanel c = Ui.card(title, body);
        c.setAlignmentX(LEFT_ALIGNMENT);
        return c;
    }

    // =====================================================================
    // Events
    // =====================================================================

    private void wireEvents() {
        subjectBox.addActionListener(e -> {
            if (updating) {
                return;
            }
            SubjectOption s = (SubjectOption) subjectBox.getSelectedItem();
            applySubjectChange(s == null ? -1 : s.id, true);
        });
        examTypeBox.addActionListener(e -> {
            if (updating) {
                return;
            }
            String type = (String) examTypeBox.getSelectedItem();
            exam.setExamType(type == null ? "" : type);
            refreshAll();
        });
        examDifficultyBox.addActionListener(e -> {
            if (updating) {
                return;
            }
            String difficulty = (String) examDifficultyBox.getSelectedItem();
            exam.setExamDifficulty(difficulty == null
                    || difficulty.startsWith("--") ? "" : difficulty);
            refreshAll();
        });
        totalMarksSpinner.addChangeListener(e -> {
            if (updating) {
                return;
            }
            exam.setTotalMarks((Integer) totalMarksSpinner.getValue());
            refreshAll();
        });
        selectedList.addListSelectionListener(e -> {
            if (updating || e.getValueIsAdjusting()) {
                return;
            }
            int idx = selectedList.getSelectedIndex();
            setCurrentModule(idx < 0 ? -1 : syllabusIds().get(idx));
        });
        allocTable.getSelectionModel().addListSelectionListener(e -> {
            if (updating || e.getValueIsAdjusting()) {
                return;
            }
            int row = allocTable.getSelectedRow();
            setCurrentModule(row < 0 ? -1 : syllabusIds().get(row));
        });
        patternTable.getSelectionModel().addListSelectionListener(e -> {
            if (updating || e.getValueIsAdjusting()) {
                return;
            }
            updatePatternSelectionButtons();
        });
    }

    private void onAddToSyllabus() {
        int[] indices = availableList.getSelectedIndices();
        if (indices.length == 0) {
            showError("Select one or more modules to add to the syllabus.");
            return;
        }
        int[] ids = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            ids[i] = availableModel.get(indices[i]).id;
        }
        addModulesToSyllabus(ids);
    }

    private void onRemoveFromSyllabus() {
        int idx = selectedList.getSelectedIndex();
        if (idx < 0) {
            showError("Select a module to remove.");
            return;
        }
        int moduleId = syllabusIds().get(idx);
        if (exam.getAllocation(moduleId) > 0
                || !exam.getPatternRows(moduleId).isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Removing \"" + exam.getModuleName(moduleId)
                            + "\" will discard its marks allocation and question pattern.\nContinue?",
                    "Confirm Removal", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        removeFromSyllabus(moduleId);
    }

    private void onRemovePatternRow() {
        if (currentModuleId < 0) {
            showError("Select a module first.");
            return;
        }
        int row = patternTable.getSelectedRow();
        if (row < 0) {
            showError("Select a pattern row to remove.");
            return;
        }
        try {
            blueprintService.removePatternRow(exam, currentModuleId, row);
            refreshAll();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onValidateAvailability() {
        List<String> structural = blueprintService.validateBlueprint(exam);
        if (!structural.isEmpty()) {
            showError("Blueprint invalid - fix these structural problems first:\n\n"
                    + String.join("\n", structuralLines(structural)));
            return;
        }
        try {
            FeasibilityResult feasibility = checkAvailability();
            String message = feasibility.getSummaryReport();
            if (!feasibility.isFeasible()) {
                message += "\n\n" + feasibility.getFailureReport();
            }
            JOptionPane.showMessageDialog(this, message, "Validate Availability",
                    feasibility.isFeasible()
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            showError("Could not check the question bank. Please check that MySQL is running.");
        }
    }

    private void onGeneratePaper() {
        List<String> structural = blueprintService.validateBlueprint(exam);
        if (!structural.isEmpty()) {
            showError("Blueprint invalid - fix these structural problems first:\n\n"
                    + String.join("\n", structuralLines(structural)));
            return;
        }
        GenerationResult result = generatePaper();
        if (result.isSuccess()) {
            try {
                PaperReview review = reviewService.openReview(
                        result.getPaper(), getLastGeneratedSlots());
                ReviewPaperDialog.show(SwingUtilities.getWindowAncestor(this), review);
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Generation failed.\n\n" + result.getFailureMessage(),
                "Generate Paper", JOptionPane.ERROR_MESSAGE);
    }

    private List<String> structuralLines(List<String> structural) {
        List<String> lines = new ArrayList<>();
        for (String error : structural) {
            lines.add("- " + error);
        }
        return lines;
    }

    private void onClear() {
        if (!exam.hasAnyData()) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Clear the entire examination blueprint?",
                "Confirm Clear", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        exam.clear();
        currentModuleId = -1;
        resetControls();
        refreshAll();
    }

    private void applySubjectChange(int newId, boolean interactive) {
        if (newId == exam.getSubjectId()) {
            return;
        }
        if (interactive && exam.hasAnyData()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Changing the subject will clear the current examination blueprint.\nContinue?",
                    "Confirm Subject Change", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                updating = true;
                try {
                    subjectBox.setSelectedIndex(findSubjectIndex(exam.getSubjectId()));
                } finally {
                    updating = false;
                }
                return;
            }
        }
        exam.clear();
        exam.setSubjectId(newId);
        exam.setSubjectName(subjectNameOf(newId));
        currentModuleId = -1;
        resetControls();
        loadAllModules();
        refreshAll();
    }

    // =====================================================================
    // Public facade (used by the UI handlers and the GUI smoke test)
    // =====================================================================

    /** Selects the subject as if chosen in the combo box (no confirm dialog). */
    public void selectSubject(int subjectId) {
        applySubjectChange(subjectId, false);
        updating = true;
        try {
            subjectBox.setSelectedIndex(findSubjectIndex(subjectId));
        } finally {
            updating = false;
        }
    }

    public void setExamType(String examType) {
        updating = true;
        try {
            examTypeBox.setSelectedItem(examType);
        } finally {
            updating = false;
        }
        exam.setExamType(examType == null ? "" : examType);
        refreshAll();
    }

    public void setTotalMarks(int totalMarks) {
        updating = true;
        try {
            totalMarksSpinner.setValue(totalMarks);
        } finally {
            updating = false;
        }
        exam.setTotalMarks(totalMarks);
        refreshAll();
    }

    /** Selects the examination-level difficulty as if chosen in the combo box. */
    public void setExamDifficulty(String examDifficulty) {
        updating = true;
        try {
            examDifficultyBox.setSelectedItem(examDifficulty);
        } finally {
            updating = false;
        }
        exam.setExamDifficulty(examDifficulty == null ? "" : examDifficulty);
        refreshAll();
    }

    public void addModulesToSyllabus(int... moduleIds) {
        for (int moduleId : moduleIds) {
            String name = null;
            for (Module m : allModules) {
                if (m.getModuleId() == moduleId) {
                    name = m.getModuleName();
                    break;
                }
            }
            if (name == null) {
                continue;
            }
            exam.selectModule(moduleId, name);
            if (currentModuleId < 0) {
                currentModuleId = moduleId;
            }
        }
        refreshAll();
    }

    public void removeFromSyllabus(int moduleId) {
        exam.deselectModule(moduleId);
        if (currentModuleId == moduleId) {
            List<Integer> ids = syllabusIds();
            currentModuleId = ids.isEmpty() ? -1 : ids.get(0);
        }
        refreshAll();
    }

    public void setModuleAllocation(int moduleId, int marks) {
        exam.setAllocation(moduleId, marks);
        refreshAll();
    }

    public void addPattern(int moduleId, int marks, int questionCount) {
        blueprintService.addPatternRow(exam, moduleId, marks, questionCount);
        refreshAll();
    }

    // =====================================================================
    // Stage 12 facade: pattern policy
    // =====================================================================

    /**
     * Stores a global pattern rule from its text form, for example
     * "12" with "4+8" meaning a 12-mark allocation composes as 4 + 8.
     * The stored rule is used exactly as authored for that allocation -
     * it is never scaled down to other allocations.
     *
     * @throws IllegalArgumentException for non-numeric or invalid text
     */
    public void setPatternRule(String allocationText, String compositionText) {
        int allocation = parsePositiveInt(allocationText,
                "The rule allocation must be a positive whole number, for example 12.");
        List<Integer> composition = parseComposition(compositionText);
        patternPolicy.setRule(allocation, composition);
        policyAllocationField.setText(allocationText == null
                ? "" : allocationText.trim());
        policyCompositionField.setText(compositionText == null
                ? "" : compositionText.trim());
        refreshPolicyPreview();
    }

    /** Whether a global rule is configured for the given allocation. */
    public boolean hasPatternRule(int allocation) {
        return patternPolicy.hasRule(allocation);
    }

    /**
     * Applies the stored pattern policy to every allocated module of the
     * current examination and refreshes the derived pattern, preview and
     * validation state. Composition comes from the question bank's own
     * mark denominations - no marks are hard-coded.
     *
     * @throws SQLException when the question bank cannot be read
     * @throws IllegalArgumentException when composition is impossible
     */
    public void applyPatternPolicy() throws SQLException {
        blueprintService.applyPatternPolicy(exam, patternPolicy);
        refreshAll();
        refreshPolicyPreview();
    }

    /** The human-readable preview of every module's composed pattern. */
    public String getPolicyPreviewText() {
        return policyPreviewLabel.getText();
    }

    private void onSetPatternRule() {
        try {
            setPatternRule(policyAllocationField.getText(),
                    policyCompositionField.getText());
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void onApplyPatternPolicy() {
        try {
            applyPatternPolicy();
        } catch (SQLException ex) {
            showError("Could not read the question bank marks. "
                    + "Please check that MySQL is running.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    /** Live preview: every allocated module -> its composed marks. */
    private void refreshPolicyPreview() {
        StringBuilder sb = new StringBuilder("<html>");
        boolean any = false;
        for (Integer moduleId : syllabusIds()) {
            int allocation = exam.getAllocation(moduleId);
            if (allocation <= 0) {
                continue;
            }
            any = true;
            sb.append(exam.getModuleName(moduleId)).append(" -&gt; ");
            List<ExamPatternRow> rows = exam.getPatternRows(moduleId);
            if (rows.isEmpty()) {
                sb.append("not composed yet - use Apply Pattern Policy");
            } else {
                boolean first = true;
                for (ExamPatternRow row : rows) {
                    for (int i = 0; i < row.getQuestionCount(); i++) {
                        if (!first) {
                            sb.append(" + ");
                        }
                        sb.append(row.getMarks());
                        first = false;
                    }
                }
            }
            sb.append("<br>");
        }
        sb.append("</html>");
        policyPreviewLabel.setText(any
                ? sb.toString() : "No pattern policy applied yet.");
    }

    private static int parsePositiveInt(String text, String message) {
        int value;
        try {
            value = Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(message);
        }
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static List<Integer> parseComposition(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Enter the composition, for example 4+8.");
        }
        List<Integer> parts = new ArrayList<>();
        for (String token : text.split("\\+")) {
            int value;
            try {
                value = Integer.parseInt(token.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Every part of the composition must be a whole number, "
                                + "for example 4+8.");
            }
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "Every part of the composition must be a positive whole number.");
            }
            parts.add(value);
        }
        return parts;
    }

    /**
     * Checks whether the question bank can satisfy the current blueprint.
     * Feasibility depends only on (module, marks) totals, never on the
     * random difficulty assignment.
     */
    public FeasibilityResult checkAvailability() throws SQLException {
        return availabilityValidator.checkAvailability(
                exam.getSubjectId(), blueprintService.generateSlots(exam));
    }

    /**
     * Attempts full paper generation (feasibility gate first, then the
     * difficulty assignment and random selection). Never returns a partial
     * paper. On success the assigned slot list is kept for the review.
     */
    public GenerationResult generatePaper() {
        List<BlueprintSlot> slots = blueprintService.generateSlots(exam);
        GenerationResult result = paperGenerator.generatePaper(
                exam.getSubjectId(), exam.getSubjectName(), exam.getExamType(),
                exam.getExamDifficulty(), exam.getTotalMarks(), slots);
        lastGeneratedSlots = result.isSuccess() ? slots : null;
        return result;
    }

    public ExamBlueprint getExam() {
        return exam;
    }

    /**
     * The derived exact question list Stage 8 will consume. Difficulty is
     * NOT assigned here - it comes from the generation run, when any.
     */
    public List<BlueprintSlot> getSlots() {
        return lastGeneratedSlots != null
                ? lastGeneratedSlots : blueprintService.generateSlots(exam);
    }

    /**
     * The slot list of the last SUCCESSFUL generation, with every
     * difficulty assigned by ExamDifficultyPolicy (null after any blueprint
     * change or failed generation). This is the list the review must use so
     * that slots and generated questions stay in sync.
     */
    public List<BlueprintSlot> getLastGeneratedSlots() {
        return lastGeneratedSlots;
    }

    public List<String> getValidationErrors() {
        return blueprintService.validateBlueprint(exam);
    }

    public int getSelectedSubjectId() {
        return exam.getSubjectId();
    }

    public String getSummaryText() {
        return summaryLabel.getText();
    }

    public int getPreviewRowCount() {
        return previewModel.getRowCount();
    }

    /** Whether the Generate Paper button is currently enabled. */
    public boolean isGenerateEnabled() {
        return generateButton.isEnabled();
    }

    /** Whether the Clear Blueprint button is currently enabled. */
    public boolean isClearBlueprintEnabled() {
        return clearButton.isEnabled();
    }

    /** Whether Edit Row is enabled (module selected AND a pattern row selected). */
    public boolean isPatternEditEnabled() {
        return editRowButton.isEnabled();
    }

    /** Whether Remove Row is enabled (module selected AND a pattern row selected). */
    public boolean isPatternRemoveEnabled() {
        return removeRowButton.isEnabled();
    }

    /**
     * Selects a pattern row as if clicked: a valid row index selects and
     * reveals it, a negative index clears the selection, an out-of-range
     * positive index is ignored.
     */
    public void selectPatternRow(int row) {
        if (row < 0) {
            patternTable.clearSelection();
            return;
        }
        if (row >= patternTable.getRowCount()) {
            return;
        }
        patternTable.setRowSelectionInterval(row, row);
        patternTable.scrollRectToVisible(patternTable.getCellRect(row, 0, true));
    }

    /**
     * Called by MainFrame when this panel becomes visible. Also drops any
     * syllabus module that no longer exists in the database.
     */
    public void refreshPanel() {
        int keep = exam.getSubjectId();
        loadSubjects(keep);
        if (exam.getSubjectId() > 0) {
            loadAllModules();
            pruneDeletedModules();
        }
        refreshAll();
    }

    /** Removes syllabus modules that are no longer part of the subject. */
    private void pruneDeletedModules() {
        List<Integer> snapshot = new ArrayList<>(exam.getSelectedModuleIds());
        for (Integer moduleId : snapshot) {
            boolean present = false;
            for (Module m : allModules) {
                if (m.getModuleId() == moduleId) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                exam.deselectModule(moduleId);
            }
        }
        if (currentModuleId >= 0
                && !exam.getSelectedModuleIds().contains(currentModuleId)) {
            List<Integer> remaining = exam.getSelectedModuleIds();
            currentModuleId = remaining.isEmpty() ? -1 : remaining.get(0);
        }
    }

    // =====================================================================
    // Refresh
    // =====================================================================

    private void refreshAll() {
        updating = true;
        try {
            lastGeneratedSlots = null;
            rebuildAvailableList();
            rebuildSelectedList();
            rebuildAllocTable();
            rebuildPatternSection(true);
            rebuildPreview();
            rebuildSummary();
            refreshPolicyPreview();
            syncSelectionToCurrent();
            generateButton.setEnabled(getValidationErrors().isEmpty());
            clearButton.setEnabled(exam.hasAnyData());
        } finally {
            updating = false;
        }
    }

    private void rebuildAvailableList() {
        availableModel.clear();
        for (Module m : allModules) {
            if (!exam.isModuleSelected(m.getModuleId())) {
                availableModel.addElement(
                        new ModuleOption(m.getModuleId(), m.getModuleName()));
            }
        }
    }

    private void rebuildSelectedList() {
        selectedModel.clear();
        for (Integer moduleId : syllabusIds()) {
            selectedModel.addElement(
                    new ModuleOption(moduleId, exam.getModuleName(moduleId)));
        }
    }

    private void rebuildAllocTable() {
        allocModel.setRowCount(0);
        int total = exam.getTotalMarks();
        for (Integer moduleId : syllabusIds()) {
            int allocation = exam.getAllocation(moduleId);
            int patternTotal = blueprintService.patternTotal(
                    exam.getPatternRows(moduleId));
            allocModel.addRow(new Object[]{
                    exam.getModuleName(moduleId),
                    allocation,
                    String.format("%.1f%%", blueprintService.percentage(allocation, total)),
                    patternTotal,
                    patternTotal - allocation
            });
        }
        int allocatedSum = 0;
        for (Integer moduleId : syllabusIds()) {
            allocatedSum += exam.getAllocation(moduleId);
        }
        allocFooterLabel.setText(String.format(
                "Allocated: %d marks  |  Examination total: %d marks  |  Difference: %d",
                allocatedSum, total, total - allocatedSum));
    }

    /**
     * Rebuilds the pattern table for the current module. When keepSelection
     * is true (same module context, e.g. a refresh), the selected row is
     * restored so Edit/Remove stay enabled; module switches clear it.
     */
    private void rebuildPatternSection(boolean keepSelection) {
        int keepRow = keepSelection ? patternTable.getSelectedRow() : -1;
        patternModel.setRowCount(0);
        if (currentModuleId < 0) {
            patternTotalLabel.setText(
                    "Select a module in Section B to define its question pattern.");
            setPatternButtonsEnabled(false);
            return;
        }
        List<ExamPatternRow> rows = exam.getPatternRows(currentModuleId);
        for (ExamPatternRow row : rows) {
            patternModel.addRow(new Object[]{
                    row.getMarks(), row.getQuestionCount(), row.getTotalMarks()
            });
        }
        if (keepRow >= 0 && keepRow < patternModel.getRowCount()) {
            patternTable.setRowSelectionInterval(keepRow, keepRow);
        }
        int patternTotal = blueprintService.patternTotal(rows);
        int allocation = exam.getAllocation(currentModuleId);
        patternTotalLabel.setText(String.format(
                "<html><b>%s</b> &nbsp; Pattern total: %d marks &nbsp; Allocated: %d marks"
                        + " &nbsp; Difference: %d</html>",
                exam.getModuleName(currentModuleId), patternTotal, allocation,
                patternTotal - allocation));
        addRowButton.setEnabled(true);
        updatePatternSelectionButtons();
    }

    private void setPatternButtonsEnabled(boolean enabled) {
        addRowButton.setEnabled(enabled);
        editRowButton.setEnabled(enabled);
        removeRowButton.setEnabled(enabled);
    }

    /** Edit/remove are only useful with a module AND a selected pattern row. */
    private void updatePatternSelectionButtons() {
        boolean rowSelected = patternTable.getSelectedRow() >= 0;
        boolean enabled = currentModuleId >= 0 && rowSelected;
        editRowButton.setEnabled(enabled);
        removeRowButton.setEnabled(enabled);
    }

    private void rebuildPreview() {
        previewModel.setRowCount(0);
        List<BlueprintSlot> slots = blueprintService.generateSlots(exam);
        // Sample difficulty assignment for display only (fixed seed, no
        // availability) - the real assignment happens at generation time.
        if (ExamDifficultyPolicy.isValidExamDifficulty(exam.getExamDifficulty())
                && !slots.isEmpty()) {
            ExamDifficultyPolicy.assign(slots, exam.getExamDifficulty(), null,
                    new Random(PREVIEW_SEED));
        }
        for (BlueprintSlot slot : slots) {
            String difficulty = slot.getDifficulty() == null
                    ? "assigned at generation" : slot.getDifficulty();
            previewModel.addRow(new Object[]{
                    slot.getSequenceNo(), slot.getModuleName(),
                    slot.getMarks(), difficulty
            });
        }
    }

    private void rebuildSummary() {
        List<String> errors = blueprintService.validateBlueprint(exam);
        updateMeter();
        if (errors.isEmpty()) {
            List<BlueprintSlot> slots = blueprintService.generateSlots(exam);
            summaryLabel.setText(String.format(
                    "<html><b style=\"color:#%06X\">VALID</b> &nbsp; Questions: %d &nbsp;"
                            + " Total marks: %d</html>",
                    Theme.OK.getRGB() & 0xFFFFFF,
                    slots.size(), blueprintService.totalMarks(slots)));
            generateButton.setToolTipText(null);
            return;
        }
        StringBuilder sb = new StringBuilder(String.format(
                "<html><b style=\"color:#%06X\">NOT VALID</b><br>",
                Theme.BAD.getRGB() & 0xFFFFFF));
        for (String error : errors) {
            sb.append("&bull; ").append(error).append("<br>");
        }
        sb.append("</html>");
        summaryLabel.setText(sb.toString());
        generateButton.setToolTipText(String.join("\n", errors));
    }

    /** Meter: allocated marks against the examination total, plus a hint. */
    private void updateMeter() {
        int total = exam.getTotalMarks();
        int allocated = 0;
        for (Integer moduleId : syllabusIds()) {
            allocated += exam.getAllocation(moduleId);
        }
        meterText.setText(allocated + " of " + total + " marks");
        meter.set(allocated, Math.max(total, 1));
        if (total <= 0) {
            meterHint.setText("Set the examination total marks.");
            meterHint.setForeground(Theme.MUTED);
        } else if (allocated > total) {
            meterHint.setText("Over by " + (allocated - total)
                    + " marks. Remove or lower a rule.");
            meterHint.setForeground(Theme.BAD);
        } else if (allocated < total) {
            meterHint.setText((total - allocated) + " marks left to allocate.");
            meterHint.setForeground(Theme.WARN);
        } else {
            meterHint.setText("Ready to generate.");
            meterHint.setForeground(Theme.OK);
        }
    }

    private void setCurrentModule(int moduleId) {
        if (moduleId == currentModuleId) {
            syncSelectionToCurrent();
            return;
        }
        currentModuleId = moduleId;
        rebuildPatternSection(false);
        syncSelectionToCurrent();
    }

    private void syncSelectionToCurrent() {
        updating = true;
        try {
            int idx = syllabusIds().indexOf(currentModuleId);
            if (idx >= 0 && idx < allocTable.getRowCount()) {
                selectedList.setSelectedIndex(idx);
                allocTable.setRowSelectionInterval(idx, idx);
            } else {
                selectedList.clearSelection();
                allocTable.clearSelection();
            }
        } finally {
            updating = false;
        }
    }

    private List<Integer> syllabusIds() {
        return exam.getSelectedModuleIds();
    }

    // =====================================================================
    // Pattern row dialog
    // =====================================================================

    private void showPatternDialog(boolean edit) {
        if (currentModuleId < 0) {
            showError("Select a module first.");
            return;
        }
        int rowIndex = -1;
        ExamPatternRow existing = null;
        if (edit) {
            rowIndex = patternTable.getSelectedRow();
            if (rowIndex < 0) {
                showError("Select a pattern row to edit.");
                return;
            }
            existing = exam.getPatternRows(currentModuleId).get(rowIndex);
        }

        JSpinner marks = new JSpinner(new SpinnerNumberModel(
                existing == null ? 2 : existing.getMarks(), 1, 10000, 1));
        JSpinner count = new JSpinner(new SpinnerNumberModel(
                existing == null ? 1 : existing.getQuestionCount(), 1, 1000, 1));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0;
        form.add(new JLabel("Marks per question:"), c);
        c.gridx = 1;
        form.add(marks, c);
        c.gridx = 0; c.gridy = 1;
        form.add(new JLabel("Number of questions:"), c);
        c.gridx = 1;
        form.add(count, c);

        int choice = JOptionPane.showConfirmDialog(this, form,
                edit ? "Edit Pattern Row" : "Add Pattern Row",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            final int moduleId = currentModuleId;
            final int marksValue = (Integer) marks.getValue();
            final int countValue = (Integer) count.getValue();
            final int targetRow = rowIndex;
            if (edit) {
                blueprintService.updatePatternRow(
                        exam, moduleId, targetRow, marksValue, countValue);
            } else {
                blueprintService.addPatternRow(
                        exam, moduleId, marksValue, countValue);
            }
            refreshAll();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    // =====================================================================
    // Loading
    // =====================================================================

    private void loadSubjects(int selectId) {
        boolean found = selectId <= 0;
        updating = true;
        try {
            subjectBox.removeAllItems();
            subjectBox.addItem(new SubjectOption(-1, "-- Select Subject --"));
            for (Subject s : questionService.getAllSubjects()) {
                subjectBox.addItem(new SubjectOption(s.getSubjectId(), s.getSubjectName()));
                if (s.getSubjectId() == selectId) {
                    found = true;
                    exam.setSubjectName(s.getSubjectName());
                }
            }
            subjectBox.setSelectedIndex(findSubjectIndex(selectId));
        } catch (SQLException ex) {
            showError("Could not load subjects.");
        } finally {
            updating = false;
        }
        if (selectId > 0 && !found) {
            exam.clear();
            currentModuleId = -1;
            resetControls();
        }
    }

    private void loadAllModules() {
        allModules.clear();
        if (exam.getSubjectId() <= 0) {
            return;
        }
        try {
            allModules.addAll(moduleService.getModulesBySubject(exam.getSubjectId()));
        } catch (SQLException ex) {
            showError("Could not load modules.");
        }
    }

    private void resetControls() {
        updating = true;
        try {
            if (examTypeBox.getItemCount() > 0) {
                examTypeBox.setSelectedIndex(0);
            }
            if (examDifficultyBox.getItemCount() > 0) {
                examDifficultyBox.setSelectedIndex(0);
            }
            totalMarksSpinner.setValue(0);
        } finally {
            updating = false;
        }
    }

    private int findSubjectIndex(int subjectId) {
        for (int i = 0; i < subjectBox.getItemCount(); i++) {
            if (subjectBox.getItemAt(i).id == subjectId) {
                return i;
            }
        }
        return 0;
    }

    private String subjectNameOf(int subjectId) {
        for (int i = 0; i < subjectBox.getItemCount(); i++) {
            SubjectOption s = subjectBox.getItemAt(i);
            if (s.id == subjectId) {
                return s.name;
            }
        }
        return "";
    }

    private static Integer parseMarks(Object value) {
        try {
            if (value instanceof Integer) {
                int n = (Integer) value;
                return n < 0 ? null : n;
            }
            int n = Integer.parseInt(String.valueOf(value).trim());
            return n < 0 ? null : n;
        } catch (NumberFormatException | NullPointerException ex) {
            return null;
        }
    }

    private void showError(String message) {
        if (initializing) {
            System.err.println("[startup] " + message);
            return;
        }
        JOptionPane.showMessageDialog(this, message, "Examination Blueprint",
                JOptionPane.ERROR_MESSAGE);
    }

    // --- Option helper classes ---

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
