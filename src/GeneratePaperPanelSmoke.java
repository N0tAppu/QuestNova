import model.ExamBlueprint;
import model.Module;
import model.Subject;
import service.ModuleService;
import service.QuestionService;
import ui.GeneratePaperPanel;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Stage 7 GUI smoke test for the redesigned Examination Blueprint Editor.
 *
 * Builds the real panel on the EDT and drives it through its public facade
 * (the same code paths the UI handlers use) against an existing subject in
 * the live database. Read-only: no data is written or changed.
 */
public class GeneratePaperPanelSmoke {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        int foundSubject = -1;
        int idA = -1;
        int idB = -1;

        try {
            QuestionService questionService = new QuestionService();
            ModuleService moduleService = new ModuleService();

            for (Subject s : questionService.getAllSubjects()) {
                List<Module> modules = moduleService.getModulesBySubject(s.getSubjectId());
                if (modules.size() >= 2) {
                    foundSubject = s.getSubjectId();
                    idA = modules.get(0).getModuleId();
                    idB = modules.get(1).getModuleId();
                    break;
                }
            }
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: could not read the database -> " + ex);
            System.exit(1);
            return;
        }

        if (foundSubject <= 0) {
            System.out.println("SKIP: no subject with at least 2 modules found.");
            System.exit(0);
            return;
        }

        final int subjectId = foundSubject;
        final int moduleA = idA;
        final int moduleB = idB;
        System.out.println("Using subject " + subjectId + " with modules "
                + moduleA + ", " + moduleB + " (read-only).");

        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    drivePanel(subjectId, moduleA, moduleB);
                } catch (Exception ex) {
                    failed++;
                    System.out.println("FAIL: unexpected exception in panel -> " + ex);
                    ex.printStackTrace();
                }
            });
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: could not run on the EDT -> " + ex);
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void drivePanel(int subjectId, int moduleA, int moduleB) {
        GeneratePaperPanel panel = new GeneratePaperPanel();
        check("Panel constructs without exception", panel != null);

        panel.refreshPanel();
        check("refreshPanel runs without exception", true);

        panel.selectSubject(subjectId);
        check("Subject selected", panel.getSelectedSubjectId() == subjectId);

        panel.setExamType(ExamBlueprint.TYPE_SEMESTER);
        panel.setExamDifficulty("Medium");
        panel.setTotalMarks(40);
        check("Examination details stored",
                panel.getExam().getExamType().equals(ExamBlueprint.TYPE_SEMESTER)
                        && panel.getExam().getExamDifficulty().equals("Medium")
                        && panel.getExam().getTotalMarks() == 40);

        panel.addModulesToSyllabus(moduleA, moduleB);
        check("Two modules selected", panel.getExam().getSelectedModuleIds().size() == 2);

        panel.setModuleAllocation(moduleA, 20);
        panel.setModuleAllocation(moduleB, 20);
        check("Allocations stored and sum = 40",
                panel.getExam().getAllocation(moduleA) == 20
                        && panel.getExam().getAllocation(moduleB) == 20);

        panel.addPattern(moduleA, 4, 2);
        panel.addPattern(moduleA, 6, 2);
        panel.addPattern(moduleB, 10, 2);
        check("Pattern rows stored", panel.getExam().getPatternRows(moduleA).size() == 2
                && panel.getExam().getPatternRows(moduleB).size() == 1);

        check("Complete blueprint is VALID", panel.getValidationErrors().isEmpty());
        check("Derived slots: 6 questions", panel.getSlots().size() == 6);
        check("Derived slots total 40 marks", totalMarks(panel) == 40);
        check("Preview table shows 6 rows", panel.getPreviewRowCount() == 6);
        check("Summary shows VALID", panel.getSummaryText().contains("VALID")
                && !panel.getSummaryText().contains("NOT VALID"));

        boolean rejected = false;
        try {
            panel.addPattern(moduleB, 10, 1);
        } catch (IllegalArgumentException ex) {
            rejected = ex.getMessage().contains("already exists");
        }
        check("Duplicate pattern row rejected by the UI facade", rejected);

        panel.setModuleAllocation(moduleA, 15);
        List<String> errors = panel.getValidationErrors();
        check("Allocation mismatch detected with a precise message",
                contains(errors, "pattern total is 20 marks, but its allocated marks are 15."));
        check("Summary shows NOT VALID", panel.getSummaryText().contains("NOT VALID"));

        panel.setModuleAllocation(moduleA, 20);
        check("Blueprint valid again after fixing the allocation",
                panel.getValidationErrors().isEmpty());
        check("Summary back to VALID", panel.getSummaryText().contains("VALID"));
    }

    private static int totalMarks(GeneratePaperPanel panel) {
        int total = 0;
        for (model.BlueprintSlot slot : panel.getSlots()) {
            total += slot.getMarks();
        }
        return total;
    }

    private static boolean contains(List<String> errors, String fragment) {
        for (String error : errors) {
            if (error.contains(fragment)) {
                return true;
            }
        }
        return false;
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
