import model.Module;
import model.Subject;
import service.ModuleService;
import service.SubjectService;

import java.sql.SQLException;
import java.util.List;

/**
 * Stage 4 CRUD test for Subject/Module management.
 * Runs against the live question_paper_db and cleans up after itself.
 */
public class SubjectModuleCrudTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        SubjectService subjectService = new SubjectService();
        ModuleService moduleService = new ModuleService();

        int subjectId = -1;
        int subjectId2 = -1;
        int moduleId = -1;

        try {
            // 1. Add subject
            subjectId = subjectService.addSubject("TEST Subject Alpha");
            check("Add subject", subjectId > 0);

            // 2. Duplicate subject rejected
            expectIllegal(() -> {
                try {
                    subjectService.addSubject("TEST Subject Alpha");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Duplicate subject rejected");

            // 3. Empty subject rejected
            expectIllegal(() -> {
                try {
                    subjectService.addSubject("   ");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Empty subject rejected");

            // 4. List subjects contains new subject
            boolean found = false;
            for (Subject s : subjectService.getAllSubjects()) {
                if (s.getSubjectId() == subjectId) {
                    found = true;
                }
            }
            check("Subject appears in list", found);

            // 5. Add module
            final int sid = subjectId;
            moduleId = moduleService.addModule(sid, "TEST Module 1");
            check("Add module", moduleId > 0);

            // 6. Duplicate module in same subject rejected
            expectIllegal(() -> {
                try {
                    moduleService.addModule(sid, "TEST Module 1");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Duplicate module rejected");

            // 7. Module list for subject
            List<Module> modules = moduleService.getModulesBySubject(subjectId);
            check("Module list contains module", modules.size() == 1);

            // 8. Update subject name
            subjectService.updateSubject(subjectId, "TEST Subject Beta");
            boolean renamed = false;
            for (Subject s : subjectService.getAllSubjects()) {
                if (s.getSubjectId() == subjectId && s.getSubjectName().equals("TEST Subject Beta")) {
                    renamed = true;
                }
            }
            check("Update subject name", renamed);

            // 9. Update module name
            moduleService.updateModule(moduleId, subjectId, "TEST Module Renamed");
            modules = moduleService.getModulesBySubject(subjectId);
            check("Update module name",
                    modules.size() == 1 && modules.get(0).getModuleName().equals("TEST Module Renamed"));

            // 10. Delete subject with modules blocked
            final int sidWithModules = subjectId;
            expectIllegal(() -> {
                try {
                    subjectService.deleteSubject(sidWithModules);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Delete subject with modules blocked");

            // 11. Second subject for safe-delete test
            subjectId2 = subjectService.addSubject("TEST Subject Gamma");
            check("Add second subject", subjectId2 > 0);

            // 12. Delete subject without modules succeeds
            subjectService.deleteSubject(subjectId2);
            boolean gone = true;
            for (Subject s : subjectService.getAllSubjects()) {
                if (s.getSubjectId() == subjectId2) {
                    gone = false;
                }
            }
            check("Delete empty subject", gone);

            // 13. Delete module then subject
            moduleService.deleteModule(moduleId);
            modules = moduleService.getModulesBySubject(subjectId);
            check("Delete module", modules.isEmpty());
            subjectService.deleteSubject(subjectId);
            gone = true;
            for (Subject s : subjectService.getAllSubjects()) {
                if (s.getSubjectId() == subjectId) {
                    gone = false;
                }
            }
            check("Delete subject after modules removed", gone);

        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            // Cleanup in case of partial failure
            cleanup(subjectService, moduleService, moduleId, subjectId);
            cleanup(subjectService, moduleService, -1, subjectId2);
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void cleanup(SubjectService subjectService, ModuleService moduleService,
                                int moduleId, int subjectId) {
        if (subjectId < 0) {
            return;
        }
        try {
            if (moduleId >= 0) {
                moduleService.deleteModule(moduleId);
            }
            subjectService.deleteSubject(subjectId);
        } catch (Exception ignored) {
            // best-effort cleanup
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

    private static void expectIllegal(Runnable action, String name) {
        try {
            action.run();
            failed++;
            System.out.println("FAIL: " + name + " (no exception thrown)");
        } catch (IllegalArgumentException ex) {
            passed++;
            System.out.println("PASS: " + name + " -> " + ex.getMessage());
        } catch (RuntimeException ex) {
            failed++;
            System.out.println("FAIL: " + name + " (wrong exception: " + ex + ")");
        }
    }
}
