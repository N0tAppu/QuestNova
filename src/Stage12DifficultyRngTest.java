import model.BlueprintSlot;
import model.ExamBlueprint;
import model.ExamPatternRow;
import model.FeasibilityResult;
import model.GeneratedQuestion;
import model.GenerationResult;
import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.ReviewQuestion;
import service.BlueprintService;
import service.BlueprintValidator;
import service.ExamDifficultyPolicy;
import service.ModuleService;
import service.PaperGenerator;
import service.PaperReviewService;
import service.QuestionService;
import service.SubjectService;
import dao.PaperDAO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Stage 12 test: Autonomous Examination Difficulty RNG.
 *
 * One examination-level difficulty (Easy / Medium / Hard) drives a
 * controlled, availability-aware random difficulty assignment. Difficulty is
 * NEVER authored per pattern row and NEVER inferred from marks.
 *
 * Uses its own test subject/modules/questions in the live DB and cleans up
 * only its own data (papers, questions, modules, subject).
 */
public class Stage12DifficultyRngTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final BlueprintService blueprintService = new BlueprintService();
    private static final BlueprintValidator validator = new BlueprintValidator();
    private static final PaperGenerator generator = new PaperGenerator();
    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final PaperReviewService reviewService = new PaperReviewService();
    private static final PaperDAO paperDAO = new PaperDAO();

    private static int subjectId = -1;
    private static int mA = -1;
    private static int mB = -1;
    private static int mC = -1;
    private static int mD = -1;
    private static final String subjectName = "TEST S12 RNG";

    public static void main(String[] args) {
        try {
            setup();
            policyCounts();
            blueprintWithoutPerRowDifficulty();
            marksDifficultyIndependence();
            seededShuffle();
            availabilityAdjustment();
            completeShortage();
            candidateMatchingAndIntegrity();
            finalizationKeepsGeneratedDifficulty();
            replaceRespectsGeneratedDifficulty();
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
    // Fixture: own subject, 4 modules, 30 questions with fixed distributions
    //   A: 2m Easy x6, 2m Medium x3, 2m Hard x2,
    //      4m Easy x3, 4m Medium x3, 4m Hard x3,
    //      8m Easy x2, 8m Medium x2, 8m Hard x2        (26 questions)
    //   B: 2m Medium x1                                  (1 question)
    //   C: 2m Easy x3                                    (3 questions)
    //   D: (no questions at all)                         (0 questions)
    // =====================================================================

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        mA = moduleService.addModule(subjectId, "S12 Module A");
        mB = moduleService.addModule(subjectId, "S12 Module B");
        mC = moduleService.addModule(subjectId, "S12 Module C");
        mD = moduleService.addModule(subjectId, "S12 Module D");

        for (int i = 1; i <= 6; i++) {
            addQ(mA, 2, "Easy", "S12-A2E" + i);
        }
        for (int i = 1; i <= 3; i++) {
            addQ(mA, 2, "Medium", "S12-A2M" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(mA, 2, "Hard", "S12-A2H" + i);
        }
        for (int i = 1; i <= 3; i++) {
            addQ(mA, 4, "Easy", "S12-A4E" + i);
        }
        for (int i = 1; i <= 3; i++) {
            addQ(mA, 4, "Medium", "S12-A4M" + i);
        }
        for (int i = 1; i <= 3; i++) {
            addQ(mA, 4, "Hard", "S12-A4H" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(mA, 8, "Easy", "S12-A8E" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(mA, 8, "Medium", "S12-A8M" + i);
        }
        for (int i = 1; i <= 2; i++) {
            addQ(mA, 8, "Hard", "S12-A8H" + i);
        }
        addQ(mB, 2, "Medium", "S12-B2M1");
        for (int i = 1; i <= 3; i++) {
            addQ(mC, 2, "Easy", "S12-C2E" + i);
        }

        check("Setup: subject + 4 modules + 30 questions",
                subjectId > 0 && mA > 0 && mB > 0 && mC > 0 && mD > 0
                        && allQuestions().size() == 30);
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
    // 1. Policy calculation (pure, no database)
    // =====================================================================

    private static void policyCounts() {
        int[] easy = ExamDifficultyPolicy.countsFor(
                ExamBlueprint.EXAM_DIFFICULTY_EASY, 10);
        check("Policy: Easy exam 10 slots = 70/25/5 -> 7 Easy, 3 Medium, 0 Hard",
                easy[0] == 7 && easy[1] == 3 && easy[2] == 0);

        int[] medium = ExamDifficultyPolicy.countsFor(
                ExamBlueprint.EXAM_DIFFICULTY_MEDIUM, 10);
        check("Policy: Medium exam 10 slots = 20/60/20 -> 2 Easy, 6 Medium, 2 Hard",
                medium[0] == 2 && medium[1] == 6 && medium[2] == 2);

        int[] hard = ExamDifficultyPolicy.countsFor(
                ExamBlueprint.EXAM_DIFFICULTY_HARD, 7);
        check("Policy: Hard exam 7 slots (largest remainder) -> 0 Easy, 2 Medium, 5 Hard",
                hard[0] == 0 && hard[1] == 2 && hard[2] == 5);

        boolean sums = true;
        boolean nonNegative = true;
        for (String exam : new String[]{
                ExamBlueprint.EXAM_DIFFICULTY_EASY,
                ExamBlueprint.EXAM_DIFFICULTY_MEDIUM,
                ExamBlueprint.EXAM_DIFFICULTY_HARD}) {
            for (int n = 0; n <= 60; n++) {
                int[] c = ExamDifficultyPolicy.countsFor(exam, n);
                if (c[0] + c[1] + c[2] != n) {
                    sums = false;
                }
                if (c[0] < 0 || c[1] < 0 || c[2] < 0) {
                    nonNegative = false;
                }
            }
        }
        check("Policy: counts always sum exactly to the slot count (0..60)", sums);
        check("Policy: counts are never negative", nonNegative);

        expectIllegal(() -> ExamDifficultyPolicy.countsFor("Extreme", 10),
                "Policy: invalid examination difficulty rejected");
    }

    // =====================================================================
    // 2. Blueprint without per-row difficulty
    // =====================================================================

    private static void blueprintWithoutPerRowDifficulty() {
        ExamBlueprint exam = new ExamBlueprint();
        exam.selectModule(mA, "S12 Module A");

        ExamPatternRow row = blueprintService.addPatternRow(exam, mA, 2, 3);
        check("Pattern row stores marks and count only",
                exam.getPatternRows(mA).size() == 1
                        && row.getMarks() == 2
                        && row.getQuestionCount() == 3
                        && row.getTotalMarks() == 6);

        expectIllegalContaining(() -> blueprintService.addPatternRow(exam, mA, 2, 5),
                "combine", "Duplicate marks row in the same module rejected");

        // --- examination difficulty is required and accepted ---
        ExamBlueprint bp = makeBlueprint(6);
        bp.selectModule(mA, "S12 Module A");
        bp.setAllocation(mA, 6);
        blueprintService.addPatternRow(bp, mA, 2, 3);
        List<String> errs = blueprintService.validateBlueprint(bp);
        checkContains(errs, "examination difficulty",
                "Blueprint without examination difficulty is rejected");

        for (String examDifficulty : new String[]{
                ExamBlueprint.EXAM_DIFFICULTY_EASY,
                ExamBlueprint.EXAM_DIFFICULTY_MEDIUM,
                ExamBlueprint.EXAM_DIFFICULTY_HARD}) {
            bp.setExamDifficulty(examDifficulty);
            check("Examination difficulty " + examDifficulty + " accepted",
                    blueprintService.validateBlueprint(bp).isEmpty());
        }

        bp.setExamDifficulty("Extreme");
        checkContains(blueprintService.validateBlueprint(bp), "examination difficulty",
                "Invalid examination difficulty is rejected");

        // --- derived slots carry NO difficulty before generation ---
        bp.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        boolean nullDifficulty = true;
        for (BlueprintSlot slot : slots) {
            if (slot.getDifficulty() != null) {
                nullDifficulty = false;
            }
        }
        check("Derived slots have no authored difficulty (assigned at generation)",
                slots.size() == 3 && nullDifficulty);
    }

    // =====================================================================
    // 3. Marks x difficulty independence (never inferred from marks)
    // =====================================================================

    private static void marksDifficultyIndependence() {
        List<BlueprintSlot> twoMark = new ArrayList<>();
        List<BlueprintSlot> eightMark = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            twoMark.add(new BlueprintSlot(i + 1, mA, 2));
            eightMark.add(new BlueprintSlot(i + 1, mA, 8));
        }
        ExamDifficultyPolicy.Availability plenty = (mid, marks, diff) -> 1000;

        ExamDifficultyPolicy.assign(twoMark,
                ExamBlueprint.EXAM_DIFFICULTY_EASY, plenty, new Random(5));
        ExamDifficultyPolicy.assign(eightMark,
                ExamBlueprint.EXAM_DIFFICULTY_EASY, plenty, new Random(5));

        check("Same seed gives the same difficulty sequence for 2-mark and 8-mark slots",
                difficultiesOf(twoMark).equals(difficultiesOf(eightMark)));

        int[] counts = ExamDifficultyPolicy.countsFor(
                ExamBlueprint.EXAM_DIFFICULTY_EASY, 12);
        List<String> d = difficultiesOf(eightMark);
        check("Easy exam 12 slots -> exact 84/30/6 policy counts (8/3/1)",
                counts[0] == 8 && counts[1] == 3 && counts[2] == 1
                        && count(d, "Easy") == 8
                        && count(d, "Medium") == 3
                        && count(d, "Hard") == 1);
    }

    // =====================================================================
    // 4. Seeded shuffle (variation without flaky single-roll assertions)
    // =====================================================================

    private static void seededShuffle() {
        List<BlueprintSlot> base = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            base.add(new BlueprintSlot(i + 1, mA, 2));
        }
        ExamDifficultyPolicy.Availability plenty = (mid, marks, diff) -> 1000;

        List<BlueprintSlot> firstRun = copyOf(base);
        ExamDifficultyPolicy.assign(firstRun, ExamBlueprint.EXAM_DIFFICULTY_MEDIUM,
                plenty, new Random(11));
        List<BlueprintSlot> sameSeed = copyOf(base);
        ExamDifficultyPolicy.assign(sameSeed, ExamBlueprint.EXAM_DIFFICULTY_MEDIUM,
                plenty, new Random(11));
        check("Same seed produces the identical difficulty assignment",
                difficultiesOf(firstRun).equals(difficultiesOf(sameSeed)));

        List<BlueprintSlot> otherSeed = copyOf(base);
        ExamDifficultyPolicy.assign(otherSeed, ExamBlueprint.EXAM_DIFFICULTY_MEDIUM,
                plenty, new Random(12));
        check("Different seeds produce different assignments (40 slots)",
                !difficultiesOf(firstRun).equals(difficultiesOf(otherSeed)));

        List<String> d = difficultiesOf(firstRun);
        int easy = count(d, "Easy");
        int medium = count(d, "Medium");
        int hard = count(d, "Hard");
        check("Shuffled assignment keeps the exact policy counts (8/24/8)",
                easy == 8 && medium == 24 && hard == 8);

        expectIllegal(() -> ExamDifficultyPolicy.assign(
                copyOf(base), "Extreme", plenty, new Random(1)),
                "Assignment with invalid examination difficulty rejected");
    }

    // =====================================================================
    // 5. Availability-aware adjustment (difficulty is the ONLY thing moved)
    // =====================================================================

    private static void availabilityAdjustment() throws Exception {
        // Single module + single mark value => policy counts are exact and the
        // adjustment result is deterministic: Hard cap 2 forces a shift.
        ExamBlueprint bp = makeBlueprint(12);
        bp.selectModule(mA, "S12 Module A");
        bp.setAllocation(mA, 12);
        blueprintService.addPatternRow(bp, mA, 2, 6);
        bp.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_HARD);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        // Policy for Hard exam / 6 slots: 0 Easy, 2 Medium, 4 Hard.
        // Available at (A, 2): Easy 6, Medium 3, Hard 2 -> Hard capped at 2.
        ExamDifficultyPolicy.assign(slots, bp.getExamDifficulty(),
                availabilityFromPools(subjectId, slots), new Random(7));

        List<String> d = difficultiesOf(slots);
        check("Adjustment: Hard capped at bank availability (2 Easy, 2 Medium, 2 Hard)",
                count(d, "Easy") == 2 && count(d, "Medium") == 2
                        && count(d, "Hard") == 2);
        check("Adjustment never changed module or marks",
                slots.size() == 6 && blueprintService.totalMarks(slots) == 12);

        FeasibilityResult f = validator.checkAvailability(subjectId, slots);
        check("Adjusted assignment is FEASIBLE", f.isFeasible());

        GenerationResult g = generate(bp, blueprintService.generateSlots(bp));
        check("Generation succeeds after adjustment",
                g.isSuccess() && g.getPaper() != null
                        && validPaper(g.getPaper(), lastSlots, 12));
        if (g.isSuccess()) {
            List<String> paperDiff = new ArrayList<>();
            for (GeneratedQuestion q : g.getPaper().getQuestions()) {
                paperDiff.add(q.getDifficulty());
            }
            check("Generated paper keeps the adjusted distribution",
                    count(paperDiff, "Easy") == 2 && count(paperDiff, "Medium") == 2
                            && count(paperDiff, "Hard") == 2);
        }
    }

    // =====================================================================
    // 6. Complete / partial shortage = clean failure, never a partial paper
    // =====================================================================

    private static void completeShortage() throws Exception {
        // Module D has no questions at all.
        ExamBlueprint empty = makeBlueprint(4);
        empty.selectModule(mD, "S12 Module D");
        empty.setAllocation(mD, 4);
        blueprintService.addPatternRow(empty, mD, 4, 1);
        empty.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);

        List<BlueprintSlot> emptySlots = blueprintService.generateSlots(empty);
        FeasibilityResult f = validator.checkAvailability(subjectId, emptySlots);
        check("Zero availability under ALL difficulties is NOT FEASIBLE", !f.isFeasible());
        String report = f.getFailureReport();
        checkContainsText(report, "S12 Module D", "Failure names the module");
        checkContainsText(report, "4 marks", "Failure names the marks");
        checkContainsText(report, "any difficulty",
                "Failure states that no difficulty could be used");
        checkContainsText(report, "Required: 1", "Failure shows the requirement");
        checkContainsText(report, "Available: 0", "Failure shows zero availability");

        GenerationResult g = generate(empty, emptySlots);
        check("Generation fails cleanly with no paper",
                !g.isSuccess() && g.getPaper() == null);
        checkContainsText(g.getFailureMessage(), "S12 Module D",
                "Generation failure message names the module");

        // Module C: 2-mark questions exist, 4-mark questions do not.
        ExamBlueprint wrongMarks = makeBlueprint(8);
        wrongMarks.selectModule(mC, "S12 Module C");
        wrongMarks.setAllocation(mC, 8);
        blueprintService.addPatternRow(wrongMarks, mC, 4, 2);
        wrongMarks.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_EASY);
        List<BlueprintSlot> wrongSlots = blueprintService.generateSlots(wrongMarks);
        check("Zero availability for a mark value is NOT FEASIBLE (C: 4 marks)",
                !validator.checkAvailability(subjectId, wrongSlots).isFeasible());

        // Module B: 2 required but only 1 available in any difficulty.
        ExamBlueprint partial = makeBlueprint(4);
        partial.selectModule(mB, "S12 Module B");
        partial.setAllocation(mB, 4);
        blueprintService.addPatternRow(partial, mB, 2, 2);
        partial.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);
        List<BlueprintSlot> partialSlots = blueprintService.generateSlots(partial);
        FeasibilityResult pf = validator.checkAvailability(subjectId, partialSlots);
        check("Partial shortage reported (2 required / 1 available)",
                !pf.isFeasible() && pf.getShortages().size() == 1
                        && pf.getShortages().get(0).getRequiredCount() == 2
                        && pf.getShortages().get(0).getAvailableCount() == 1
                        && pf.getShortages().get(0).getShortage() == 1);

        GenerationResult pg = generate(partial, partialSlots);
        check("Partial shortage refuses generation",
                !pg.isSuccess() && pg.getPaper() == null
                        && pg.getFailureMessage().contains("Available: 1"));
    }

    // =====================================================================
    // 7. Candidate matching + full integrity
    // =====================================================================

    private static void candidateMatchingAndIntegrity() throws Exception {
        ExamBlueprint bp = makeBlueprint(32);
        bp.selectModule(mA, "S12 Module A");
        bp.setAllocation(mA, 28);
        blueprintService.addPatternRow(bp, mA, 2, 2);
        blueprintService.addPatternRow(bp, mA, 4, 2);
        blueprintService.addPatternRow(bp, mA, 8, 2);
        bp.selectModule(mC, "S12 Module C");
        bp.setAllocation(mC, 4);
        blueprintService.addPatternRow(bp, mC, 2, 2);
        bp.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        check("Mixed blueprint is structurally valid",
                blueprintService.validateBlueprint(bp).isEmpty());

        GenerationResult g = generate(bp, slots);
        check("Mixed generation succeeds", g.isSuccess() && g.getPaper() != null);
        check("Integrity: complete, distinct, eligible, ordered, exact totals",
                g.isSuccess() && validPaper(g.getPaper(), lastSlots, 32));
        if (g.isSuccess()) {
            Set<Integer> ids = new HashSet<>();
            for (GeneratedQuestion q : g.getPaper().getQuestions()) {
                ids.add(q.getQuestionId());
            }
            check("No duplicate question ids", ids.size() == 8);
        }
    }

    // =====================================================================
    // 8. Finalization retains the generated difficulty
    // =====================================================================

    private static void finalizationKeepsGeneratedDifficulty() throws Exception {
        ExamBlueprint bp = makeBlueprint(8);
        bp.selectModule(mA, "S12 Module A");
        bp.setAllocation(mA, 8);
        blueprintService.addPatternRow(bp, mA, 2, 4);
        bp.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_MEDIUM);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        GenerationResult g = generate(bp, slots);
        check("Finalization scenario generates a paper",
                g.isSuccess() && g.getPaper() != null);
        if (!g.isSuccess()) {
            return;
        }

        PaperReview review = reviewService.openReview(g.getPaper(), lastSlots);
        check("Review opens with no validation errors",
                reviewService.validate(review).isEmpty());
        int paperId = reviewService.finalizePaper(review);
        check("Paper finalized", paperId > 0);

        PaperReview loaded = reviewService.loadPaper(paperId);
        boolean same = loaded != null && loaded.getSlots().size() == lastSlots.size()
                && loaded.getQuestions().size() == lastSlots.size();
        if (same) {
            for (int i = 0; i < lastSlots.size(); i++) {
                BlueprintSlot ls = loaded.getSlots().get(i);
                BlueprintSlot rs = lastSlots.get(i);
                ReviewQuestion lq = loaded.getQuestions().get(i);
                if (ls.getMarks() != rs.getMarks()
                        || !String.valueOf(ls.getDifficulty()).equals(
                                String.valueOf(rs.getDifficulty()))
                        || !String.valueOf(lq.getDifficulty()).equals(
                                String.valueOf(rs.getDifficulty()))) {
                    same = false;
                    break;
                }
            }
        }
        check("Reloaded paper keeps the GENERATED slot and question difficulties", same);

        for (QuestionPaper p : paperDAO.getPapers()) {
            if (p.getPaperId() == paperId) {
                paperDAO.deletePaper(p.getPaperId());
            }
        }
    }

    // =====================================================================
    // 9. Review replace/regenerate respects the generated difficulty
    // =====================================================================

    private static void replaceRespectsGeneratedDifficulty() throws Exception {
        ExamBlueprint bp = makeBlueprint(8);
        bp.selectModule(mA, "S12 Module A");
        bp.setAllocation(mA, 8);
        blueprintService.addPatternRow(bp, mA, 2, 4);
        bp.setExamDifficulty(ExamBlueprint.EXAM_DIFFICULTY_EASY);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        GenerationResult g = generate(bp, slots);
        check("Replace scenario generates a paper",
                g.isSuccess() && g.getPaper() != null);
        if (!g.isSuccess()) {
            return;
        }

        PaperReview review = reviewService.openReview(g.getPaper(), lastSlots);
        int sequenceNo = 1;
        String slotDifficulty = review.getSlotBySequence(sequenceNo).getDifficulty();
        int poolSize = questionService.searchQuestions(
                subjectId, mA, 2, slotDifficulty, null).size();
        List<Question> candidates =
                reviewService.getReplacementCandidates(review, sequenceNo);
        boolean allMatch = !candidates.isEmpty();
        for (Question candidate : candidates) {
            if (!slotDifficulty.equals(candidate.getDifficulty())
                    || candidate.getMarks() != 2
                    || candidate.getModuleId() != mA) {
                allMatch = false;
            }
        }
        int usedMatching = 0;
        for (ReviewQuestion used : review.getQuestions()) {
            if (used.getModuleId() == mA && used.getMarks() == 2
                    && slotDifficulty.equals(used.getDifficulty())) {
                usedMatching++;
            }
        }
        check("Replacement candidates all match the GENERATED difficulty ("
                + slotDifficulty + ")", allMatch
                        && candidates.size() == poolSize - usedMatching);

        String otherDifficulty = "Hard".equals(slotDifficulty) ? "Easy" : "Hard";
        Question wrongDifficulty = null;
        for (Question q : questionService.searchQuestions(
                subjectId, mA, 2, otherDifficulty, null)) {
            boolean used = false;
            for (ReviewQuestion rq : review.getQuestions()) {
                if (rq.getQuestionId() == q.getQuestionId()) {
                    used = true;
                }
            }
            if (!used) {
                wrongDifficulty = q;
                break;
            }
        }
        if (wrongDifficulty != null) {
            boolean rejected = false;
            try {
                reviewService.replaceQuestion(
                        review, sequenceNo, wrongDifficulty.getQuestionId());
            } catch (IllegalArgumentException ex) {
                rejected = true;
            }
            check("Replacing with another difficulty is refused", rejected);
        } else {
            check("Replacing with another difficulty is refused", false);
        }

        reviewService.regenerateQuestion(review, sequenceNo);
        check("Regenerate keeps the generated difficulty of the slot",
                slotDifficulty.equals(
                        review.getQuestionBySequence(sequenceNo).getDifficulty())
                        && reviewService.validate(review).isEmpty());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static List<BlueprintSlot> lastSlots;

    private static GenerationResult generate(ExamBlueprint bp, List<BlueprintSlot> slots) {
        lastSlots = slots;
        return generator.generatePaper(subjectId, bp.getSubjectName(),
                bp.getExamType(), bp.getExamDifficulty(), bp.getTotalMarks(), slots);
    }

    private static ExamBlueprint makeBlueprint(int totalMarks) {
        ExamBlueprint bp = new ExamBlueprint();
        bp.setSubjectId(subjectId);
        bp.setSubjectName(subjectName);
        bp.setExamType(ExamBlueprint.TYPE_SEMESTER);
        bp.setTotalMarks(totalMarks);
        return bp;
    }

    private static ExamDifficultyPolicy.Availability availabilityFromPools(
            int subject, List<BlueprintSlot> slots) throws Exception {
        BlueprintValidator pools = new BlueprintValidator();
        java.util.Map<BlueprintValidator.PatternKey, List<Question>> loaded =
                pools.loadCandidatePools(subject, slots);
        return (moduleId, marks, difficulty) -> {
            List<Question> pool = loaded.get(
                    new BlueprintValidator.PatternKey(moduleId, marks, difficulty));
            return pool == null ? 0 : pool.size();
        };
    }

    private static boolean validPaper(model.GeneratedPaper paper,
                                      List<BlueprintSlot> slots, int totalMarks) {
        if (paper == null || paper.getQuestions().size() != slots.size()) {
            return false;
        }
        Set<Integer> ids = new HashSet<>();
        int sum = 0;
        for (int i = 0; i < slots.size(); i++) {
            BlueprintSlot s = slots.get(i);
            GeneratedQuestion q = paper.getQuestions().get(i);
            if (q.getQuestionId() <= 0 || !ids.add(q.getQuestionId())) {
                return false;
            }
            if (q.getSequenceNo() != s.getSequenceNo()
                    || q.getModuleId() != s.getModuleId()
                    || q.getMarks() != s.getMarks()
                    || !String.valueOf(q.getDifficulty()).equals(
                            String.valueOf(s.getDifficulty()))
                    || q.getSubjectId() != subjectId) {
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

    private static List<BlueprintSlot> copyOf(List<BlueprintSlot> slots) {
        List<BlueprintSlot> copy = new ArrayList<>();
        for (BlueprintSlot slot : slots) {
            copy.add(new BlueprintSlot(slot.getSequenceNo(),
                    slot.getModuleId(), slot.getMarks()));
        }
        return copy;
    }

    private static List<String> difficultiesOf(List<BlueprintSlot> slots) {
        List<String> list = new ArrayList<>();
        for (BlueprintSlot slot : slots) {
            list.add(slot.getDifficulty());
        }
        return list;
    }

    private static int count(List<String> values, String wanted) {
        int n = 0;
        for (String value : values) {
            if (wanted.equals(value)) {
                n++;
            }
        }
        return n;
    }

    private static List<Question> allQuestions() throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null);
    }

    private static void cleanup() {
        if (subjectId <= 0) {
            return;
        }
        try {
            for (QuestionPaper p : paperDAO.getPapers()) {
                if (p.getSubjectId() == subjectId) {
                    try {
                        paperDAO.deletePaper(p.getPaperId());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
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
        for (int mid : new int[]{mA, mB, mC, mD}) {
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

    private static void checkContains(List<String> errors, String fragment, String name) {
        for (String error : errors) {
            if (error.contains(fragment)) {
                passed++;
                System.out.println("PASS: " + name);
                return;
            }
        }
        failed++;
        System.out.println("FAIL: " + name + " (missing: \"" + fragment + "\")");
        System.out.println("      errors were: " + errors);
    }

    private static void checkContainsText(String text, String fragment, String name) {
        if (text != null && text.contains(fragment)) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name + " (missing: \"" + fragment + "\")");
            System.out.println("      text was: " + text);
        }
    }

    private static void expectIllegal(ThrowingRunnable action, String name) {
        try {
            action.run();
            failed++;
            System.out.println("FAIL: " + name + " (no exception thrown)");
        } catch (IllegalArgumentException ex) {
            passed++;
            System.out.println("PASS: " + name + " -> " + ex.getMessage());
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: " + name + " (wrong exception: " + ex + ")");
        }
    }

    private static void expectIllegalContaining(ThrowingRunnable action,
                                                String fragment, String name) {
        try {
            action.run();
            failed++;
            System.out.println("FAIL: " + name + " (no exception thrown)");
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains(fragment)) {
                passed++;
                System.out.println("PASS: " + name + " -> " + ex.getMessage());
            } else {
                failed++;
                System.out.println("FAIL: " + name + " (message missing \""
                        + fragment + "\": " + ex.getMessage() + ")");
            }
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: " + name + " (wrong exception: " + ex + ")");
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
