package ui;

import model.Question;
import service.QuestionService;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared preview / confirmation / result dialog for bulk question deletion.
 * Every destructive bulk flow (Import screen and Question Bank screen)
 * shows the same warning: what will be deleted, what is kept because a
 * finalized paper pins it, and that the action cannot be undone.
 * The dialog only displays data - all rules live in QuestionService.
 */
public final class QuestionDeleteConfirm {

    private static final int MAX_TEXT = 90;

    private QuestionDeleteConfirm() {
    }

    /**
     * Shows the preview list with an explicit warning and asks for
     * confirmation. Returns true only when the user picks
     * "Delete N Questions".
     */
    public static boolean confirmDeletion(Component parent, String scopeLine,
            QuestionService.BulkDeletePlan plan, QuestionService questionService) {
        if (plan.getDeletable().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(scopeLine).append("\n\nNo matching questions to delete.");
            if (!plan.getKept().isEmpty()) {
                sb.append('\n').append(plan.getKept().size())
                        .append(" question(s) are used by finalized papers and will be kept.");
            }
            JOptionPane.showMessageDialog(parent, sb.toString(),
                    "Delete Questions", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        JTextArea area = new JTextArea(
                buildPreviewText(scopeLine, plan, questionService), 20, 68);
        area.setEditable(false);
        Object[] options = {"Delete " + plan.getDeletable().size() + " Questions", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent, new JScrollPane(area),
                "Confirm Deletion", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return choice == 0;
    }

    /** Reports what a bulk delete did (deleted vs. kept for papers). */
    public static void showResult(Component parent,
            QuestionService.BulkDeleteResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Deleted: ").append(result.getDeleted()).append(" question(s).\n");
        if (!result.getKept().isEmpty()) {
            sb.append("Kept: ").append(result.getKept().size())
                    .append(" question(s) used by finalized papers:\n");
            for (String paper : result.getKeptPapers()) {
                sb.append("  ").append(paper).append('\n');
            }
            sb.append("Finalized papers were NOT modified.\n");
        }
        JTextArea area = new JTextArea(sb.toString(), 10, 56);
        area.setEditable(false);
        JOptionPane.showMessageDialog(parent, new JScrollPane(area),
                "Delete Questions", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String buildPreviewText(String scopeLine,
            QuestionService.BulkDeletePlan plan, QuestionService questionService) {
        StringBuilder sb = new StringBuilder();
        sb.append(scopeLine).append("\n\n");
        sb.append(plan.getDeletable().size())
                .append(" question(s) will be PERMANENTLY deleted.\n\n");

        List<Question> questions = new ArrayList<>();
        Map<String, Integer> perModule = new LinkedHashMap<>();
        for (int id : plan.getDeletable()) {
            try {
                Question q = questionService.getQuestionById(id);
                if (q != null) {
                    questions.add(q);
                    String module = q.getModuleName() == null ? "?" : q.getModuleName();
                    perModule.merge(module, 1, Integer::sum);
                }
            } catch (Exception ignored) {
                // a question that vanished since the preview: skip its line
            }
        }
        if (!perModule.isEmpty()) {
            sb.append("Per module: ");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : perModule.entrySet()) {
                parts.add(entry.getKey() + ": " + entry.getValue());
            }
            sb.append(String.join(", ", parts)).append("\n\n");
        }
        for (Question q : questions) {
            sb.append('[').append(q.getQuestionId()).append("] ")
                    .append(q.getModuleName()).append(" (").append(q.getMarks())
                    .append(", ").append(q.getDifficulty()).append(") ")
                    .append(truncate(q.getQuestionText())).append('\n');
        }

        sb.append("\nWARNING: this cannot be undone.\n");
        if (!plan.getKept().isEmpty()) {
            sb.append('\n').append(plan.getKept().size())
                    .append(" question(s) are used by finalized papers and will be KEPT:\n");
            for (String paper : plan.getKeptPapers()) {
                sb.append("  ").append(paper).append('\n');
            }
            sb.append("Finalized papers are NOT modified.\n");
        }
        return sb.toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "...";
    }
}
