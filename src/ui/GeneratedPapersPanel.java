package ui;

import model.PaperReview;
import model.QuestionPaper;
import service.PaperReviewService;

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
import java.awt.Font;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Generated Papers history screen (Stage 9).
 * Lists every finalized paper and opens one as a read-only review.
 * Finalized papers can never be edited - only viewed and exported as a
 * PDF (Stage 10).
 *
 * Stage 12: the selected paper can be deleted. The action is only enabled
 * with a selection, always asks for explicit confirmation, removes ONLY
 * the paper's stored rows (the question bank is never affected) and clears
 * the stale selection afterwards.
 */
public class GeneratedPapersPanel extends JPanel {

    private final PaperReviewService reviewService = new PaperReviewService();
    private final List<QuestionPaper> papers = new ArrayList<>();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Paper ID", "Subject", "Total Marks", "Generated On"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JButton openButton = new JButton("Open Paper");
    private final JButton deleteButton = new JButton("Delete Question Paper");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel emptyLabel = new JLabel("No papers have been finalized yet.");

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm");

    /** Suppresses error dialogs while the constructor loads initial data. */
    private boolean initializing = true;

    public GeneratedPapersPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Generated Papers");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionStates();
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        south.add(emptyLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        openButton.addActionListener(e -> onOpen());
        deleteButton.addActionListener(e -> onDelete());
        refreshButton.addActionListener(e -> refreshPanel());
        buttons.add(openButton);
        buttons.add(deleteButton);
        buttons.add(refreshButton);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        refreshPanel();
        initializing = false;
    }

    // =====================================================================
    // Public facade (used by MainFrame and the GUI smoke test)
    // =====================================================================

    /** Reloads the history. Called by MainFrame when this panel is shown. */
    public void refreshPanel() {
        try {
            papers.clear();
            papers.addAll(reviewService.getPapers());
            rebuildTable();
        } catch (SQLException ex) {
            showError("Could not load the list of generated papers. Please check that MySQL is running.");
        }
    }

    public int getRowCount() {
        return tableModel.getRowCount();
    }

    /** Paper id of the selected row, or -1 when nothing is selected. */
    public int getSelectedPaperId() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= papers.size()) {
            return -1;
        }
        return papers.get(row).getPaperId();
    }

    public void selectRow(int row) {
        if (row >= 0 && row < tableModel.getRowCount()) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
    }

    /**
     * Loads the selected finalized paper as a read-only review.
     *
     * @return the review, or null when nothing is selected
     */
    public PaperReview loadSelected() {
        int paperId = getSelectedPaperId();
        if (paperId < 0) {
            return null;
        }
        try {
            return reviewService.loadPaper(paperId);
        } catch (SQLException ex) {
            showError("Could not open the paper. Please check that MySQL is running.");
            return null;
        }
    }

    /** Opens the selected paper in the read-only review dialog. */
    public void openSelected() {
        PaperReview review = loadSelected();
        if (review == null) {
            if (getSelectedPaperId() < 0) {
                showError("Please select a paper first.");
            } else {
                showError("The selected paper could not be opened. It may no longer exist. "
                        + "Please refresh the list.");
            }
            return;
        }
        ReviewPaperDialog.show(
                javax.swing.SwingUtilities.getWindowAncestor(this), review);
    }

    // =====================================================================
    // Stage 12 facade: paper deletion
    // =====================================================================

    /** Whether the Delete Question Paper button is currently enabled. */
    public boolean isDeleteEnabled() {
        return deleteButton.isEnabled();
    }

    /** Whether the Open Paper button is currently enabled. */
    public boolean isOpenEnabled() {
        return openButton.isEnabled();
    }

    /** The label of the delete action button. */
    public String getDeleteActionText() {
        return deleteButton.getText();
    }

    /** Clears the table selection (disables the selection-bound actions). */
    public void clearSelection() {
        table.clearSelection();
    }

    /**
     * Deletes the selected paper - NO confirmation dialog here (the dialog
     * belongs to the button handler), so callers can decide the flow.
     * On success the list is refreshed and the stale selection cleared.
     *
     * @return true only when a paper was actually deleted
     */
    public boolean deleteSelectedPaper() {
        int paperId = getSelectedPaperId();
        if (paperId < 0) {
            return false;
        }
        try {
            reviewService.deletePaper(paperId);
        } catch (SQLException ex) {
            showError("Could not delete the question paper. "
                    + "Please check that MySQL is running.");
            return false;
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            refreshPanel();
            return false;
        }
        refreshPanel();
        clearSelection();
        return true;
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private void onOpen() {
        openSelected();
    }

    /**
     * Stage 12: asks for explicit confirmation and then deletes the
     * selected paper. Cancel, closing or dismissing the dialog deletes
     * nothing.
     */
    private void onDelete() {
        int paperId = getSelectedPaperId();
        if (paperId < 0) {
            showError("Please select a question paper first.");
            return;
        }
        QuestionPaper paper = null;
        for (QuestionPaper candidate : papers) {
            if (candidate.getPaperId() == paperId) {
                paper = candidate;
                break;
            }
        }
        if (paper == null) {
            showError("The selected question paper could not be found. "
                    + "Please refresh the list.");
            refreshPanel();
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("Paper #").append(paper.getPaperId()).append("\n");
        message.append("Subject: ").append(paper.getSubjectName()).append("\n");
        message.append("Total Marks: ").append(paper.getTotalMarks()).append("\n");
        message.append("Generated: ").append(paper.getGeneratedAt() == null
                ? "" : DATE_FORMAT.format(paper.getGeneratedAt())).append("\n\n");
        message.append("Deleting removes this paper from the generated history,\n")
                .append("including its stored questions and blueprint.\n")
                .append("The question bank will NOT be affected.\n\n")
                .append("This action cannot be undone.");
        Object[] options = {"Cancel", "Delete Paper"};
        int choice = JOptionPane.showOptionDialog(this, message.toString(),
                "Delete Question Paper?", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice != 1) {
            return;
        }
        deleteSelectedPaper();
    }

    /** Open and Delete are bound to the current selection. */
    private void updateActionStates() {
        boolean selected = getSelectedPaperId() >= 0;
        openButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
    }

    private void rebuildTable() {
        int selected = table.getSelectedRow();
        tableModel.setRowCount(0);
        for (QuestionPaper paper : papers) {
            tableModel.addRow(new Object[]{
                    paper.getPaperId(),
                    paper.getSubjectName(),
                    paper.getTotalMarks(),
                    paper.getGeneratedAt() == null
                            ? "" : DATE_FORMAT.format(paper.getGeneratedAt())});
        }
        emptyLabel.setVisible(tableModel.getRowCount() == 0);
        if (selected >= 0 && selected < tableModel.getRowCount()) {
            table.setRowSelectionInterval(selected, selected);
        } else if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        updateActionStates();
    }

    private void showError(String message) {
        if (initializing) {
            System.err.println("[startup] " + message);
            return;
        }
        JOptionPane.showMessageDialog(this, message, "Generated Papers",
                JOptionPane.ERROR_MESSAGE);
    }
}
