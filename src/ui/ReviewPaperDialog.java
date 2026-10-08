package ui;

import model.PaperReview;

import javax.swing.JDialog;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Window;

/**
 * Modal host for the Generated Paper Review screen (Stage 9).
 * Opens right after generation and also for a finalized paper from the
 * history, where the same screen appears read-only.
 */
public class ReviewPaperDialog extends JDialog {

    private final ReviewPaperPanel panel = new ReviewPaperPanel();

    private ReviewPaperDialog(Window owner, PaperReview review) {
        super(owner, "Review Generated Paper", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        panel.setReview(review);
        panel.setCloseAction(this::dispose);
        setContentPane(panel);
        setPreferredSize(new Dimension(980, 700));
        setMinimumSize(new Dimension(980, 700));
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog modally over the given window. */
    public static void show(Window owner, PaperReview review) {
        ReviewPaperDialog dialog = new ReviewPaperDialog(owner, review);
        dialog.setVisible(true);
    }
}
