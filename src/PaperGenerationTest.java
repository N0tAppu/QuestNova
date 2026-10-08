import model.BlueprintSlot;
import model.ExamBlueprint;
import model.FeasibilityResult;
import model.GeneratedPaper;
import model.GenerationResult;
import model.Question;
import service.BlueprintService;
import service.BlueprintValidator;
import service.ModuleService;
import service.PaperGenerator;
import service.QuestionService;
import service.SubjectService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 8 test: blueprint feasibility validation + paper generation.
 *
 * Uses a test subject/modules/questions in the live DB (cleaned up in the
 * finally block) and tests PROPERTIES of the random selection, never
 * specific question ids.
 */
public class PaperGenerationTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final BlueprintService blueprintService = new BlueprintService();
    private static final BlueprintValidator validator = new BlueprintValidator();
    private static final PaperGenerator generator = new PaperGenerator();
    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;
    private static int m3 = -1;
    private static String subjectName = "TEST PAPER GEN";

    public static void main(String[] args) {
        try {
            setup();
            scenarioA();
            scenarioB();
            scenarioC();
            scenarioD();
            scenarioE();
            scenarioF();
            scenarioG();
            scenarioH();
            scenarioIAndJ();
            scenarioK();
            scenarioL();
            emptyBlueprintGuard();
        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            cleanup();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================
    // Fixture: one subject, 3 modules, 15 questions with fixed distributions
    //   M1: 2/Easy x4, 4/Medium x3, 2/Hard x2
    //   M2: 4/Medium x2, 6/Hard x2
    //   M3: 2/Easy x2
    // =====================================================================

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        m1 = moduleService.addModule(subjectId, "Module 1");
        m2 = moduleService.addModule(subjectId, "Module 2");
        m3 = moduleService.addModule(subjectId, "Module 3");

        for (int i = 1; i <= 4; i++) {
            addQ(m1, 2, "Easy", "GEN-A" + i);
        }
        for (int i = 1; i <= 3; i++) {
            addQ(m1, 4, "Medium", "GEN-B" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(m1, 2, "Hard", "GEN-C" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(m2, 4, "Medium", "GEN-D" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(m2, 6, "Hard", "GEN-E" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(m3, 2, "Easy", "GEN-F" + i);
        }

        check("Setup: subject + 3 modules + 15 questions",
                subjectId > 0 && m1 > 0 && m2 > 0 && m3 > 0
                        && allQuestions().size() == 15);
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
    // A. Fully feasible blueprint
    // =====================================================================

    private static void scenarioA() throws Exception {
        ExamBlueprint bp = makeBlueprint(12);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 4);
        blueprintService.addPatternRow(bp, m1, 2, 2);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 8);
        blueprintService.addPatternRow(bp, m2, 4, 2);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        check("A: blueprint is structurally valid",
                blueprintService.validateBlueprint(bp).isEmpty());

        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("A: FEASIBLE", f.isFeasible());
        check("A: total required questions = 4", f.getTotalRequiredQuestions() == 4);
        check("A: no shortages", f.getShortages().isEmpty());

        GenerationResult g = generate(bp, slots);
        check("A: generation succeeds", g.isSuccess() && g.getPaper() != null);
        check("A: generated paper passes all integrity checks",
                g.isSuccess() && validPaper(g.getPaper(), slots, 12));
        check("A: paper reports subject and total marks",
                g.isSuccess() && g.getPaper().getSubjectId() == subjectId
                        && g.getPaper().getSubjectName().equals(subjectName)
                        && g.getPaper().getTotalMarks() == 12);
    }

    // =====================================================================
    // B. Exact availability (required == available)
    // =====================================================================

    private static void scenarioB() throws Exception {
        ExamBlueprint bp = makeBlueprint(12);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 12);
        blueprintService.addPatternRow(bp, m1, 4, 3);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("B: exact availability (3 required / 3 available) is FEASIBLE",
                f.isFeasible());
        check("B: requirement reports required 3, available 3",
                f.getRequirements().size() == 1
                        && f.getRequirements().get(0).getRequiredCount() == 3
                        && f.getRequirements().get(0).getAvailableCount() == 3);

        GenerationResult g = generate(bp, slots);
        check("B: generation succeeds at exact capacity",
                g.isSuccess() && validPaper(g.getPaper(), slots, 12));
    }

    // =====================================================================
    // C. Insufficient availability
    // =====================================================================

    private static void scenarioC() throws Exception {
        ExamBlueprint bp = makeBlueprint(12);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 12);
        blueprintService.addPatternRow(bp, m2, 4, 3);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("C: NOT FEASIBLE when 3 required but 2 available", !f.isFeasible());
        check("C: single shortage with exact counts",
                f.getShortages().size() == 1
                        && f.getShortages().get(0).getRequiredCount() == 3
                        && f.getShortages().get(0).getAvailableCount() == 2
                        && f.getShortages().get(0).getShortage() == 1);
        checkContains("C: shortage report names module", f, "Module 2");
        checkContains("C: shortage report shows marks", f, "4 marks");
        checkContains("C: shortage report shows difficulty", f, "any difficulty");
        checkContains("C: shortage report shows Required", f, "Required: 3");
        checkContains("C: shortage report shows Available", f, "Available: 2");
        checkContains("C: shortage report shows Shortage", f, "Shortage: 1");

        GenerationResult g = generate(bp, slots);
        check("C: generation refused", !g.isSuccess());
        check("C: no partial paper returned", g.getPaper() == null);
        check("C: failure message carries the shortage",
                g.getFailureMessage().contains("Required: 3")
                        && g.getFailureMessage().contains("Available: 2"));
    }

    // =====================================================================
    // D. Multiple shortages - all reported
    // =====================================================================

    private static void scenarioD() throws Exception {
        ExamBlueprint bp = makeBlueprint(26);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 14);
        blueprintService.addPatternRow(bp, m1, 2, 7);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 12);
        blueprintService.addPatternRow(bp, m2, 4, 3);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("D: NOT FEASIBLE", !f.isFeasible());
        check("D: both shortages reported", f.getShortages().size() == 2);
        boolean twoMarksShort = false;
        boolean fourMarksShort = false;
        for (FeasibilityResult.PatternRequirement r : f.getShortages()) {
            if (r.getMarks() == 2 && r.getDifficulty().equals("any difficulty")) {
                twoMarksShort = r.getRequiredCount() == 7 && r.getAvailableCount() == 6;
            }
            if (r.getMarks() == 4 && r.getDifficulty().equals("any difficulty")) {
                fourMarksShort = r.getRequiredCount() == 3 && r.getAvailableCount() == 2;
            }
        }
        check("D: 2-mark shortage is 7 required / 6 available", twoMarksShort);
        check("D: 4-mark shortage is 3 required / 2 available", fourMarksShort);
        check("D: report contains both failures",
                f.getFailureReport().contains("Module 1")
                        && f.getFailureReport().contains("Module 2"));

        GenerationResult g = generate(bp, slots);
        check("D: generation refused with no paper", !g.isSuccess() && g.getPaper() == null);
    }

    // =====================================================================
    // E. Same marks, only some difficulties available in the pool
    //    (the Medium slots the policy wants must be moved to Easy/Hard)
    // =====================================================================

    private static void scenarioE() throws Exception {
        ExamBlueprint bp = makeBlueprint(8);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 8);
        blueprintService.addPatternRow(bp, m1, 2, 4);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("E: feasible - 6 two-mark questions across difficulties",
                f.isFeasible()
                        && f.getRequirements().size() == 1
                        && f.getRequirements().get(0).getRequiredCount() == 4
                        && f.getRequirements().get(0).getAvailableCount() == 6);

        GenerationResult g = generate(bp, slots);
        check("E: generation succeeds", g.isSuccess() && validPaper(g.getPaper(), slots, 8));
        if (g.isSuccess()) {
            boolean ok = true;
            for (int i = 0; i < slots.size(); i++) {
                String slotDiff = slots.get(i).getDifficulty();
                String qDiff = g.getPaper().getQuestions().get(i).getDifficulty();
                // M1 has no Medium two-mark questions: every Medium slot the
                // policy wanted must have been moved before selection.
                if (!slotDiff.equals(qDiff) || "Medium".equals(slotDiff)) {
                    ok = false;
                }
            }
            check("E: unavailable Medium slots moved; slot difficulty == question difficulty",
                    ok);
        } else {
            check("E: unavailable Medium slots moved; slot difficulty == question difficulty",
                    false);
        }
    }

    // =====================================================================
    // F. Same marks, different modules
    // =====================================================================

    private static void scenarioF() throws Exception {
        ExamBlueprint bp = makeBlueprint(16);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 8);
        blueprintService.addPatternRow(bp, m1, 4, 2);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 8);
        blueprintService.addPatternRow(bp, m2, 4, 2);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        check("F: feasible (3 M1 + 2 M2 Medium available)",
                validator.checkAvailability(subjectId, slots).isFeasible());

        GenerationResult g = generate(bp, slots);
        check("F: generation succeeds", g.isSuccess() && validPaper(g.getPaper(), slots, 16));
        if (g.isSuccess()) {
            boolean ok = true;
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i).getModuleId()
                        != g.getPaper().getQuestions().get(i).getModuleId()) {
                    ok = false;
                }
            }
            check("F: M1 questions never assigned to M2 slots", ok);
        } else {
            check("F: M1 questions never assigned to M2 slots", false);
        }
    }

    // =====================================================================
    // G. Duplicate prevention - more candidates than required
    // =====================================================================

    private static void scenarioG() throws Exception {
        ExamBlueprint bp = makeBlueprint(4);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 4);
        blueprintService.addPatternRow(bp, m1, 2, 2);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        GenerationResult g = generate(bp, slots);
        check("G: generation succeeds with 4 candidates for 2 slots",
                g.isSuccess() && validPaper(g.getPaper(), slots, 4));
        if (g.isSuccess()) {
            Set<Integer> ids = new HashSet<>();
            for (model.GeneratedQuestion q : g.getPaper().getQuestions()) {
                ids.add(q.getQuestionId());
            }
            check("G: all selected question ids are unique", ids.size() == 2);
        } else {
            check("G: all selected question ids are unique", false);
        }
    }

    // =====================================================================
    // H. Exact candidate pool reuse
    // =====================================================================

    private static void scenarioH() throws Exception {
        ExamBlueprint bp = makeBlueprint(8);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 8);
        blueprintService.addPatternRow(bp, m1, 2, 4);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("H: one pattern reported once with required 4 / available 6",
                f.getRequirements().size() == 1
                        && f.getRequirements().get(0).getRequiredCount() == 4
                        && f.getRequirements().get(0).getAvailableCount() == 6
                        && f.isFeasible());

        GenerationResult g = generate(bp, slots);
        check("H: generation succeeds using the single group",
                g.isSuccess() && validPaper(g.getPaper(), slots, 8));
        if (g.isSuccess()) {
            Set<Integer> ids = new HashSet<>();
            for (model.GeneratedQuestion q : g.getPaper().getQuestions()) {
                ids.add(q.getQuestionId());
            }
            check("H: 4 distinct questions drawn from the 6-question group",
                    ids.size() == 4);
        } else {
            check("H: 4 distinct questions drawn from the 6-question group", false);
        }
    }

    // =====================================================================
    // I + J. Sequence preservation and total marks
    // =====================================================================

    private static void scenarioIAndJ() throws Exception {
        ExamBlueprint bp = makeBlueprint(16);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 6);
        blueprintService.addPatternRow(bp, m1, 2, 1);
        blueprintService.addPatternRow(bp, m1, 4, 1);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 10);
        blueprintService.addPatternRow(bp, m2, 4, 1);
        blueprintService.addPatternRow(bp, m2, 6, 1);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        check("I: mixed blueprint structurally valid",
                blueprintService.validateBlueprint(bp).isEmpty());

        GenerationResult g = generate(bp, slots);
        check("I: generation succeeds", g.isSuccess() && validPaper(g.getPaper(), slots, 16));
        if (g.isSuccess()) {
            boolean ordered = true;
            for (int i = 0; i < slots.size(); i++) {
                model.GeneratedQuestion q = g.getPaper().getQuestions().get(i);
                BlueprintSlot s = slots.get(i);
                if (q.getSequenceNo() != s.getSequenceNo()
                        || q.getModuleId() != s.getModuleId()
                        || q.getMarks() != s.getMarks()
                        || !q.getDifficulty().equals(s.getDifficulty())) {
                    ordered = false;
                }
            }
            check("I: questions follow the exact blueprint sequence (not id/marks/module order)",
                    ordered);

            int sum = 0;
            for (model.GeneratedQuestion q : g.getPaper().getQuestions()) {
                sum += q.getMarks();
            }
            check("J: sum of generated marks == blueprint total (16)",
                    sum == 16 && g.getPaper().getTotalMarks() == 16);
        } else {
            check("I: questions follow the exact blueprint sequence (not id/marks/module order)",
                    false);
            check("J: sum of generated marks == blueprint total (16)", false);
        }
    }

    // =====================================================================
    // K. Question-bank immutability
    // =====================================================================

    private static void scenarioK() throws Exception {
        Map<Integer, String> before = snapshotBank();

        ExamBlueprint bp = makeBlueprint(12);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 4);
        blueprintService.addPatternRow(bp, m1, 2, 2);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 8);
        blueprintService.addPatternRow(bp, m2, 4, 2);

        GenerationResult g = generate(bp, blueprintService.generateSlots(bp));
        Map<Integer, String> after = snapshotBank();

        check("K: question count unchanged by generation", g.isSuccess()
                && before.size() == 15 && before.size() == after.size());
        check("K: every question row unchanged by generation", before.equals(after));
    }

    // =====================================================================
    // L. Multiple generation runs - every result must be valid
    // =====================================================================

    private static void scenarioL() throws Exception {
        ExamBlueprint bp = makeBlueprint(12);
        bp.selectModule(m1, "Module 1");
        bp.setAllocation(m1, 4);
        blueprintService.addPatternRow(bp, m1, 2, 2);
        bp.selectModule(m2, "Module 2");
        bp.setAllocation(m2, 8);
        blueprintService.addPatternRow(bp, m2, 4, 2);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        boolean allValid = true;
        for (int run = 0; run < 5; run++) {
            GenerationResult g = generate(bp, slots);
            if (!g.isSuccess() || !validPaper(g.getPaper(), slots, 12)) {
                allValid = false;
            }
        }
        check("L: 5 consecutive runs all produce valid complete papers", allValid);
    }

    // =====================================================================
    // Guard: an empty blueprint must not produce an empty "paper"
    // =====================================================================

    private static void emptyBlueprintGuard() {
        GenerationResult g = generator.generatePaper(
                subjectId, subjectName, "Semester Examination", "Medium", 0,
                new ArrayList<>());
        check("Empty slot list refused (no empty paper)", !g.isSuccess() && g.getPaper() == null);

        GenerationResult noDifficulty = generator.generatePaper(
                subjectId, subjectName, "Semester Examination", "", 12,
                new ArrayList<>(java.util.List.of(
                        new model.BlueprintSlot(1, m1, 2))));
        check("Missing examination difficulty refused",
                !noDifficulty.isSuccess() && noDifficulty.getPaper() == null
                        && noDifficulty.getFailureMessage().contains(
                                "examination difficulty"));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static ExamBlueprint makeBlueprint(int totalMarks) {
        ExamBlueprint bp = new ExamBlueprint();
        bp.setSubjectId(subjectId);
        bp.setSubjectName(subjectName);
        bp.setExamType(ExamBlueprint.TYPE_SEMESTER);
        bp.setExamDifficulty("Medium");
        bp.setTotalMarks(totalMarks);
        return bp;
    }

    private static GenerationResult generate(ExamBlueprint bp, List<BlueprintSlot> slots) {
        return generator.generatePaper(subjectId, bp.getSubjectName(),
                bp.getExamType(), bp.getExamDifficulty(), bp.getTotalMarks(), slots);
    }

    /** Property check: complete, one question per slot, distinct ids, exact eligibility, sequence, totals. */
    private static boolean validPaper(GeneratedPaper paper, List<BlueprintSlot> slots,
                                      int totalMarks) {
        if (paper == null || paper.getQuestions().size() != slots.size()) {
            return false;
        }
        Set<Integer> ids = new HashSet<>();
        int sum = 0;
        for (int i = 0; i < slots.size(); i++) {
            BlueprintSlot s = slots.get(i);
            model.GeneratedQuestion q = paper.getQuestions().get(i);
            if (q.getQuestionId() <= 0 || !ids.add(q.getQuestionId())) {
                return false;
            }
            if (q.getSequenceNo() != s.getSequenceNo()
                    || q.getModuleId() != s.getModuleId()
                    || q.getMarks() != s.getMarks()
                    || !q.getDifficulty().equals(s.getDifficulty())
                    || q.getSubjectId() != subjectId) {
                return false;
            }
            if (q.getQuestionText() == null || q.getQuestionText().isEmpty()
                    || q.getAnswerText() == null || q.getAnswerText().isEmpty()) {
                return false;
            }
            sum += q.getMarks();
        }
        int slotSum = 0;
        for (BlueprintSlot s : slots) {
            slotSum += s.getMarks();
        }
        return sum == totalMarks && sum == slotSum
                && paper.getTotalMarks() == totalMarks
                && paper.getSubjectId() == subjectId;
    }

    private static Map<Integer, String> snapshotBank() throws Exception {
        Map<Integer, String> snapshot = new LinkedHashMap<>();
        for (Question q : questionService.searchQuestions(subjectId, null, null, null, null)) {
            snapshot.put(q.getQuestionId(),
                    q.getModuleId() + "|" + q.getMarks() + "|" + q.getDifficulty()
                            + "|" + q.getQuestionText() + "|" + q.getAnswerText());
        }
        return snapshot;
    }

    private static List<Question> allQuestions() throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null);
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

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static void checkContains(String name, FeasibilityResult f, String fragment) {
        if (f.getFailureReport() != null && f.getFailureReport().contains(fragment)) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name + " (missing: \"" + fragment + "\")");
            System.out.println("      report was: " + f.getFailureReport());
        }
    }
}
