import model.BlueprintSlot;
import model.ExamBlueprint;
import model.FeasibilityResult;
import model.GeneratedQuestion;
import model.GenerationResult;
import model.Module;
import model.Question;
import model.Subject;
import service.ModuleService;
import service.QuestionService;
import service.SubjectService;
import ui.GeneratePaperPanel;

import javax.swing.SwingUtilities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 8 GUI smoke test: drives the Examination Blueprint panel facade
 * through the full workflow
 *   blueprint -> structural validation -> availability check -> generation
 * for a FEASIBLE case and an INFEASIBLE case.
 * Uses its own test subject/modules/questions and cleans them up.
 */
public class PaperGenerationSmoke {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;

    public static void main(String[] args) {
        try {
            setup();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    feasibleFlow();
                    infeasibleFlow();
                } catch (Exception ex) {
                    failed++;
                    System.out.println("FAIL: unexpected exception -> " + ex);
                    ex.printStackTrace();
                }
            });
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: setup/EDT failure -> " + ex);
            ex.printStackTrace();
        } finally {
            cleanup();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject("TEST GEN SMOKE");
        m1 = moduleService.addModule(subjectId, "Smoke Module 1");
        m2 = moduleService.addModule(subjectId, "Smoke Module 2");
        for (int i = 1; i <= 4; i++) {
            addQ(m1, 2, "Easy", "SMOKE-E" + i);
        }
        for (int i = 1; i <= 3; i++) {
            addQ(m2, 4, "Medium", "SMOKE-M" + i);
        }
        check("Setup: test subject + 2 modules + 7 questions",
                subjectId > 0 && m1 > 0 && m2 > 0
                        && questionService.searchQuestions(
                                subjectId, null, null, null, null).size() == 7);
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
    // Feasible flow: blueprint -> validate -> availability -> generate
    // =====================================================================

    private static void feasibleFlow() throws Exception {
        GeneratePaperPanel panel = new GeneratePaperPanel();
        panel.refreshPanel();
        panel.selectSubject(subjectId);
        panel.setExamType(ExamBlueprint.TYPE_SEMESTER);
        panel.setExamDifficulty("Medium");
        panel.setTotalMarks(12);
        panel.addModulesToSyllabus(m1, m2);
        panel.setModuleAllocation(m1, 4);
        panel.setModuleAllocation(m2, 8);
        panel.addPattern(m1, 2, 2);
        panel.addPattern(m2, 4, 2);

        check("1: blueprint structurally valid", panel.getValidationErrors().isEmpty());

        FeasibilityResult f = panel.checkAvailability();
        check("2-5: availability check reports FEASIBLE", f.isFeasible());
        check("5: availability summary lists both patterns",
                f.getRequirements().size() == 2
                        && f.getSummaryReport().contains("Smoke Module 1")
                        && f.getSummaryReport().contains("Smoke Module 2"));

        GenerationResult g = panel.generatePaper();
        check("6: paper generated", g.isSuccess() && g.getPaper() != null);
        if (!g.isSuccess()) {
            return;
        }

        List<BlueprintSlot> slots = panel.getSlots();
        List<GeneratedQuestion> questions = g.getPaper().getQuestions();

        check("7: all questions appear (4 of 4)", questions.size() == 4
                && questions.size() == slots.size());
        check("8: module/marks/difficulty correspond to the blueprint", matches(slots, questions));
        check("9: numbering/order follows the blueprint sequence", sequenceOk(slots, questions));
        check("10: total marks = 12", g.getPaper().getTotalMarks() == 12
                && sumMarks(questions) == 12);
        check("generated paper shows subject", g.getPaper().getSubjectName()
                .equals("TEST GEN SMOKE"));

        Set<Integer> ids = new HashSet<>();
        for (GeneratedQuestion q : questions) {
            ids.add(q.getQuestionId());
        }
        check("generated question ids are unique", ids.size() == questions.size());
    }

    // =====================================================================
    // Infeasible flow: valid blueprint, insufficient question bank
    // =====================================================================

    private static void infeasibleFlow() throws Exception {
        GeneratePaperPanel panel = new GeneratePaperPanel();
        panel.refreshPanel();
        panel.selectSubject(subjectId);
        panel.setExamType(ExamBlueprint.TYPE_CAT);
        panel.setExamDifficulty("Medium");
        panel.setTotalMarks(28);
        panel.addModulesToSyllabus(m1, m2);
        panel.setModuleAllocation(m1, 12);
        panel.setModuleAllocation(m2, 16);
        panel.addPattern(m1, 2, 6);
        panel.addPattern(m2, 4, 4);

        check("11: infeasible blueprint still structurally valid",
                panel.getValidationErrors().isEmpty());

        FeasibilityResult f = panel.checkAvailability();
        check("11: availability check reports NOT FEASIBLE", !f.isFeasible());
        check("13: BOTH shortages shown (2 Easy shortage + 1 Medium shortage)",
                f.getShortages().size() == 2
                        && f.getFailureReport().contains("Smoke Module 1")
                        && f.getFailureReport().contains("Smoke Module 2")
                        && f.getFailureReport().contains("Required: 6")
                        && f.getFailureReport().contains("Available: 4")
                        && f.getFailureReport().contains("Shortage: 2")
                        && f.getFailureReport().contains("Required: 4")
                        && f.getFailureReport().contains("Available: 3")
                        && f.getFailureReport().contains("Shortage: 1"));

        GenerationResult g = panel.generatePaper();
        check("12: generation refused", !g.isSuccess());
        check("12: NO partial paper produced", g.getPaper() == null);
        check("12: failure message shows every shortage",
                g.getFailureMessage().contains("Smoke Module 1")
                        && g.getFailureMessage().contains("Smoke Module 2"));

        check("question bank untouched by failed generation",
                questionService.searchQuestions(subjectId, null, null, null, null).size() == 7);
    }

    // =====================================================================
    // Property helpers
    // =====================================================================

    private static boolean matches(List<BlueprintSlot> slots, List<GeneratedQuestion> questions) {
        for (int i = 0; i < slots.size(); i++) {
            BlueprintSlot s = slots.get(i);
            GeneratedQuestion q = questions.get(i);
            if (s.getModuleId() != q.getModuleId()
                    || s.getMarks() != q.getMarks()
                    || !s.getDifficulty().equals(q.getDifficulty())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sequenceOk(List<BlueprintSlot> slots,
                                      List<GeneratedQuestion> questions) {
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getSequenceNo() != i + 1
                    || questions.get(i).getSequenceNo() != slots.get(i).getSequenceNo()) {
                return false;
            }
        }
        return true;
    }

    private static int sumMarks(List<GeneratedQuestion> questions) {
        int sum = 0;
        for (GeneratedQuestion q : questions) {
            sum += q.getMarks();
        }
        return sum;
    }

    private static void cleanup() {
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
