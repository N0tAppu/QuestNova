package ui;

import model.PaperReview;
import model.QuestionPaper;
import service.PaperReviewService;

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
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
 *
 * Presentation follows the Quest reference (DESIGN.md section v5): the
 * refresh action lives in the page header, the table sits in a card on the
 * left and the selected paper's details in a card on the right. Double
 * clicking a row opens the paper.
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
    private final JButton openButton = Ui.primary("Open paper");
    private final JButton deleteButton = Ui.danger("Delete Question Paper");
    private final JButton refreshButton = Ui.plain("Refresh");
    private final JLabel emptyLabel = Ui.muted("Select a paper to see details.");

    // --- detail card (built once, filled on selection) ---
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailPane = new JPanel(detailCards);
    private final JLabel detailHeading = Ui.bold("");
    private final JLabel detailSubject = detailValue();
    private final JLabel detailMarks = detailValue();
    private final JLabel detailQuestions = detailValue();
    private final JLabel detailGenerated = detailValue();

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm");

    /** Suppresses error dialogs while the constructor loads initial data. */
    private boolean initializing = true;

    public GeneratedPapersPanel() {
        setLayout(new BorderLayout(Theme.S, Theme.S));
        setOpaque(false);

        add(Ui.pageHeader("Generated papers", "Double-click a paper to open it.",
                refreshButton), BorderLayout.NORTH);

        table.getAccessibleContext().setAccessibleName("Generated papers");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Fixed widths + horizontal scroll so no column collapses to "..."
        // when the table card sits next to the 340px detail card.
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setMinWidth(84);
        table.getColumnModel().getColumn(0).setPreferredWidth(84);
        table.getColumnModel().getColumn(1).setMinWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setMinWidth(104);
        table.getColumnModel().getColumn(2).setPreferredWidth(104);
        table.getColumnModel().getColumn(3).setMinWidth(124);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    onOpen();
                }
            }
        });

        openButton.addActionListener(e -> onOpen());
        deleteButton.addActionListener(e -> onDelete());
        refreshButton.addActionListener(e -> refreshPanel());

        JPanel tableCard = Ui.bareCard();
        tableCard.add(Ui.table(table), BorderLayout.CENTER);

        detailPane.setOpaque(false);
        detailPane.add(buildEmptyView(), "empty");
        detailPane.add(buildDetailView(), "detail");
        JPanel detailCard = Ui.bareCard();
        detailCard.setPreferredSize(new Dimension(340, 0));
        detailCard.add(detailPane, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(Theme.S, 0));
        body.setOpaque(false);
        body.add(tableCard, BorderLayout.CENTER);
        body.add(detailCard, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);

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

    /** Selection drives the action states and the detail card together. */
    private void onSelectionChanged() {
        updateActionStates();
        updateDetail();
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
        if (selected >= 0 && selected < tableModel.getRowCount()) {
            table.setRowSelectionInterval(selected, selected);
        } else if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        onSelectionChanged();
    }

    // --- detail card ----------------------------------------------------

    /** The empty state of the detail card. */
    private JPanel buildEmptyView() {
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        empty.setBorder(new EmptyBorder(Theme.XL, Theme.L, Theme.XL, Theme.L));
        empty.add(emptyLabel, BorderLayout.CENTER);
        return empty;
    }

    /** Subject / total marks / questions / generated, plus the actions. */
    private JPanel buildDetailView() {
        JPanel list = new JPanel(new GridBagLayout());
        list.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(0, 0, Theme.S, 0);
        // GridBagConstraints.gridy defaults to RELATIVE (-1); pin the first
        // row or the whole grid shifts up and the top rows overlap.
        g.gridy = 0;
        addDetailRow(list, g, "Subject", detailSubject);
        addDetailRow(list, g, "Total marks", detailMarks);
        addDetailRow(list, g, "Questions", detailQuestions);
        addDetailRow(list, g, "Generated", detailGenerated);

        JPanel top = new JPanel(new BorderLayout(0, Theme.S));
        top.setOpaque(false);
        top.add(detailHeading, BorderLayout.NORTH);
        top.add(list, BorderLayout.CENTER);

        JPanel view = new JPanel(new BorderLayout(0, Theme.M));
        view.setOpaque(false);
        view.setBorder(new EmptyBorder(Theme.L, Theme.L, Theme.L, Theme.L));
        view.add(top, BorderLayout.NORTH);
        view.add(Ui.leftRow(openButton, deleteButton), BorderLayout.SOUTH);
        return view;
    }

    private static void addDetailRow(JPanel list, GridBagConstraints g,
            String label, JLabel value) {
        g.gridx = 0;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        // Right inset on the label reserves the gap before the value column.
        g.insets = new Insets(0, 0, Theme.S, Theme.S);
        list.add(Ui.muted(label), g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 0, 0);
        list.add(value, g);
        g.gridy++;
    }

    private static JLabel detailValue() {
        JLabel value = new JLabel();
        Ui.fg(value, "INK");
        return value;
    }

    /** Fills the detail card for the current selection, or shows the empty state. */
    private void updateDetail() {
        QuestionPaper paper = selectedPaper();
        if (paper == null) {
            emptyLabel.setText("Select a paper to see details.");
            detailCards.show(detailPane, "empty");
            return;
        }
        detailHeading.setText("Paper " + paper.getPaperId());
        detailSubject.setText(paper.getSubjectName());
        detailMarks.setText(String.valueOf(paper.getTotalMarks()));
        detailQuestions.setText(questionCount(paper.getPaperId()));
        detailGenerated.setText(paper.getGeneratedAt() == null
                ? "" : DATE_FORMAT.format(paper.getGeneratedAt()));
        detailCards.show(detailPane, "detail");
    }

    private QuestionPaper selectedPaper() {
        int paperId = getSelectedPaperId();
        if (paperId < 0) {
            return null;
        }
        for (QuestionPaper candidate : papers) {
            if (candidate.getPaperId() == paperId) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The history row carries no question count, so it is read through the
     * existing review service. Read-only: a detail row never opens a dialog.
     */
    private String questionCount(int paperId) {
        try {
            PaperReview review = reviewService.loadPaper(paperId);
            if (review != null && review.getQuestions() != null) {
                return String.valueOf(review.getQuestions().size());
            }
        } catch (SQLException ex) {
            // presentation only - the row simply stays empty
        }
        return "\u2014";
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
