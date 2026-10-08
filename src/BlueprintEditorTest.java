import model.BlueprintSlot;
import model.Module;
import model.Subject;
import service.BlueprintService;
import service.ModuleService;
import service.SubjectService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stage 7 test for the Question Paper Blueprint editor.
 * Uses a test subject/modules in the live DB and cleans up only its own data.
 */
public class BlueprintEditorTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final BlueprintService blueprintService = new BlueprintService();
    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();

    public static void main(String[] args) {
        int subjectId = -1;
        int module1Id = -1;
        int module2Id = -1;
        int otherSubjectId = -1;
        int otherModuleId = -1;

        List<BlueprintSlot> slots = new ArrayList<>();

        try {
            // --- Setup: own test data only ---
            subjectId = subjectService.addSubject("TEST BP Subject");
            module1Id = moduleService.addModule(subjectId, "Module 1");
            module2Id = moduleService.addModule(subjectId, "Module 2");
            otherSubjectId = subjectService.addSubject("TEST BP Other");
            otherModuleId = moduleService.addModule(otherSubjectId, "Other Module");
            check("Setup: test subjects + modules", subjectId > 0 && module1Id > 0
                    && module2Id > 0 && otherModuleId > 0);
            final int sid = subjectId;
            final int m1 = module1Id;
            final int m2 = module2Id;
            final int otherSid = otherSubjectId;
            final int otherMid = otherModuleId;

            // --- Subject selection -> module loading ---
            List<Module> modules = moduleService.getModulesBySubject(subjectId);
            check("Subject selection: modules load for selected subject", modules.size() == 2);
            List<Module> otherModules = moduleService.getModulesBySubject(otherSubjectId);
            check("Module loading: other subject has its own modules",
                    otherModules.size() == 1
                            && otherModules.get(0).getModuleName().equals("Other Module"));

            // --- Add slot ---
            BlueprintSlot s1 = blueprintService.addSlot(slots, sid, m1, 2);
            check("Add slot: sequence 1, fields stored, no difficulty yet",
                    slots.size() == 1 && s1.getSequenceNo() == 1
                            && s1.getMarks() == 2 && s1.getDifficulty() == null
                            && s1.getModuleName().equals("Module 1"));

            // --- Multiple slots with different marks ---
            blueprintService.addSlot(slots, sid, m1, 4);
            blueprintService.addSlot(slots, sid, m2, 8);
            check("Add multiple slots: 3 slots, unique sequences 1..3",
                    slots.size() == 3
                            && slots.get(0).getSequenceNo() == 1
                            && slots.get(1).getSequenceNo() == 2
                            && slots.get(2).getSequenceNo() == 3);
            check("Marks stored as-is (4 and 8), difficulties stay unassigned",
                    slots.get(1).getMarks() == 4
                            && slots.get(1).getDifficulty() == null
                            && slots.get(2).getMarks() == 8
                            && slots.get(2).getDifficulty() == null);

            // --- Positive-mark validation ---
            expectIllegal(() -> addSafe(slots, sid, m1, 0),
                    "Marks = 0 rejected");
            expectIllegal(() -> addSafe(slots, sid, m1, -5),
                    "Negative marks rejected");
            check("Invalid marks did not modify the blueprint", slots.size() == 3);

            // --- Module/subject consistency ---
            expectIllegal(() -> addSafe(slots, sid, otherMid, 2),
                    "Module from another subject rejected");
            expectIllegal(() -> addSafe(slots, otherSid, m1, 2),
                    "Subject/module mismatch rejected");
            check("Consistency checks did not modify the blueprint", slots.size() == 3);

            // --- Edit slot ---
            blueprintService.updateSlot(slots, 1, sid, m2, 6);
            check("Edit slot: module/marks updated, position kept, "
                            + "difficulty cleared for generation",
                    slots.get(1).getSequenceNo() == 2
                            && slots.get(1).getModuleId() == module2Id
                            && slots.get(1).getMarks() == 6
                            && slots.get(1).getDifficulty() == null);
            expectIllegal(() -> blueprintService.updateSlot(slots, 99, sid, m1, 2), "Edit out-of-range index rejected");
            expectIllegal(() -> {
                try {
                    blueprintService.updateSlot(slots, 0, sid, m1, 0);
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Edit with invalid marks rejected");

            // --- Reorder slots ---
            blueprintService.moveSlot(slots, 0, +1); // slot 1 <-> slot 2
            check("Reorder: move down swaps order, sequences renumbered",
                    slots.get(0).getSequenceNo() == 1
                            && slots.get(0).getMarks() == 6
                            && slots.get(1).getSequenceNo() == 2
                            && slots.get(1).getMarks() == 2);
            blueprintService.moveSlot(slots, 2, -1); // slot 3 <-> slot 2
            check("Reorder: move up swaps order, sequences renumbered",
                    slots.get(1).getSequenceNo() == 2
                            && slots.get(1).getMarks() == 8
                            && slots.get(2).getSequenceNo() == 3
                            && slots.get(2).getMarks() == 2);
            expectIllegal(() -> blueprintService.moveSlot(slots, 0, -1),
                    "Move up at top rejected");
            expectIllegal(() -> blueprintService.moveSlot(slots, 2, +1),
                    "Move down at bottom rejected");
            check("Rejected moves did not modify order", slots.size() == 3);

            // Current order: [6/Hard/m2, 8/Hard/m2, 2/Easy/m1]
            // --- Remove slot ---
            blueprintService.removeSlot(slots, 1); // remove the 8-mark slot
            check("Remove slot: size 2, sequences renumbered 1..2",
                    slots.size() == 2
                            && slots.get(0).getSequenceNo() == 1
                            && slots.get(1).getSequenceNo() == 2);
            expectIllegal(() -> blueprintService.removeSlot(slots, 5),
                    "Remove out-of-range index rejected");

            // --- Total marks calculation ---
            // Current: [6/Hard/m2, 2/Easy/m1] => total 8
            check("Total marks derived from slots = 8",
                    blueprintService.totalMarks(slots) == 8);

            // --- Module-wise totals ---
            Map<Integer, Integer> totals = blueprintService.moduleWiseTotals(slots);
            check("Module-wise totals: m2=6, m1=2",
                    totals.get(module2Id) == 6 && totals.get(module1Id) == 2);

            // Rebuild a full example from the design doc: M1=2+4, M2=8 => total 14
            List<BlueprintSlot> full = new ArrayList<>();
            blueprintService.addSlot(full, sid, m1, 2);
            blueprintService.addSlot(full, sid, m1, 4);
            blueprintService.addSlot(full, sid, m2, 8);
            check("Design example: total = 14",
                    blueprintService.totalMarks(full) == 14);
            Map<Integer, Integer> fullTotals = blueprintService.moduleWiseTotals(full);
            check("Design example: Module 1 = 6, Module 2 = 8",
                    fullTotals.get(module1Id) == 6 && fullTotals.get(module2Id) == 8);

            // --- Empty subject validation ---
            expectIllegal(() -> addSafe(slots, -1, m1, 2),
                    "Missing subject rejected");

        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            cleanup(subjectId, otherSubjectId, module1Id, module2Id, otherModuleId);
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void addSafe(List<BlueprintSlot> slots, int subjectId, int moduleId,
                                int marks) throws Exception {
        blueprintService.addSlot(slots, subjectId, moduleId, marks);
    }

    private static void cleanup(int subjectId, int otherSubjectId,
                                int m1, int m2, int otherM) {
        for (int mid : new int[]{m1, m2, otherM}) {
            if (mid > 0) {
                try {
                    moduleService.deleteModule(mid);
                } catch (Exception ignored) {
                }
            }
        }
        for (int sid : new int[]{subjectId, otherSubjectId}) {
            if (sid > 0) {
                try {
                    subjectService.deleteSubject(sid);
                } catch (Exception ignored) {
                }
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

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
