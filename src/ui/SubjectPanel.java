package ui;

import model.Module;
import model.Subject;
import service.ModuleService;
import service.SubjectService;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;

/**
 * Panel for managing subjects and their modules (Module 1).
 * All business logic is delegated to SubjectService / ModuleService.
 *
 * Presentation follows the Quest reference (DESIGN.md section v1): a page
 * header above two cards - subjects on the left, the selected subject's
 * modules on the right. The app shell pads the screen, so this panel adds
 * no border of its own.
 */
public class SubjectPanel extends JPanel {

    private final SubjectService subjectService = new SubjectService();
    private final ModuleService moduleService = new ModuleService();

    private final DefaultTableModel subjectModel = new DefaultTableModel(
            new String[]{"ID", "Subject", "Modules"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        /** The module count is a Number so the column renders right-aligned. */
        @Override
        public Class<?> getColumnClass(int column) {
            return column == 2 ? Integer.class : Object.class;
        }
    };
    private final DefaultTableModel moduleModel = new DefaultTableModel(
            new String[]{"ID", "Module"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable subjectTable = new JTable(subjectModel);
    private final JTable moduleTable = new JTable(moduleModel);

    private final JButton subjectEditButton = Ui.plain("Edit");
    private final JButton subjectDeleteButton = Ui.danger("Delete");
    private final JButton subjectAddButton = Ui.primary("Add subject");
    private final JButton moduleEditButton = Ui.plain("Edit");
    private final JButton moduleDeleteButton = Ui.danger("Delete");
    private final JButton moduleAddButton = Ui.primary("Add module");

    private final JLabel modulesTitle = Ui.bold("Modules");
    private final JLabel subjectEmptyLabel = Ui.muted("No subjects yet.");
    private final JLabel moduleEmptyLabel = Ui.muted("No modules yet.");

    private int selectedSubjectId = -1;

    /** True while the constructor runs: startup errors must not open dialogs. */
    private boolean initializing = true;

    public SubjectPanel() {
        setLayout(new BorderLayout(Theme.S, Theme.S));
        setOpaque(false);

        add(Ui.pageHeader("Subjects & modules", "Select a subject to manage its modules."),
                BorderLayout.NORTH);

        subjectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subjectTable.getAccessibleContext().setAccessibleName("Subjects");
        subjectTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSubjectSelected();
            }
        });

        moduleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        moduleTable.getAccessibleContext().setAccessibleName("Modules");
        moduleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateModuleButtonStates();
            }
        });

        subjectEditButton.addActionListener(e -> onEditSubject());
        subjectDeleteButton.addActionListener(e -> onDeleteSubject());
        subjectAddButton.addActionListener(e -> onAddSubject());
        moduleEditButton.addActionListener(e -> onEditModule());
        moduleDeleteButton.addActionListener(e -> onDeleteModule());
        moduleAddButton.addActionListener(e -> onAddModule());

        JPanel center = new JPanel(new GridLayout(1, 2, Theme.S, Theme.S));
        center.setOpaque(false);
        center.add(buildSubjectCard());
        center.add(buildModuleCard());
        add(center, BorderLayout.CENTER);

        try {
            refreshSubjects();
        } finally {
            initializing = false;
        }
    }

    // --- Cards ----------------------------------------------------------

    /** Left card: the subjects list with its row actions in the header. */
    private JPanel buildSubjectCard() {
        placeEmptyLabel(subjectEmptyLabel);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(subjectEmptyLabel, BorderLayout.NORTH);
        body.add(Ui.table(subjectTable), BorderLayout.CENTER);
        return Ui.card("Subjects", body,
                subjectEditButton, subjectDeleteButton, subjectAddButton);
    }

    /**
     * Right card: the modules of the selected subject. The title follows the
     * selection ("Modules in &lt;subject&gt;", or "Modules" with no selection).
     */
    private JPanel buildModuleCard() {
        placeEmptyLabel(moduleEmptyLabel);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(moduleEmptyLabel, BorderLayout.NORTH);
        body.add(Ui.table(moduleTable), BorderLayout.CENTER);
        return Ui.card(modulesTitle, body,
                moduleEditButton, moduleDeleteButton, moduleAddButton);
    }

    /** The empty-state note sits directly under the card header. */
    private static void placeEmptyLabel(JLabel label) {
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(Theme.S, Theme.M, Theme.S, Theme.M));
    }

    // --- Button enablement (unchanged rules) ---

    /** Row actions are only meaningful while a subject row is selected. */
    private void updateSubjectButtonStates() {
        boolean hasSelection = subjectTable.getSelectedRow() >= 0;
        subjectEditButton.setEnabled(hasSelection);
        subjectDeleteButton.setEnabled(hasSelection);
    }

    /** Row actions are only meaningful while a module row is selected. */
    private void updateModuleButtonStates() {
        boolean hasSelection = moduleTable.getSelectedRow() >= 0;
        moduleEditButton.setEnabled(hasSelection);
        moduleDeleteButton.setEnabled(hasSelection);
    }

    /** Keeps the right-hand card title in step with the subject selection. */
    private void updateModuleCardTitle() {
        int row = subjectTable.getSelectedRow();
        modulesTitle.setText(row >= 0
                ? "Modules in " + subjectModel.getValueAt(row, 1)
                : "Modules");
    }

    // --- Subject actions ---

    private void onAddSubject() {
        String name = JOptionPane.showInputDialog(this, "Subject name:", "Add Subject",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        try {
            subjectService.addSubject(name);
            refreshSubjects();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not add the subject. Please check that MySQL is running.");
        }
    }

    private void onEditSubject() {
        int row = subjectTable.getSelectedRow();
        if (row < 0) {
            showError("Select a subject to edit.");
            return;
        }
        int id = (int) subjectModel.getValueAt(row, 0);
        String current = (String) subjectModel.getValueAt(row, 1);
        String name = JOptionPane.showInputDialog(this, "Subject name:", current);
        if (name == null) {
            return;
        }
        try {
            subjectService.updateSubject(id, name);
            refreshSubjects();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not update the subject.");
        }
    }

    private void onDeleteSubject() {
        int row = subjectTable.getSelectedRow();
        if (row < 0) {
            showError("Select a subject to delete.");
            return;
        }
        int id = (int) subjectModel.getValueAt(row, 0);
        String name = (String) subjectModel.getValueAt(row, 1);
        if (!Ui.confirm(this, "Confirm Delete",
                "Delete subject \"" + name + "\"?", "Delete subject")) {
            return;
        }
        try {
            subjectService.deleteSubject(id);
            refreshSubjects();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not delete the subject.");
        }
    }

    // --- Module actions ---

    private void onAddModule() {
        if (selectedSubjectId < 0) {
            showError("Select a subject first.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Module name:", "Add Module",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        try {
            moduleService.addModule(selectedSubjectId, name);
            refreshModules();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not add the module.");
        }
    }

    private void onEditModule() {
        int row = moduleTable.getSelectedRow();
        if (row < 0) {
            showError("Select a module to edit.");
            return;
        }
        int id = (int) moduleModel.getValueAt(row, 0);
        String current = (String) moduleModel.getValueAt(row, 1);
        String name = JOptionPane.showInputDialog(this, "Module name:", current);
        if (name == null) {
            return;
        }
        try {
            moduleService.updateModule(id, selectedSubjectId, name);
            refreshModules();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not update the module.");
        }
    }

    private void onDeleteModule() {
        int row = moduleTable.getSelectedRow();
        if (row < 0) {
            showError("Select a module to delete.");
            return;
        }
        int id = (int) moduleModel.getValueAt(row, 0);
        String name = (String) moduleModel.getValueAt(row, 1);
        if (!Ui.confirm(this, "Confirm Delete",
                "Delete module \"" + name + "\"?", "Delete module")) {
            return;
        }
        try {
            moduleService.deleteModule(id);
            refreshModules();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (SQLException ex) {
            showError("Could not delete the module.");
        }
    }

    // --- Refresh / helpers ---

    /**
     * Reloads subjects (called by MainFrame when navigating back).
     */
    public void refreshIfChanged() {
        refreshSubjects();
    }

    private void onSubjectSelected() {
        int row = subjectTable.getSelectedRow();
        if (row >= 0) {
            selectedSubjectId = (int) subjectModel.getValueAt(row, 0);
        } else {
            selectedSubjectId = -1;
        }
        updateModuleCardTitle();
        updateSubjectButtonStates();
        refreshModules();
    }

    private void refreshSubjects() {
        try {
            List<Subject> subjects = subjectService.getAllSubjects();
            subjectModel.setRowCount(0);
            int rowToSelect = -1;
            for (Subject s : subjects) {
                subjectModel.addRow(new Object[]{
                        s.getSubjectId(), s.getSubjectName(),
                        moduleCount(s.getSubjectId())});
                if (s.getSubjectId() == selectedSubjectId) {
                    rowToSelect = subjectModel.getRowCount() - 1;
                }
            }
            if (rowToSelect >= 0) {
                subjectTable.setRowSelectionInterval(rowToSelect, rowToSelect);
            } else if (!subjects.isEmpty()) {
                subjectTable.setRowSelectionInterval(0, 0);
            } else {
                selectedSubjectId = -1;
                refreshModules();
            }
        } catch (SQLException ex) {
            showError("Could not load subjects. Please check that MySQL is running.");
        }
        updateModuleCardTitle();
        subjectEmptyLabel.setVisible(subjectModel.getRowCount() == 0);
        updateSubjectButtonStates();
        // The selection listener normally refreshed the module side already;
        // re-assert it for the error path where no row could be selected.
        updateModuleButtonStates();
    }

    private void refreshModules() {
        moduleModel.setRowCount(0);
        if (selectedSubjectId < 0) {
            moduleEmptyLabel.setVisible(moduleModel.getRowCount() == 0);
            updateModuleButtonStates();
            return;
        }
        try {
            List<Module> modules = moduleService.getModulesBySubject(selectedSubjectId);
            for (Module m : modules) {
                moduleModel.addRow(new Object[]{m.getModuleId(), m.getModuleName()});
            }
            syncSubjectModuleCount();
        } catch (SQLException ex) {
            showError("Could not load modules.");
        }
        moduleEmptyLabel.setVisible(moduleModel.getRowCount() == 0);
        updateModuleButtonStates();
    }

    /**
     * Module count shown in the subjects table. ModuleService exposes no
     * count method (only ModuleDAO does), so the count comes from the list
     * the service already provides - the UI never talks to the DAO.
     */
    private int moduleCount(int subjectId) throws SQLException {
        return moduleService.getModulesBySubject(subjectId).size();
    }

    /** Keeps the Modules column honest after a module is added or removed. */
    private void syncSubjectModuleCount() {
        if (selectedSubjectId < 0) {
            return;
        }
        int count = moduleModel.getRowCount();
        for (int row = 0; row < subjectModel.getRowCount(); row++) {
            if ((int) subjectModel.getValueAt(row, 0) == selectedSubjectId) {
                subjectModel.setValueAt(count, row, 2);
                return;
            }
        }
    }

    // =====================================================================
    // Public facade (used by the handlers and the GUI smoke test)
    // =====================================================================

    /** True while a subject row is selected. */
    public boolean isSubjectEditEnabled() {
        return subjectEditButton.isEnabled();
    }

    /** True while a subject row is selected. */
    public boolean isSubjectDeleteEnabled() {
        return subjectDeleteButton.isEnabled();
    }

    /** True while a module row is selected. */
    public boolean isModuleEditEnabled() {
        return moduleEditButton.isEnabled();
    }

    /** True while a module row is selected. */
    public boolean isModuleDeleteEnabled() {
        return moduleDeleteButton.isEnabled();
    }

    /**
     * Selects a subject row (or clears the selection when row &lt; 0).
     * Rows outside the table are ignored.
     */
    public void selectSubjectRow(int row) {
        if (row >= 0 && row < subjectModel.getRowCount()) {
            subjectTable.setRowSelectionInterval(row, row);
            subjectTable.scrollRectToVisible(subjectTable.getCellRect(row, 0, true));
        } else if (row < 0) {
            subjectTable.clearSelection();
        }
    }

    /**
     * Selects a module row (or clears the selection when row &lt; 0).
     * Rows outside the table are ignored.
     */
    public void selectModuleRow(int row) {
        if (row >= 0 && row < moduleModel.getRowCount()) {
            moduleTable.setRowSelectionInterval(row, row);
            moduleTable.scrollRectToVisible(moduleTable.getCellRect(row, 0, true));
        } else if (row < 0) {
            moduleTable.clearSelection();
        }
    }

    public int getSubjectRowCount() {
        return subjectModel.getRowCount();
    }

    /** Subject id of the given row, or -1 when the row is out of range. */
    public int getSubjectIdAt(int row) {
        return row >= 0 && row < subjectModel.getRowCount()
                ? (int) subjectModel.getValueAt(row, 0) : -1;
    }

    public int getModuleRowCount() {
        return moduleModel.getRowCount();
    }

    private void showError(String message) {
        if (initializing) {
            System.err.println("[startup] " + message);
            return;
        }
        JOptionPane.showMessageDialog(this, message, "Subjects & Modules",
                JOptionPane.ERROR_MESSAGE);
    }
}
