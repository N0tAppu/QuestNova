package ui;

import model.Module;
import model.Subject;
import service.ModuleService;
import service.SubjectService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;

/**
 * Panel for managing subjects and their modules (Module 1).
 * All business logic is delegated to SubjectService / ModuleService.
 */
public class SubjectPanel extends JPanel {

    private final SubjectService subjectService = new SubjectService();
    private final ModuleService moduleService = new ModuleService();

    private final DefaultTableModel subjectModel = new DefaultTableModel(
            new String[]{"ID", "Subject Name"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel moduleModel = new DefaultTableModel(
            new String[]{"ID", "Module Name"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable subjectTable = new JTable(subjectModel);
    private final JTable moduleTable = new JTable(moduleModel);

    private final JButton subjectEditButton = new JButton("Edit");
    private final JButton subjectDeleteButton = new JButton("Delete");
    private final JButton moduleEditButton = new JButton("Edit");
    private final JButton moduleDeleteButton = new JButton("Delete");
    private final JLabel subjectEmptyLabel = new JLabel("No subjects yet.");
    private final JLabel moduleEmptyLabel = new JLabel("No modules yet.");

    private int selectedSubjectId = -1;

    /** True while the constructor runs: startup errors must not open dialogs. */
    private boolean initializing = true;

    public SubjectPanel() {
        setLayout(new BorderLayout(UiTheme.GAP, UiTheme.GAP));
        setBorder(BorderFactory.createEmptyBorder(
                UiTheme.PAD, UiTheme.PAD, UiTheme.PAD, UiTheme.PAD));

        add(UiTheme.title("Subjects & Modules"), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, UiTheme.GAP, 0));

        // --- Subjects side ---
        JPanel subjectPanel = new JPanel(new BorderLayout(5, 5));
        UiTheme.styleCard(subjectPanel, "Subjects");
        subjectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UiTheme.styleTable(subjectTable);
        subjectTable.getAccessibleContext().setAccessibleName("Subjects");
        subjectTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSubjectSelected();
            }
        });
        subjectPanel.add(new JScrollPane(subjectTable), BorderLayout.CENTER);
        subjectPanel.add(buildButtons(this::onAddSubject, this::onEditSubject,
                this::onDeleteSubject, subjectEditButton, subjectDeleteButton,
                subjectEmptyLabel), BorderLayout.SOUTH);

        // --- Modules side ---
        JPanel modulePanel = new JPanel(new BorderLayout(5, 5));
        UiTheme.styleCard(modulePanel, "Modules");
        moduleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UiTheme.styleTable(moduleTable);
        moduleTable.getAccessibleContext().setAccessibleName("Modules");
        moduleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateModuleButtonStates();
            }
        });
        modulePanel.add(new JScrollPane(moduleTable), BorderLayout.CENTER);
        modulePanel.add(buildButtons(this::onAddModule, this::onEditModule,
                this::onDeleteModule, moduleEditButton, moduleDeleteButton,
                moduleEmptyLabel), BorderLayout.SOUTH);

        center.add(subjectPanel);
        center.add(modulePanel);
        add(center, BorderLayout.CENTER);

        try {
            refreshSubjects();
        } finally {
            initializing = false;
        }
    }

    private JPanel buildButtons(Runnable onAdd, Runnable onEdit, Runnable onDelete,
            JButton editButton, JButton deleteButton, JLabel emptyLabel) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setOpaque(false);
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> onAdd.run());
        editButton.addActionListener(e -> onEdit.run());
        deleteButton.addActionListener(e -> onDelete.run());
        UiTheme.primaryButton(addButton);
        UiTheme.neutralButton(editButton);
        UiTheme.neutralButton(deleteButton);
        emptyLabel.setForeground(UiTheme.MUTED);
        panel.add(emptyLabel);
        panel.add(addButton);
        panel.add(editButton);
        panel.add(deleteButton);
        return panel;
    }

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
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete subject \"" + name + "\"?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
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
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete module \"" + name + "\"?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
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
        updateSubjectButtonStates();
        refreshModules();
    }

    private void refreshSubjects() {
        try {
            List<Subject> subjects = subjectService.getAllSubjects();
            subjectModel.setRowCount(0);
            int rowToSelect = -1;
            for (Subject s : subjects) {
                subjectModel.addRow(new Object[]{s.getSubjectId(), s.getSubjectName()});
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
        } catch (SQLException ex) {
            showError("Could not load modules.");
        }
        moduleEmptyLabel.setVisible(moduleModel.getRowCount() == 0);
        updateModuleButtonStates();
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
