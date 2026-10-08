import dao.PaperDAO;
import model.BlueprintSlot;
import model.ExamBlueprint;
import model.GenerationResult;
import model.Module;
import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.Subject;
import service.BlueprintService;
import service.ModuleService;
import service.PaperGenerator;
import service.PaperReviewService;
import service.PDFExportService;
import service.QuestionService;
import service.SubjectService;
import ui.GeneratedPapersPanel;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 12 TDD suite: deletion of a finalized question paper from the
 * Generated Question Papers panel (specification sections 15-22 and 29).
 *
 * Covers DAO deletion with cascading children, service validation
 * (invalid / nonexistent ids), source-question protection, unrelated-paper
 * protection, the "deleted paper cannot be exported" rule and the panel's
 * UI state (delete enabled only with a selection, selection cleared and
 * actions disabled after deletion, list refreshed).
 *
 * Uses its own test subject/modules/questions and two finalized fixture
 * papers in the live DB and cleans everything up.
 */
public class PaperDeletionTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final PaperReviewService reviewService = new PaperReviewService();
    private static final BlueprintService blueprintService = new BlueprintService();
    private static final PaperGenerator paperGenerator = new PaperGenerator();
    private static final PDFExportService pdfExportService = new PDFExportService();
    private static final PaperDAO paperDAO = new PaperDAO();

    private static final String SUBJECT_NAME = "TEST S12 PAPER DELETE";
    private static final String[] DIFFICULTIES = {"Easy", "Medium", "Hard"};

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;

    private static List<QuestionPaper> baseline = new ArrayList<>();
    private static int papersWithFixtures = 0;
    private static int p1 = -1;
    private static int p2 = -1;

    private interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        preClean();
        try {
            section("fixture", PaperDeletionTest::setupFixture);
            section("DAO deletion", PaperDeletionTest::runDaoChecks);
            section("service deletion", PaperDeletionTest::runServiceChecks);
            section("deleted paper export", PaperDeletionTest::runPdfChecks);
            section("panel deletion", PaperDeletionTest::driveHistoryPanel);
        } finally {
            cleanup();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================
    // Fixture: 1 subject, 2 modules, 24 questions, 2 finalized papers
    //   each paper: 12 marks = M1(4+2) + M2(4+2), 4 slots
    // =====================================================================

    private static void setupFixture() throws Exception {
        baseline = reviewService.getPapers();
        subjectId = subjectService.addSubject(SUBJECT_NAME);
        m1 = moduleService.addModule(subjectId, "DEL M1");
        m2 = moduleService.addModule(subjectId, "DEL M2");
        for (int module : new int[]{m1, m2}) {
            for (String difficulty : DIFFICULTIES) {
                for (int k = 1; k <= 2; k++) {
                    addQ(module, 4, difficulty,
                            "DEL-" + module + "-4-" + difficulty + "-" + k);
                    addQ(module, 2, difficulty,
                            "DEL-" + module + "-2-" + difficulty + "-" + k);
                }
            }
        }
        p1 = createPaper();
        p2 = createPaper();
        papersWithFixtures = reviewService.getPapers().size();
        check("Fixture: subject, 2 modules, 24 questions, 2 finalized papers",
                subjectId > 0 && m1 > 0 && m2 > 0
                        && bankQuestions().size() == 24
                        && p1 > 0 && p2 > 0 && p1 != p2
                        && papersWithFixtures == baseline.size() + 2);
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

    private static int createPaper() throws Exception {
        ExamBlueprint bp = new ExamBlueprint();
        bp.setSubjectId(subjectId);
        bp.setSubjectName(SUBJECT_NAME);
        bp.setExamType(ExamBlueprint.TYPE_SEMESTER);
        bp.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        bp.setTotalMarks(12);
        bp.selectModule(m1, "DEL M1");
        bp.setAllocation(m1, 6);
        bp.selectModule(m2, "DEL M2");
        bp.setAllocation(m2, 6);
        blueprintService.addPatternRow(bp, m1, 4, 1);
        blueprintService.addPatternRow(bp, m1, 2, 1);
        blueprintService.addPatternRow(bp, m2, 4, 1);
        blueprintService.addPatternRow(bp, m2, 2, 1);
        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        GenerationResult result = paperGenerator.generatePaper(
                subjectId, SUBJECT_NAME, ExamBlueprint.TYPE_SEMESTER,
                ExamBlueprint.EXAM_DIFFICULTY_MEDIUM, 12, slots);
        if (!result.isSuccess()) {
            throw new IllegalStateException(
                    "fixture generation failed: " + result.getFailureMessage());
        }
        PaperReview review = reviewService.openReview(result.getPaper(), slots);
        return reviewService.finalizePaper(review);
    }

    // =====================================================================
    // Section: DAO level
    // =====================================================================

    private static void runDaoChecks() throws Exception {
        check("Fixture paper stored 4 question snapshots",
                paperDAO.countPaperQuestions(p1) == 4);
        check("Second fixture paper stored 4 question snapshots",
                paperDAO.countPaperQuestions(p2) == 4);

        paperDAO.deletePaper(99999999);
        check("Deleting a nonexistent paper at DAO level is a clean no-op",
                reviewService.getPapers().size() == papersWithFixtures);
    }

    // =====================================================================
    // Section: service level
    // =====================================================================

    private static void runServiceChecks() throws Exception {
        int questionsBefore = bankQuestions().size();

        expectIAE("Paper id 0 is rejected", () -> reviewService.deletePaper(0));
        expectIAE("Negative paper id is rejected",
                () -> reviewService.deletePaper(-3));
        expectIAE("Nonexistent paper id is rejected",
                () -> reviewService.deletePaper(987654321));
        check("Rejected deletions changed nothing",
                reviewService.getPapers().size() == papersWithFixtures
                        && reviewService.loadPaper(p1) != null);

        reviewService.deletePaper(p1);
        check("Deleted paper no longer loads", reviewService.loadPaper(p1) == null);
        check("Deleted paper snapshots are removed",
                paperDAO.countPaperQuestions(p1) == 0);

        PaperReview other = reviewService.loadPaper(p2);
        check("Unrelated paper still loads read-only",
                other != null && other.isFinalized()
                        && other.getQuestions().size() == 4);
        check("Unrelated paper snapshots remain",
                paperDAO.countPaperQuestions(p2) == 4);
        check("Question bank untouched by paper deletion",
                bankQuestions().size() == questionsBefore);
        check("Exactly the deleted fixture paper disappeared",
                reviewService.getPapers().size() == papersWithFixtures - 1);
        check("Unrelated papers remain",
                allPresent(reviewService.getPapers(), baseline));

        if (other != null) {
            expectISE("Finalized paper stays read-only until deleted",
                    () -> reviewService.editQuestionText(other, 1, "x", "y"));
        } else {
            check("Finalized paper stays read-only until deleted", false);
        }
    }

    // =====================================================================
    // Section: a deleted paper cannot be exported (spec section 29, PDF)
    // =====================================================================

    private static void runPdfChecks() {
        File target = new File(System.getProperty("java.io.tmpdir"),
                "s12-deleted-" + System.nanoTime() + ".pdf");
        try {
            expectIAE("Deleted paper cannot be exported",
                    () -> pdfExportService.exportQuestionPaper(p1, target));
            check("No PDF was written for the deleted paper", !target.exists());
        } finally {
            if (target.exists()) {
                target.delete();
            }
        }
    }

    // =====================================================================
    // Section: GeneratedPapersPanel delete action (on the EDT)
    // =====================================================================

    private static void driveHistoryPanel() throws Exception {
        int questionsBefore = bankQuestions().size();
        SwingUtilities.invokeAndWait(() -> {
            try {
                GeneratedPapersPanel history = new GeneratedPapersPanel();
                history.refreshPanel();
                int beforeCount = history.getRowCount();
                check("History lists the remaining fixture paper",
                        beforeCount == baseline.size() + 1);

                int row = findRow(history, p2);
                check("Fixture paper row is present", row >= 0);
                if (row >= 0) {
                    history.selectRow(row);
                    check("Open enabled with a selection", history.isOpenEnabled());
                    check("Delete enabled with a selection", history.isDeleteEnabled());
                    check("Delete action is labelled \"Delete Question Paper\"",
                            "Delete Question Paper".equals(history.getDeleteActionText()));

                    history.clearSelection();
                    check("Open disabled without a selection",
                            !history.isOpenEnabled());
                    check("Delete disabled without a selection",
                            !history.isDeleteEnabled());

                    history.selectRow(row);
                    boolean ok = history.deleteSelectedPaper();
                    check("Panel deletion succeeds", ok);
                    check("The row was removed from the list",
                            history.getRowCount() == beforeCount - 1);
                    check("No stale selection remains",
                            history.getSelectedPaperId() == -1);
                    check("Open disabled after deletion",
                            !history.isOpenEnabled());
                    check("Delete disabled after deletion",
                            !history.isDeleteEnabled());
                    check("Deleted paper is gone from the database",
                            safeLoad(p2) == null);
                    check("Question bank untouched by panel deletion",
                            bankQuestions().size() == questionsBefore);
                    check("Unrelated papers remain after panel deletion",
                            allPresent(bankPapers(), baseline));
                }
            } catch (Exception ex) {
                failed++;
                System.out.println("FAIL: panel deletion section threw -> " + ex);
                ex.printStackTrace();
            }
        });
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static int findRow(GeneratedPapersPanel history, int paperId) {
        int found = -1;
        for (int i = 0; i < history.getRowCount(); i++) {
            history.selectRow(i);
            if (history.getSelectedPaperId() == paperId) {
                found = i;
                break;
            }
        }
        return found;
    }

    private static boolean allPresent(List<QuestionPaper> current,
                                      List<QuestionPaper> required) {
        for (QuestionPaper paper : required) {
            boolean present = false;
            for (QuestionPaper candidate : current) {
                if (candidate.getPaperId() == paper.getPaperId()) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                return false;
            }
        }
        return true;
    }

    private static PaperReview safeLoad(int paperId) {
        try {
            return reviewService.loadPaper(paperId);
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<Question> bankQuestions() throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null);
    }

    private static List<QuestionPaper> bankPapers() throws Exception {
        return reviewService.getPapers();
    }

    private static void section(String name, Action action) {
        try {
            action.run();
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL: section \"" + name + "\" threw -> " + t);
            t.printStackTrace();
        }
    }

    private static void expectIAE(String name, Action action) {
        try {
            action.run();
            check(name, false);
        } catch (IllegalArgumentException expected) {
            check(name, true);
        } catch (Throwable t) {
            check(name, false);
            System.out.println("  unexpected -> " + t);
        }
    }

    private static void expectISE(String name, Action action) {
        try {
            action.run();
            check(name, false);
        } catch (IllegalStateException expected) {
            check(name, true);
        } catch (Throwable t) {
            check(name, false);
            System.out.println("  unexpected -> " + t);
        }
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

    // =====================================================================
    // Cleanup
    // =====================================================================

    private static void preClean() {
        try {
            for (Subject s : subjectService.getAllSubjects()) {
                if (!SUBJECT_NAME.equals(s.getSubjectName())) {
                    continue;
                }
                removeSubject(s.getSubjectId());
            }
        } catch (Exception ignored) {
        }
    }

    private static void cleanup() {
        if (subjectId > 0) {
            removeSubject(subjectId);
        }
        System.out.println("CLEANED");
    }

    private static void removeSubject(int sid) {
        try {
            for (QuestionPaper p : reviewService.getPapers()) {
                if (p.getSubjectId() == sid) {
                    try {
                        paperDAO.deletePaper(p.getPaperId());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            for (Question q : questionService.searchQuestions(sid, null, null, null, null)) {
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
}
