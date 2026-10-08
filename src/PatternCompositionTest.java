import model.BlueprintSlot;
import model.ExamBlueprint;
import model.ExamPatternRow;
import model.FeasibilityResult;
import model.GenerationResult;
import model.Module;
import model.Question;
import model.QuestionPaper;
import model.Subject;
import service.BlueprintService;
import service.BlueprintValidator;
import service.ModuleService;
import service.PaperGenerator;
import service.PatternPolicy;
import service.QuestionService;
import service.SubjectService;
import ui.GeneratePaperPanel;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Stage 12 TDD suite: automatic mark-pattern composition (pattern policy).
 *
 * Covers specification sections 2-7 and 25/29 (pattern composition):
 * configured global rules, deterministic automatic composition,
 * validation of rules, different module allocations, repeated global
 * application, no silent scaling and no pattern alteration because of a
 * question shortage.
 *
 * Uses its own test subject in the live DB and cleans everything up.
 */
public class PatternCompositionTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final BlueprintService blueprintService = new BlueprintService();
    private static final PaperGenerator paperGenerator = new PaperGenerator();
    private static final BlueprintValidator availabilityValidator = new BlueprintValidator();

    private static final String SUBJECT_NAME = "TEST S12 COMPOSITION";
    private static final String[] DIFFICULTIES = {"Easy", "Medium", "Hard"};

    private static int subjectId = -1;
    /** modules[0..4] = normal balanced modules, modules[5] = shortage fixture. */
    private static final int[] modules = new int[6];

    private interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        preClean();
        try {
            section("pattern rules", PatternCompositionTest::runPolicyChecks);
            section("fixture", PatternCompositionTest::setupFixture);
            section("denominations", PatternCompositionTest::runDenominationChecks);
            section("policy application", PatternCompositionTest::runApplyChecks);
            section("shortage", PatternCompositionTest::runShortageChecks);
            section("pattern policy UI", PatternCompositionTest::drivePolicyUi);
        } finally {
            cleanup();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================
    // Section: configured rules and automatic composition (in memory)
    // =====================================================================

    private static void runPolicyChecks() {
        List<Integer> denoms = Arrays.asList(2, 4, 6, 8);

        PatternPolicy policy = new PatternPolicy();
        policy.setRule(12, Arrays.asList(4, 8));
        check("Rule 12 -> 4 + 8 is stored",
                policy.hasRule(12) && Arrays.asList(4, 8).equals(policy.getRule(12)));
        check("12 composes as 4 + 8 from the rule",
                Arrays.asList(4, 8).equals(policy.compose(12, denoms)));

        PatternPolicy p444 = new PatternPolicy();
        p444.setRule(12, Arrays.asList(4, 4, 4));
        check("12 composes as 4 + 4 + 4 from the rule",
                Arrays.asList(4, 4, 4).equals(p444.compose(12, denoms)));

        PatternPolicy p822 = new PatternPolicy();
        p822.setRule(12, Arrays.asList(8, 2, 2));
        check("12 composes as 8 + 2 + 2 from the rule",
                Arrays.asList(8, 2, 2).equals(p822.compose(12, denoms)));

        PatternPolicy auto = new PatternPolicy();
        List<Integer> composed12 = auto.compose(12, denoms);
        check("Automatic composition of 12 uses the fewest questions (4 + 8)",
                Arrays.asList(4, 8).equals(composed12));
        check("Automatic composition sums exactly to the allocation",
                sum(composed12) == 12 && allFrom(composed12, denoms));
        check("Automatic composition is deterministic",
                composed12.equals(auto.compose(12, denoms)));

        expectIAE("Zero part in a rule is rejected",
                () -> policy.setRule(12, Arrays.asList(0, 12)));
        expectIAE("Negative part in a rule is rejected",
                () -> policy.setRule(12, Arrays.asList(-4, 16)));
        expectIAE("Empty rule composition is rejected",
                () -> policy.setRule(12, new ArrayList<Integer>()));
        expectIAE("Null rule composition is rejected",
                () -> policy.setRule(12, null));
        expectIAE("Rule allocation must be positive",
                () -> policy.setRule(0, Arrays.asList(0)));
        expectIAE("Composition sum mismatch is rejected",
                () -> policy.setRule(12, Arrays.asList(4, 6)));
        check("Rejected rules leave the stored rule untouched",
                Arrays.asList(4, 8).equals(policy.getRule(12)));

        PatternPolicy mixed = new PatternPolicy();
        mixed.setRule(12, Arrays.asList(4, 8));
        check("12-mark allocation reuses the configured rule",
                Arrays.asList(4, 8).equals(mixed.compose(12, denoms)));
        check("6-mark allocation composes independently - no silent scaling",
                Arrays.asList(6).equals(mixed.compose(6, Arrays.asList(4, 6, 8))));
        check("Every allocation gets a valid composition of its own",
                Arrays.asList(6).equals(mixed.compose(6, denoms)));

        check("Automatic composition backtracks when greedy fails",
                Arrays.asList(3, 3).equals(auto.compose(6, Arrays.asList(3, 5))));
        expectIAE("Impossible composition is rejected",
                () -> auto.compose(7, Arrays.asList(2, 4, 8)));
        expectIAE("Composition without denominations is rejected",
                () -> auto.compose(6, new ArrayList<Integer>()));
        expectIAE("Composition of a non-positive allocation is rejected",
                () -> auto.compose(0, denoms));

        List<Integer> stored = policy.getRule(12);
        if (stored != null) {
            stored.add(99);
        }
        check("Stored rules are protected from callers",
                Arrays.asList(4, 8).equals(policy.getRule(12)));
    }

    // =====================================================================
    // Fixture: 1 subject, 6 modules, 66 questions
    //   M1..M5 (normal): 4-mark x6 + 8-mark x6, balanced per difficulty
    //   M6 (shortage):   2-mark x6 only
    // =====================================================================

    private static void setupFixture() throws Exception {
        subjectId = subjectService.addSubject(SUBJECT_NAME);
        for (int i = 1; i <= 6; i++) {
            String name = i <= 5 ? "COMP M" + i : "COMP SHORTAGE";
            modules[i - 1] = moduleService.addModule(subjectId, name);
        }
        for (int i = 0; i < 5; i++) {
            for (String difficulty : DIFFICULTIES) {
                for (int k = 1; k <= 2; k++) {
                    addQ(modules[i], 4, difficulty,
                            "COMP-M" + (i + 1) + "-4-" + difficulty + "-" + k);
                    addQ(modules[i], 8, difficulty,
                            "COMP-M" + (i + 1) + "-8-" + difficulty + "-" + k);
                }
            }
        }
        for (String difficulty : DIFFICULTIES) {
            for (int k = 1; k <= 2; k++) {
                addQ(modules[5], 2, difficulty,
                        "COMP-SHORT-2-" + difficulty + "-" + k);
            }
        }
        check("Fixture: 6 modules with 66 questions",
                subjectId > 0
                        && bankQuestions().size() == 66);
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
    // Section: denominations come from the question bank
    // =====================================================================

    private static void runDenominationChecks() throws Exception {
        List<Integer> denoms = blueprintService.getDenominations(subjectId);
        check("Denominations are read from the question bank",
                Arrays.asList(2, 4, 8).equals(denoms));
        check("An unknown subject has no denominations",
                blueprintService.getDenominations(9999999).isEmpty());
    }

    // =====================================================================
    // Section: applyPatternPolicy (spec section 31 end-to-end)
    // =====================================================================

    private static void runApplyChecks() throws Exception {
        ExamBlueprint exam = new ExamBlueprint();
        exam.setSubjectId(subjectId);
        exam.setSubjectName(SUBJECT_NAME);
        exam.setExamType(ExamBlueprint.TYPE_SEMESTER);
        exam.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        exam.setTotalMarks(60);
        for (int i = 1; i <= 5; i++) {
            exam.selectModule(modules[i - 1], "COMP M" + i);
            exam.setAllocation(modules[i - 1], 12);
        }

        PatternPolicy policy = new PatternPolicy();
        policy.setRule(12, Arrays.asList(4, 8));
        Map<Integer, List<Integer>> composed =
                blueprintService.applyPatternPolicy(exam, policy);

        check("Rule applied to all five 12-mark modules", composed.size() == 5);
        boolean allRule = composed.size() == 5;
        for (int i = 1; i <= 5; i++) {
            allRule = allRule && Arrays.asList(4, 8).equals(composed.get(modules[i - 1]))
                    && rowsEqual(exam, modules[i - 1], new int[][]{{4, 1}, {8, 1}});
        }
        check("Every module's pattern rows are exactly 4 + 8", allRule);
        check("Applied blueprint is structurally valid",
                blueprintService.validateBlueprint(exam).isEmpty());

        List<BlueprintSlot> slots = blueprintService.generateSlots(exam);
        check("Derived slots keep the pattern: 10 questions, 60 marks",
                slots.size() == 10 && blueprintService.totalMarks(slots) == 60);
        check("Slot order is module by module, 4 then 8",
                slots.get(0).getMarks() == 4 && slots.get(1).getMarks() == 8
                        && slots.get(2).getMarks() == 4 && slots.get(3).getMarks() == 8);

        blueprintService.applyPatternPolicy(exam, policy);
        check("Repeated application does not duplicate or change rows",
                rowsEqual(exam, modules[0], new int[][]{{4, 1}, {8, 1}})
                        && exam.getPatternRows(modules[0]).size() == 2);

        GenerationResult result = paperGenerator.generatePaper(
                subjectId, SUBJECT_NAME, ExamBlueprint.TYPE_SEMESTER,
                ExamBlueprint.EXAM_DIFFICULTY_MEDIUM, 60, slots);
        check("Generation succeeds from the composed pattern",
                result.isSuccess() && result.getPaper() != null
                        && result.getPaper().getQuestionCount() == 10);

        // --- different module allocations (rule for 12, none for 6) ---
        ExamBlueprint mixed = new ExamBlueprint();
        mixed.setSubjectId(subjectId);
        mixed.setSubjectName(SUBJECT_NAME);
        mixed.setExamType(ExamBlueprint.TYPE_SEMESTER);
        mixed.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        mixed.setTotalMarks(18);
        mixed.selectModule(modules[0], "COMP M1");
        mixed.setAllocation(modules[0], 12);
        mixed.selectModule(modules[5], "COMP SHORTAGE");
        mixed.setAllocation(modules[5], 6);

        PatternPolicy mixedPolicy = new PatternPolicy();
        mixedPolicy.setRule(12, Arrays.asList(4, 8));
        Map<Integer, List<Integer>> mixedComposed = blueprintService.applyPatternPolicy(
                mixed, mixedPolicy, Arrays.asList(4, 6, 8));
        check("12-mark module keeps the configured rule",
                Arrays.asList(4, 8).equals(mixedComposed.get(modules[0]))
                        && rowsEqual(mixed, modules[0], new int[][]{{4, 1}, {8, 1}}));
        check("6-mark module gets its own composition, never a scaled rule",
                Arrays.asList(6).equals(mixedComposed.get(modules[5]))
                        && rowsEqual(mixed, modules[5], new int[][]{{6, 1}}));
        check("Mixed-allocation blueprint is structurally valid",
                blueprintService.validateBlueprint(mixed).isEmpty());

        // --- modules without an allocation are skipped ---
        ExamBlueprint skipped = new ExamBlueprint();
        skipped.setSubjectId(subjectId);
        skipped.setSubjectName(SUBJECT_NAME);
        skipped.setExamType(ExamBlueprint.TYPE_SEMESTER);
        skipped.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        skipped.setTotalMarks(12);
        skipped.selectModule(modules[0], "COMP M1");
        Map<Integer, List<Integer>> skipComposed = blueprintService.applyPatternPolicy(
                skipped, new PatternPolicy(), Arrays.asList(4, 8));
        check("A module without allocation is skipped",
                skipComposed.isEmpty() && skipped.getPatternRows(modules[0]).isEmpty());
    }

    // =====================================================================
    // Section: shortages never alter the academic mark pattern
    // =====================================================================

    private static void runShortageChecks() throws Exception {
        ExamBlueprint shortExam = new ExamBlueprint();
        shortExam.setSubjectId(subjectId);
        shortExam.setSubjectName(SUBJECT_NAME);
        shortExam.setExamType(ExamBlueprint.TYPE_SEMESTER);
        shortExam.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        shortExam.setTotalMarks(12);
        shortExam.selectModule(modules[5], "COMP SHORTAGE");
        shortExam.setAllocation(modules[5], 12);

        PatternPolicy policy = new PatternPolicy();
        policy.setRule(12, Arrays.asList(4, 8));
        blueprintService.applyPatternPolicy(shortExam, policy);
        check("Shortage fixture received the configured pattern",
                rowsEqual(shortExam, modules[5], new int[][]{{4, 1}, {8, 1}}));

        List<BlueprintSlot> slots = blueprintService.generateSlots(shortExam);
        FeasibilityResult feasibility = availabilityValidator.checkAvailability(
                subjectId, slots);
        check("Availability analysis reports the shortage",
                !feasibility.isFeasible() && !feasibility.getShortages().isEmpty());
        boolean exact = false;
        for (FeasibilityResult.PatternRequirement shortage : feasibility.getShortages()) {
            if (shortage.getModuleId() == modules[5] && shortage.getMarks() == 8
                    && shortage.getShortage() > 0) {
                exact = true;
            }
        }
        check("Shortage names the exact module and mark combination", exact);

        GenerationResult result = paperGenerator.generatePaper(
                subjectId, SUBJECT_NAME, ExamBlueprint.TYPE_SEMESTER,
                ExamBlueprint.EXAM_DIFFICULTY_MEDIUM, 12, slots);
        check("Generation fails cleanly instead of guessing",
                !result.isSuccess() && result.getPaper() == null
                        && !result.getFailureMessage().isEmpty());
        check("Mark pattern is NOT altered because of the shortage",
                rowsEqual(shortExam, modules[5], new int[][]{{4, 1}, {8, 1}}));
    }

    // =====================================================================
    // Section: Pattern Policy in the blueprint editor UI (on the EDT)
    // =====================================================================

    private static void drivePolicyUi() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                GeneratePaperPanel panel = new GeneratePaperPanel();
                panel.refreshPanel();
                panel.selectSubject(subjectId);
                panel.setExamType(ExamBlueprint.TYPE_SEMESTER);
                panel.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
                panel.setTotalMarks(24);
                panel.addModulesToSyllabus(modules[0], modules[1]);
                panel.setModuleAllocation(modules[0], 12);
                panel.setModuleAllocation(modules[1], 12);

                panel.setPatternRule("12", "4+8");
                check("Pattern rule text is accepted", panel.hasPatternRule(12));

                try {
                    panel.applyPatternPolicy();
                    check("Applying the pattern policy succeeds", true);
                } catch (Exception ex) {
                    check("Applying the pattern policy succeeds", false);
                    System.out.println("  unexpected -> " + ex);
                }

                String preview = panel.getPolicyPreviewText();
                check("Preview lists every module's composition",
                        preview.contains("COMP M1") && preview.contains("COMP M2")
                                && preview.contains("4 + 8"));
                check("Policy blueprint is structurally valid",
                        panel.getValidationErrors().isEmpty());
                check("Derived preview keeps the composed slots",
                        panel.getSlots().size() == 4);

                expectIAE("Invalid rule composition text is rejected",
                        () -> panel.setPatternRule("12", "4+7"));
                expectIAE("Non-numeric rule allocation is rejected",
                        () -> panel.setPatternRule("twelve", "4+8"));
                expectIAE("Non-numeric rule composition is rejected",
                        () -> panel.setPatternRule("12", "four+eight"));
                expectIAE("Rule parts must be positive",
                        () -> panel.setPatternRule("12", "4+-8"));
            } catch (Exception ex) {
                failed++;
                System.out.println("FAIL: pattern policy UI section threw -> " + ex);
                ex.printStackTrace();
            }
        });
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static boolean rowsEqual(ExamBlueprint exam, int moduleId, int[][] expected) {
        List<ExamPatternRow> rows = exam.getPatternRows(moduleId);
        if (rows.size() != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (rows.get(i).getMarks() != expected[i][0]
                    || rows.get(i).getQuestionCount() != expected[i][1]) {
                return false;
            }
        }
        return true;
    }

    private static int sum(List<Integer> values) {
        int total = 0;
        if (values != null) {
            for (Integer value : values) {
                total += value;
            }
        }
        return total;
    }

    private static boolean allFrom(List<Integer> values, List<Integer> denominations) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (Integer value : values) {
            if (!denominations.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static List<Question> bankQuestions() throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null);
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
            for (QuestionPaper p : new service.PaperReviewService().getPapers()) {
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
