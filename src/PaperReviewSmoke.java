import model.ExamBlueprint;
import model.Module;
import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.ReviewQuestion;
import model.Subject;
import service.ModuleService;
import service.PaperReviewService;
import service.QuestionService;
import service.SubjectService;
import ui.GeneratedPapersPanel;
import ui.ReviewPaperPanel;

import javax.swing.SwingUtilities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 9 GUI smoke test: drives the Generated Paper Review screen and the
 * Generated Papers history through their public facades (the same code
 * paths the button handlers use) on the EDT.
 *
 * Uses its own test subject/modules/questions in the live DB and cleans
 * everything up (including the finalized paper) at the end.
 */
public class PaperReviewSmoke {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final PaperReviewService reviewService = new PaperReviewService();

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;
    private static int paperId = -1;
    private static final String subjectName = "TEST REVIEW SMOKE";

    public static void main(String[] args) {
        preClean();
        try {
            setup();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    driveReviewScreen();
                } catch (Exception ex) {
                    failed++;
                    System.out.println("FAIL: unexpected exception in review screen -> " + ex);
                    ex.printStackTrace();
                }
            });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    driveHistoryScreen();
                } catch (Exception ex) {
                    failed++;
                    System.out.println("FAIL: unexpected exception in history screen -> " + ex);
                    ex.printStackTrace();
                }
            });
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: unexpected exception -> " + ex);
            ex.printStackTrace();
        } finally {
            cleanup();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================
    // Fixture: 1 subject, 2 modules, 10 questions
    //   M1: 2/Easy x3, 4/Medium x1
    //   M2: 4/Medium x4, 6/Hard x2
    //   blueprint total 16 = 4 slots (2/Easy, 4/Med, 4/Med, 6/Hard)
    // =====================================================================

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        m1 = moduleService.addModule(subjectId, "SMOKE M1");
        m2 = moduleService.addModule(subjectId, "SMOKE M2");
        for (int i = 1; i <= 3; i++) {
            addQ(m1, 2, "Easy", "SMOKE-E" + i);
        }
        addQ(m1, 4, "Medium", "SMOKE-B1");
        for (int i = 1; i <= 4; i++) {
            addQ(m2, 4, "Medium", "SMOKE-M" + i);
        }
        addQ(m2, 6, "Hard", "SMOKE-H1");
        addQ(m2, 6, "Hard", "SMOKE-H2");
        check("Setup: subject + 2 modules + 10 questions",
                subjectId > 0 && m1 > 0 && m2 > 0
                        && questionService.searchQuestions(
                        subjectId, null, null, null, null).size() == 10);
    }

    private static void addQ(int moduleId, int marks, String difficulty, String text)
            throws Exception {
        Question q = new Question();
        q.setSubjectId(subjectId);
        q.setModuleId(moduleId);
        q.setQuestionText(text);
        q.setAnswerText("Answer " + text);
        q.setMarks(marks);
        q.setDifficulty(difficulty);
        questionService.addQuestion(q);
    }

    // =====================================================================
    // Review screen (edit / replace / regenerate / bank update / finalize)
    // =====================================================================

    private static void driveReviewScreen() throws Exception {
        ui.GeneratePaperPanel blueprint = new ui.GeneratePaperPanel();
        blueprint.refreshPanel();
        blueprint.selectSubject(subjectId);
        blueprint.setExamType(ExamBlueprint.TYPE_SEMESTER);
        blueprint.setExamDifficulty("Medium");
        blueprint.setTotalMarks(16);
        blueprint.addModulesToSyllabus(m1, m2);
        blueprint.setModuleAllocation(m1, 6);
        blueprint.setModuleAllocation(m2, 10);
        blueprint.addPattern(m1, 2, 1);
        blueprint.addPattern(m1, 4, 1);
        blueprint.addPattern(m2, 4, 1);
        blueprint.addPattern(m2, 6, 1);
        check("Blueprint built for the smoke subject", blueprint.getValidationErrors().isEmpty());

        model.GenerationResult result = blueprint.generatePaper();
        check("Generation succeeds", result.isSuccess() && result.getPaper() != null);
        if (!result.isSuccess()) {
            return;
        }

        PaperReview review = reviewService.openReview(result.getPaper(), blueprint.getSlots());
        ReviewPaperPanel panel = new ReviewPaperPanel();
        panel.setReview(review);
        check("Review screen binds the paper (4 rows)",
                panel.getRowCount() == 4 && !panel.isFinalized());
        check("Status shows VALID", panel.getStatusText().contains("VALID"));
        check("Validation reports no problems", panel.getValidationErrors().isEmpty());

        panel.selectQuestion(2);
        check("Selection follows the blueprint position", panel.getSelectedSequenceNo() == 2);

        Map<Integer, String> bankBefore = snapshotBank();

        panel.editSelected("SMOKE-EDITED-Q", "SMOKE-EDITED-A");
        check("Edit for This Paper changed the review copy",
                review.getQuestionBySequence(2).getQuestionText().equals("SMOKE-EDITED-Q")
                        && review.getQuestionBySequence(2).getAnswerText()
                        .equals("SMOKE-EDITED-A"));
        check("Edit for This Paper left the bank untouched",
                bankBefore.equals(snapshotBank()));

        panel.selectQuestion(3);
        List<Question> candidates = panel.getReplacementCandidates();
        check("Replacement candidates offered for slot 3", candidates.size() == 3);
        panel.replaceSelected(candidates.get(0).getQuestionId());
        check("Replacement installed with sequence kept",
                review.getQuestionBySequence(3).getSequenceNo() == 3
                        && review.getQuestionBySequence(3).getQuestionId()
                        == candidates.get(0).getQuestionId());
        check("Replacement left the bank untouched", bankBefore.equals(snapshotBank()));

        int hardBefore = review.getQuestionBySequence(4).getQuestionId();
        panel.selectQuestion(4);
        panel.regenerateSelected();
        check("Regeneration swapped in an unused question",
                review.getQuestionBySequence(4).getQuestionId() != hardBefore
                        && review.getQuestionBySequence(4).getMarks() == 6);
        check("Regeneration left the bank untouched", bankBefore.equals(snapshotBank()));
        check("Review still valid after all edits", panel.getValidationErrors().isEmpty());

        panel.selectQuestion(2);
        panel.updateBankSelected();
        Map<Integer, String> bankAfter = snapshotBank();
        check("Update Question Bank wrote exactly the edited question",
                bankBefore.size() == bankAfter.size()
                        && !bankBefore.get(review.getQuestionBySequence(2).getQuestionId())
                        .equals(bankAfter.get(review.getQuestionBySequence(2).getQuestionId()))
                        && bankAfter.get(review.getQuestionBySequence(2).getQuestionId())
                        .contains("SMOKE-EDITED-Q"));

        paperId = panel.finalizeCurrentPaper();
        check("Finalize stored the paper", paperId > 0 && panel.isFinalized()
                && panel.getPaperId() == paperId);
        check("Status shows FINALIZED with the paper id",
                panel.getStatusText().contains("FINALIZED")
                        && panel.getStatusText().contains(String.valueOf(paperId)));

        boolean readOnly = false;
        try {
            panel.editSelected("x", "y");
        } catch (IllegalStateException ex) {
            readOnly = true;
        }
        check("Finalized review is read-only through the facade", readOnly);

        PaperReview loaded = reviewService.loadPaper(paperId);
        check("Finalized paper reloads read-only with 4 questions",
                loaded != null && loaded.isFinalized()
                        && loaded.getQuestions().size() == 4);
        if (loaded != null) {
            boolean same = true;
            for (int i = 0; i < 4; i++) {
                ReviewQuestion lq = loaded.getQuestionBySequence(i + 1);
                ReviewQuestion rq = review.getQuestionBySequence(i + 1);
                if (lq == null || rq == null
                        || lq.getQuestionId() != rq.getQuestionId()
                        || !lq.getQuestionText().equals(rq.getQuestionText())) {
                    same = false;
                }
            }
            check("Reload reproduces every stored question", same);
        } else {
            check("Reload reproduces every stored question", false);
        }
    }

    // =====================================================================
    // History screen
    // =====================================================================

    private static void driveHistoryScreen() throws Exception {
        GeneratedPapersPanel history = new GeneratedPapersPanel();
        check("History screen lists at least our paper", history.getRowCount() >= 1);

        int row = -1;
        for (int i = 1; i <= history.getRowCount(); i++) {
            history.selectRow(i - 1);
            if (history.getSelectedPaperId() == paperId) {
                row = i - 1;
            }
        }
        check("Our finalized paper appears in the history", row >= 0);

        history.selectRow(row);
        check("Selection reports our paper id",
                history.getSelectedPaperId() == paperId);
        PaperReview loaded = history.loadSelected();
        check("History opens the paper as a read-only review",
                loaded != null && loaded.isFinalized()
                        && loaded.getSubjectName().equals(subjectName));
        check("Reopened paper stays read-only", loaded != null
                && loaded.getQuestions().size() == 4);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static Map<Integer, String> snapshotBank() throws Exception {
        Map<Integer, String> snapshot = new LinkedHashMap<>();
        for (Question q : questionService.searchQuestions(subjectId, null, null, null, null)) {
            snapshot.put(q.getQuestionId(), q.getQuestionText() + "|" + q.getAnswerText());
        }
        return snapshot;
    }

    private static void preClean() {
        try {
            for (Subject s : subjectService.getAllSubjects()) {
                if (!subjectName.equals(s.getSubjectName())) {
                    continue;
                }
                int sid = s.getSubjectId();
                try {
                    for (QuestionPaper p : reviewService.getPapers()) {
                        if (p.getSubjectId() == sid) {
                            try {
                                new dao.PaperDAO().deletePaper(p.getPaperId());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    for (Question q : questionService.searchQuestions(
                            sid, null, null, null, null)) {
                        try {
                            questionService.deleteQuestion(q.getQuestionId());
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    for (Module m : moduleService.getModulesBySubject(sid)) {
                        try {
                            moduleService.deleteModule(m.getModuleId());
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    subjectService.deleteSubject(sid);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void cleanup() {
        try {
            for (QuestionPaper p : reviewService.getPapers()) {
                if (p.getSubjectId() == subjectId) {
                    try {
                        new dao.PaperDAO().deletePaper(p.getPaperId());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (subjectId <= 0) {
            return;
        }
        try {
            for (Question q : questionService.searchQuestions(subjectId, null, null, null, null)) {
                try {
                    questionService.deleteQuestion(q.getQuestionId());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        for (int mid : new int[]{m1, m2}) {
            if (mid > 0) {
                try {
                    moduleService.deleteModule(mid);
                } catch (Exception ignored) {
                }
            }
        }
        try {
            subjectService.deleteSubject(subjectId);
        } catch (Exception ignored) {
        }
        System.out.println("CLEANED");
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }
}
