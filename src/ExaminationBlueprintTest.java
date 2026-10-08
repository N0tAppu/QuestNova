import model.BlueprintSlot;
import model.ExamBlueprint;
import model.ExamPatternRow;
import service.BlueprintService;
import service.ModuleService;
import service.SubjectService;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 7 (redesigned) test for the Examination Blueprint logic:
 * module allocations, question patterns, slot conversion and the full
 * structural validation. Uses a test subject/modules in the live DB and
 * cleans up only its own data.
 */
public class ExaminationBlueprintTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final BlueprintService blueprintService = new BlueprintService();
    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();

    public static void main(String[] args) {
        int subjectId = -1;
        int algebraId = -1;
        int geometryId = -1;
        int trigId = -1;

        try {
            // --- Setup: own test data only ---
            subjectId = subjectService.addSubject("TEST EXAM BP");
            algebraId = moduleService.addModule(subjectId, "Algebra");
            geometryId = moduleService.addModule(subjectId, "Geometry");
            trigId = moduleService.addModule(subjectId, "Trigonometry");
            check("Setup: test subject + 3 modules", subjectId > 0 && algebraId > 0
                    && geometryId > 0 && trigId > 0);
            final int A = algebraId;
            final int G = geometryId;
            final int T = trigId;

            // ================= percentage (derived, never editable) =================
            check("Percentage 15/60 = 25%", blueprintService.percentage(15, 60) == 25.0);
            check("Percentage with total 0 = 0 (no division by zero)",
                    blueprintService.percentage(5, 0) == 0.0);

            // ================= patternTotal =================
            List<ExamPatternRow> rows = new ArrayList<>();
            rows.add(new ExamPatternRow(2, 3));
            rows.add(new ExamPatternRow(4, 2));
            check("Pattern total = 6 + 8 = 14", blueprintService.patternTotal(rows) == 14);
            check("Pattern total of null list = 0", blueprintService.patternTotal(null) == 0);

            // ================= addPatternRow =================
            ExamBlueprint exam = new ExamBlueprint();
            exam.selectModule(A, "Algebra");
            exam.selectModule(G, "Geometry");

            ExamPatternRow added = blueprintService.addPatternRow(exam, A, 2, 3);
            check("Add pattern row: fields stored",
                    exam.getPatternRows(A).size() == 1
                            && added.getMarks() == 2
                            && added.getQuestionCount() == 3
                            && added.getTotalMarks() == 6);

            expectIllegal(() -> blueprintService.addPatternRow(exam, A, 0, 1),
                    "Pattern marks = 0 rejected");
            expectIllegal(() -> blueprintService.addPatternRow(exam, A, -2, 1),
                    "Negative pattern marks rejected");
            expectIllegal(() -> blueprintService.addPatternRow(exam, A, 2, 0),
                    "Question count = 0 rejected");
            expectIllegal(() -> blueprintService.addPatternRow(exam, A, 2, -1),
                    "Negative question count rejected");
            expectIllegalContaining(
                    () -> blueprintService.addPatternRow(exam, A, 2, 5),
                    "combine", "Duplicate marks row rejected with guidance");
            expectIllegal(() -> blueprintService.addPatternRow(exam, T, 2, 1),
                    "Pattern for unselected module rejected");
            check("Rejected adds did not modify the pattern",
                    exam.getPatternRows(A).size() == 1);

            // ================= updatePatternRow =================
            blueprintService.addPatternRow(exam, A, 3, 1);
            blueprintService.updatePatternRow(exam, A, 0, 5, 2);
            check("Edit row 0: values updated, position kept",
                    exam.getPatternRows(A).get(0).getMarks() == 5
                            && exam.getPatternRows(A).get(0).getQuestionCount() == 2
                            && exam.getPatternRows(A).get(1).getMarks() == 3
                            && exam.getPatternRows(A).get(1).getQuestionCount() == 1);
            expectIllegal(() -> blueprintService.updatePatternRow(exam, A, 99, 1, 1),
                    "Edit out-of-range row rejected");
            expectIllegalContaining(
                    () -> blueprintService.updatePatternRow(exam, A, 0, 3, 1),
                    "already exists", "Edit creating a duplicate row rejected");
            expectIllegal(() -> blueprintService.updatePatternRow(exam, T, 0, 1, 1),
                    "Edit of unselected module rejected");

            // ================= removePatternRow =================
            blueprintService.removePatternRow(exam, A, 1);
            check("Remove row: size 1 remains",
                    exam.getPatternRows(A).size() == 1
                            && exam.getPatternRows(A).get(0).getMarks() == 5);
            expectIllegal(() -> blueprintService.removePatternRow(exam, A, 5),
                    "Remove out-of-range row rejected");
            expectIllegal(() -> blueprintService.removePatternRow(exam, T, 0),
                    "Remove of unselected module rejected");

            // ================= generateSlots =================
            ExamBlueprint gen = new ExamBlueprint();
            gen.setSubjectId(1);
            gen.setExamType(ExamBlueprint.TYPE_SEMESTER);
            gen.setExamDifficulty("Easy");
            gen.setTotalMarks(20);
            gen.selectModule(G, "Geometry");
            gen.selectModule(A, "Algebra");
            gen.setAllocation(G, 10);
            gen.setAllocation(A, 10);
            blueprintService.addPatternRow(gen, G, 10, 1);
            blueprintService.addPatternRow(gen, A, 2, 5);

            List<BlueprintSlot> slots = blueprintService.generateSlots(gen);
            check("Slot conversion: 1 + 5 questions = 6 slots", slots.size() == 6);
            check("Slot order follows syllabus selection order (Geometry first)",
                    slots.get(0).getModuleId() == G
                            && slots.get(1).getModuleId() == A
                            && slots.get(5).getModuleId() == A);
            check("Sequence numbers continuous from 1",
                    slots.get(0).getSequenceNo() == 1
                            && slots.get(5).getSequenceNo() == 6);
            check("Question count expanded (5 x 2-mark Algebra slots)",
                    countSlots(slots, A, 2, null) == 5
                            && countSlots(slots, G, 10, null) == 1);
            check("Derived slots carry NO difficulty (assigned at generation)",
                    allDifficultiesNull(slots));
            check("Slot module names filled in",
                    slots.get(0).getModuleName().equals("Geometry")
                            && slots.get(1).getModuleName().equals("Algebra"));
            check("Unselected module never appears in slots",
                    countSlots(slots, T, 0, null) == 0);
            check("Valid blueprint: no validation errors",
                    blueprintService.validateBlueprint(gen).isEmpty()
                            && blueprintService.isValidBlueprint(gen));
            check("Slot marks total equals examination total (20)",
                    blueprintService.totalMarks(slots) == 20);

            // ================= validateBlueprint: empty =================
            ExamBlueprint empty = new ExamBlueprint();
            List<String> errs = blueprintService.validateBlueprint(empty);
            checkContains("Empty: subject required", errs, "Please select a subject.");
            checkContains("Empty: examination type required", errs,
                    "Please select an examination type.");
            checkContains("Empty: total marks required", errs,
                    "Examination total marks must be a positive whole number.");
            checkContains("Empty: modules required", errs,
                    "Select at least one syllabus module.");

            // ================= allocation sum != examination total =================
            ExamBlueprint sum = new ExamBlueprint();
            sum.setSubjectId(subjectId);
            sum.setExamType(ExamBlueprint.TYPE_SEMESTER);
            sum.setExamDifficulty("Medium");
            sum.setTotalMarks(60);
            sum.selectModule(A, "Algebra");
            sum.selectModule(G, "Geometry");
            sum.setAllocation(A, 10);
            sum.setAllocation(G, 20);
            blueprintService.addPatternRow(sum, A, 2, 5);
            blueprintService.addPatternRow(sum, G, 10, 2);
            errs = blueprintService.validateBlueprint(sum);
            checkContains("Allocation sum mismatch message is precise", errs,
                    "Module allocations total 30 marks, but the examination total marks is 60.");
            check("Allocation sum mismatch: it is the only problem", errs.size() == 1);

            // ================= pattern total != module allocation =================
            ExamBlueprint pat = new ExamBlueprint();
            pat.setSubjectId(subjectId);
            pat.setExamType(ExamBlueprint.TYPE_CAT);
            pat.setExamDifficulty("Hard");
            pat.setTotalMarks(60);
            pat.selectModule(A, "Algebra");
            pat.selectModule(G, "Geometry");
            pat.setAllocation(A, 16);
            pat.setAllocation(G, 44);
            blueprintService.addPatternRow(pat, A, 2, 7);
            blueprintService.addPatternRow(pat, G, 10, 4);
            errs = blueprintService.validateBlueprint(pat);
            checkContains("Module pattern mismatch names module and both values", errs,
                    "Algebra pattern total is 14 marks, but its allocated marks are 16.");
            checkContains("Second module mismatch reported too", errs,
                    "Geometry pattern total is 40 marks, but its allocated marks are 44.");

            // ================= module without any pattern row =================
            ExamBlueprint noRows = new ExamBlueprint();
            noRows.setSubjectId(subjectId);
            noRows.setExamType(ExamBlueprint.TYPE_OTHER);
            noRows.setExamDifficulty("Easy");
            noRows.setTotalMarks(60);
            noRows.selectModule(A, "Algebra");
            noRows.selectModule(G, "Geometry");
            noRows.setAllocation(A, 30);
            noRows.setAllocation(G, 30);
            errs = blueprintService.validateBlueprint(noRows);
            checkContains("Missing pattern rows reported per module", errs,
                    "Algebra: add at least one question-pattern row.");
            checkContains("Missing pattern rows reported for both modules", errs,
                    "Geometry: add at least one question-pattern row.");

            // ================= zero allocation =================
            ExamBlueprint zero = new ExamBlueprint();
            zero.setSubjectId(subjectId);
            zero.setExamType(ExamBlueprint.TYPE_SEMESTER);
            zero.setExamDifficulty("Medium");
            zero.setTotalMarks(60);
            zero.selectModule(A, "Algebra");
            zero.setAllocation(A, 0);
            blueprintService.addPatternRow(zero, A, 2, 3);
            errs = blueprintService.validateBlueprint(zero);
            checkContains("Zero allocation rejected by validation", errs,
                    "Algebra: allocated marks must be a positive whole number.");

            // ================= missing examination difficulty =================
            ExamBlueprint noDifficulty = new ExamBlueprint();
            noDifficulty.setSubjectId(subjectId);
            noDifficulty.setExamType(ExamBlueprint.TYPE_SEMESTER);
            noDifficulty.setTotalMarks(60);
            noDifficulty.selectModule(A, "Algebra");
            noDifficulty.setAllocation(A, 60);
            blueprintService.addPatternRow(noDifficulty, A, 2, 30);
            errs = blueprintService.validateBlueprint(noDifficulty);
            checkContains("Missing examination difficulty reported", errs,
                    "Please select an examination difficulty (Easy, Medium or Hard).");

            // ================= duplicate pattern rows (bypassing service guard) ====
            ExamBlueprint dup = new ExamBlueprint();
            dup.setSubjectId(subjectId);
            dup.setExamType(ExamBlueprint.TYPE_SEMESTER);
            dup.setExamDifficulty("Hard");
            dup.setTotalMarks(60);
            dup.selectModule(A, "Algebra");
            dup.selectModule(G, "Geometry");
            dup.setAllocation(A, 20);
            dup.setAllocation(G, 40);
            dup.getPatternRows(A).add(new ExamPatternRow(2, 5));
            dup.getPatternRows(A).add(new ExamPatternRow(2, 3));
            dup.getPatternRows(G).add(new ExamPatternRow(10, 4));
            errs = blueprintService.validateBlueprint(dup);
            checkContains("Duplicate rows detected by validation with guidance", errs,
                    "Algebra: duplicate pattern rows for 2 marks");

            // ================= no hard-coded rules =================
            ExamBlueprint custom = new ExamBlueprint();
            custom.setSubjectId(subjectId);
            custom.setExamType(ExamBlueprint.TYPE_OTHER);
            custom.setExamDifficulty("Medium");
            custom.setTotalMarks(45);
            custom.selectModule(A, "Algebra");
            custom.selectModule(G, "Geometry");
            custom.selectModule(T, "Trigonometry");
            custom.setAllocation(A, 15);
            custom.setAllocation(G, 15);
            custom.setAllocation(T, 15);
            blueprintService.addPatternRow(custom, A, 15, 1);
            blueprintService.addPatternRow(custom, G, 1, 15);
            blueprintService.addPatternRow(custom, T, 5, 3);
            check("Arbitrary total (45) with exam type 'Other' is VALID",
                    blueprintService.isValidBlueprint(custom));
            check("No hard-coded marks rules (15-mark and 1-mark rows both valid)",
                    blueprintService.validateBlueprint(custom).isEmpty());
            List<BlueprintSlot> customSlots = blueprintService.generateSlots(custom);
            check("Custom blueprint derived slots: 19 questions, 45 marks",
                    customSlots.size() == 19
                            && blueprintService.totalMarks(customSlots) == 45);
            check("Custom slots keep marks but carry no difficulty",
                    allDifficultiesNull(customSlots)
                            && countSlots(customSlots, A, 15, null) == 1
                            && countSlots(customSlots, G, 1, null) == 15
                            && countSlots(customSlots, T, 5, null) == 3);

            // ================= examination difficulty (Stage 12) ==============
            ExamBlueprint diffExam = new ExamBlueprint();
            diffExam.setSubjectId(subjectId);
            diffExam.setExamType(ExamBlueprint.TYPE_SEMESTER);
            diffExam.setTotalMarks(60);
            diffExam.selectModule(A, "Algebra");
            diffExam.setAllocation(A, 60);
            blueprintService.addPatternRow(diffExam, A, 2, 30);
            diffExam.setExamDifficulty("Hard");
            check("Exam difficulty set -> structural validation passes",
                    blueprintService.validateBlueprint(diffExam).isEmpty());
            diffExam.setExamDifficulty("");
            check("Exam difficulty cleared -> validation demands it again",
                    blueprintService.validateBlueprint(diffExam).size() == 1
                            && blueprintService.validateBlueprint(diffExam).get(0)
                                    .contains("examination difficulty"));

        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            cleanup(subjectId, algebraId, geometryId, trigId);
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static int countSlots(List<BlueprintSlot> slots, int moduleId,
                                  int marks, String difficulty) {
        int count = 0;
        for (BlueprintSlot slot : slots) {
            if (slot.getModuleId() != moduleId) {
                continue;
            }
            if (marks > 0 && slot.getMarks() != marks) {
                continue;
            }
            if (difficulty != null && !difficulty.equals(slot.getDifficulty())) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static boolean allDifficultiesNull(List<BlueprintSlot> slots) {
        for (BlueprintSlot slot : slots) {
            if (slot.getDifficulty() != null) {
                return false;
            }
        }
        return true;
    }

    private static void cleanup(int subjectId, int m1, int m2, int m3) {
        for (int mid : new int[]{m1, m2, m3}) {
            if (mid > 0) {
                try {
                    moduleService.deleteModule(mid);
                } catch (Exception ignored) {
                }
            }
        }
        if (subjectId > 0) {
            try {
                subjectService.deleteSubject(subjectId);
            } catch (Exception ignored) {
            }
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

    private static void checkContains(String name, List<String> errors, String fragment) {
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
