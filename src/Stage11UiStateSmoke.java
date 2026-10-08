import dao.PaperDAO;
import model.BlueprintSlot;
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
import ui.GeneratePaperPanel;
import ui.ImportPanel;
import ui.QuestionPanel;
import ui.ReviewPaperPanel;
import ui.SubjectPanel;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 11 GUI state smoke test: every screen must enable exactly the
 * actions that can currently do something, and disable the rest.
 *
 * The assertions read the real button states through the public facades on
 * the EDT, so a missing selection listener or a missing gate fails the test.
 *
 * Uses its own test subject/modules/questions in the live DB and cleans
 * everything up at the end.
 */
public class Stage11UiStateSmoke {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final PaperReviewService reviewService = new PaperReviewService();
    private static final PaperDAO paperDAO = new PaperDAO();

    private static final String subjectName = "TEST STAGE11 UI";

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;
    private static int m3 = -1;
    private static int q1 = -1;
    private static int q2 = -1;

    public static void main(String[] args) {
        preClean();
        try {
            setup();
            SwingUtilities.invokeAndWait(() -> run(Stage11UiStateSmoke::driveReviewPanel));
            SwingUtilities.invokeAndWait(() -> run(Stage11UiStateSmoke::driveQuestionPanel));
            SwingUtilities.invokeAndWait(() -> run(Stage11UiStateSmoke::driveSubjectPanel));
            SwingUtilities.invokeAndWait(() -> run(Stage11UiStateSmoke::driveImportPanel));
            SwingUtilities.invokeAndWait(() -> run(Stage11UiStateSmoke::driveBlueprintPanel));
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

    private static void run(Runnable step) {
        try {
            step.run();
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: unexpected exception in panel -> " + ex);
            ex.printStackTrace();
        }
    }

    // =====================================================================
    // Fixture: 1 subject, 3 modules, 2 questions
    // =====================================================================

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        m1 = moduleService.addModule(subjectId, "STAGE11 U1");
        m2 = moduleService.addModule(subjectId, "STAGE11 U2");
        m3 = moduleService.addModule(subjectId, "STAGE11 U3");
        q1 = addQ(m1, 2, "Easy", "UI-Q1 What is a class?");
        q2 = addQ(m2, 4, "Medium", "UI-Q2 Explain inheritance.");
        check("Setup: subject, three modules and two questions",
                subjectId > 0 && m1 > 0 && m2 > 0 && m3 > 0 && q1 > 0 && q2 > 0);
    }

    private static int addQ(int moduleId, int marks, String difficulty, String text)
            throws Exception {
        Question q = new Question();
        q.setSubjectId(subjectId);
        q.setModuleId(moduleId);
        q.setQuestionText(text);
        q.setAnswerText("Answer " + text);
        q.setMarks(marks);
        q.setDifficulty(difficulty);
        return questionService.addQuestion(q);
    }

    private static PaperReview buildReview(String secondText) {
        BlueprintSlot s1 = new BlueprintSlot(1, m1, 2, "Easy");
        s1.setModuleName("STAGE11 U1");
        BlueprintSlot s2 = new BlueprintSlot(2, m2, 4, "Medium");
        s2.setModuleName("STAGE11 U2");
        List<BlueprintSlot> slots = new ArrayList<>();
        slots.add(s1);
        slots.add(s2);

        ReviewQuestion r1 = new ReviewQuestion(1, q1, subjectId, m1, "STAGE11 U1",
                "UI-Q1 What is a class?", "Answer UI-Q1 What is a class?", 2, "Easy");
        ReviewQuestion r2 = new ReviewQuestion(2, q2, subjectId, m2, "STAGE11 U2",
                secondText, "Answer UI-Q2 Explain inheritance.", 4, "Medium");
        List<ReviewQuestion> questions = new ArrayList<>();
        questions.add(r1);
        questions.add(r2);

        return new PaperReview(subjectId, subjectName, ExamBlueprint.TYPE_SEMESTER, 6,
                slots, questions);
    }

    // =====================================================================
    // Review screen: actions follow selection, validity and finalization
    // =====================================================================

    private static void driveReviewPanel() {
        ReviewPaperPanel panel = new ReviewPaperPanel();
        check("Review: unbound screen has no review actions",
                !panel.isReviewActionEnabled());
        check("Review: unbound screen cannot be finalized", !panel.isFinalizeEnabled());
        check("Review: unbound screen cannot export",
                !panel.isExportQuestionPaperEnabled() && !panel.isExportAnswerKeyEnabled());

        panel.setReview(buildReview("UI-Q2 Explain inheritance."));
        panel.selectQuestion(1);
        check("Review: a valid paper enables its actions", panel.isReviewActionEnabled());
        check("Review: a valid paper can be finalized", panel.isFinalizeEnabled());
        check("Review: an open paper cannot be exported yet",
                !panel.isExportQuestionPaperEnabled() && !panel.isExportAnswerKeyEnabled());

        panel.setReview(buildReview(""));
        panel.selectQuestion(1);
        check("Review: an invalid paper keeps its edit actions",
                panel.isReviewActionEnabled());
        check("Review: an invalid paper cannot be finalized", !panel.isFinalizeEnabled());
        check("Review: the status reports INVALID",
                panel.getStatusText().contains("INVALID"));
    }

    // =====================================================================
    // Question bank: Edit / Delete / View Answer follow the selection
    // =====================================================================

    private static void driveQuestionPanel() {
        QuestionPanel panel = new QuestionPanel();
        panel.refreshFilters();
        check("Questions: the two fixture questions are listed",
                panel.getQuestionRowCount() >= 2);
        check("Questions: without a selection the row actions are disabled",
                !panel.isEditEnabled() && !panel.isDeleteEnabled()
                        && !panel.isViewAnswerEnabled());

        panel.selectQuestionRow(0);
        check("Questions: a selection enables Edit, Delete and View Answer",
                panel.isEditEnabled() && panel.isDeleteEnabled()
                        && panel.isViewAnswerEnabled());

        panel.selectQuestionRow(-1);
        check("Questions: clearing the selection disables them again",
                !panel.isEditEnabled() && !panel.isDeleteEnabled()
                        && !panel.isViewAnswerEnabled());

        check("Questions: clear-all starts disabled on (All Subjects)",
                !panel.isClearAllEnabled());
        panel.selectQuestionSubject(subjectId);
        check("Questions: selecting a subject enables clear-all",
                panel.isClearAllEnabled());
        panel.selectQuestionSubject(-1);
        check("Questions: back on (All Subjects) disables clear-all again",
                !panel.isClearAllEnabled());
    }

    // =====================================================================
    // Subjects & Modules: subject and module actions follow their tables
    // =====================================================================

    private static void driveSubjectPanel() {
        SubjectPanel panel = new SubjectPanel();
        check("Subjects: the subjects are listed", panel.getSubjectRowCount() >= 1);

        int row = -1;
        for (int i = 0; i < panel.getSubjectRowCount(); i++) {
            if (panel.getSubjectIdAt(i) == subjectId) {
                row = i;
            }
        }
        check("Subjects: the fixture subject appears in the list", row >= 0);
        if (row < 0) {
            return;
        }

        panel.selectSubjectRow(row);
        check("Subjects: the auto-selected subject enables Edit and Delete",
                panel.isSubjectEditEnabled() && panel.isSubjectDeleteEnabled());
        check("Subjects: module actions are disabled before a module is selected",
                !panel.isModuleEditEnabled() && !panel.isModuleDeleteEnabled());
        check("Subjects: the fixture subject shows its three modules",
                panel.getModuleRowCount() == 3);

        panel.selectModuleRow(0);
        check("Subjects: a selected module enables Edit and Delete",
                panel.isModuleEditEnabled() && panel.isModuleDeleteEnabled());

        panel.selectModuleRow(-1);
        check("Subjects: clearing the module selection disables them again",
                !panel.isModuleEditEnabled() && !panel.isModuleDeleteEnabled());
    }

    // =====================================================================
    // Import: the import action needs a subject AND a chosen file
    // =====================================================================

    private static void driveImportPanel() {
        ImportPanel panel = new ImportPanel();
        check("Import: disabled without a subject and a file", !panel.isImportEnabled());

        check("Import: clear-subject is disabled without a subject",
                !panel.isClearSubjectEnabled());
        check("Import: delete-from-file is disabled without subject and file",
                !panel.isDeleteFromFileEnabled());

        panel.selectImportSubject(subjectId);
        check("Import: still disabled while no file is chosen", !panel.isImportEnabled());
        check("Import: a subject alone enables clear-subject",
                panel.isClearSubjectEnabled());
        check("Import: a subject without a file keeps delete-from-file disabled",
                !panel.isDeleteFromFileEnabled());
    }

    // =====================================================================
    // Blueprint editor: generation, clear and pattern rows follow state
    // =====================================================================

    private static void driveBlueprintPanel() {
        GeneratePaperPanel panel = new GeneratePaperPanel();
        panel.refreshPanel();
        check("Blueprint: generation is disabled for an empty blueprint",
                !panel.isGenerateEnabled());
        check("Blueprint: clear is disabled while the blueprint is empty",
                !panel.isClearBlueprintEnabled());
        check("Blueprint: pattern row actions are disabled without a module",
                !panel.isPatternEditEnabled() && !panel.isPatternRemoveEnabled());

        panel.selectSubject(subjectId);
        panel.setExamType(ExamBlueprint.TYPE_SEMESTER);
        panel.setExamDifficulty("Easy");
        panel.setTotalMarks(6);
        panel.addModulesToSyllabus(m1, m2);
        panel.setModuleAllocation(m1, 2);
        panel.setModuleAllocation(m2, 4);
        panel.addPattern(m1, 2, 1);
        panel.addPattern(m2, 4, 1);

        check("Blueprint: the completed blueprint validates",
                panel.getValidationErrors().isEmpty());
        check("Blueprint: the preview lists both questions",
                panel.getPreviewRowCount() == 2);
        check("Blueprint: generation is enabled for a valid blueprint",
                panel.isGenerateEnabled());
        check("Blueprint: clear is enabled once the blueprint has data",
                panel.isClearBlueprintEnabled());
        check("Blueprint: pattern row actions stay disabled without a selected row",
                !panel.isPatternEditEnabled() && !panel.isPatternRemoveEnabled());

        panel.selectPatternRow(0);
        check("Blueprint: a selected pattern row enables Edit and Remove",
                panel.isPatternEditEnabled() && panel.isPatternRemoveEnabled());

        panel.selectPatternRow(-1);
        check("Blueprint: clearing the pattern selection disables them again",
                !panel.isPatternEditEnabled() && !panel.isPatternRemoveEnabled());

        panel.addModulesToSyllabus(m3);
        check("Blueprint: the third module joins the syllabus",
                panel.getExam().isModuleSelected(m3));
        try {
            moduleService.deleteModule(m3);
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: Blueprint: could not delete the third module -> "
                    + ex);
            return;
        }
        panel.refreshPanel();
        check("Blueprint: a module deleted in the database leaves the syllabus",
                !panel.getExam().isModuleSelected(m3));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

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
                                paperDAO.deletePaper(p.getPaperId());
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
                        paperDAO.deletePaper(p.getPaperId());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (subjectId > 0) {
            try {
                for (Question q : questionService.searchQuestions(
                        subjectId, null, null, null, null)) {
                    try {
                        questionService.deleteQuestion(q.getQuestionId());
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            for (int mid : new int[]{m1, m2, m3}) {
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
