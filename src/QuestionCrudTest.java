import model.Module;
import model.Question;
import model.Subject;
import service.ModuleService;
import service.QuestionService;
import service.SubjectService;

import java.sql.SQLException;
import java.util.List;

/**
 * Stage 5 CRUD + search/filter test for Question Management.
 * Runs against the live question_paper_db and cleans up after itself.
 */
public class QuestionCrudTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        SubjectService subjectService = new SubjectService();
        ModuleService moduleService = new ModuleService();
        QuestionService questionService = new QuestionService();

        int subjectId = -1;
        int module1Id = -1;
        int module2Id = -1;
        int q1Id = -1;
        int q2Id = -1;
        int q3Id = -1;

        try {
            // --- Setup: subject with two modules ---
            subjectId = subjectService.addSubject("TEST Java Programming");
            module1Id = moduleService.addModule(subjectId, "Module 1");
            module2Id = moduleService.addModule(subjectId, "Module 2");
            check("Setup: subject + 2 modules", subjectId > 0 && module1Id > 0 && module2Id > 0);
            final int sid = subjectId;
            final int m1 = module1Id;
            final int m2 = module2Id;
            // Global count before this test adds anything (other data may exist)
            final int globalBefore =
                    questionService.searchQuestions(null, null, null, null, null).size();

            // --- Add questions ---
            q1Id = questionService.addQuestion(makeQuestion(sid, m1,
                    "What is inheritance?", "Inheritance allows code reuse.", 2, "Easy"));
            check("Add question (2 marks, Easy)", q1Id > 0);

            q2Id = questionService.addQuestion(makeQuestion(sid, m1,
                    "Explain polymorphism.", "Polymorphism is one interface many forms.", 4, "Hard"));
            check("Add question (4 marks, Hard) - marks/difficulty independent",
                    q2Id > 0);

            q3Id = questionService.addQuestion(makeQuestion(sid, m2,
                    "Explain exception handling.", "Exception handling manages errors.", 8, "Medium"));
            check("Add question (8 marks, Medium)", q3Id > 0);

            // --- Validation: empty question / answer ---
            expectIllegal(() -> addSafe(questionService, makeQuestion(sid, m1,
                    "  ", "answer", 2, "Easy")), "Empty question text rejected");
            expectIllegal(() -> addSafe(questionService, makeQuestion(sid, m1,
                    "Q?", "   ", 2, "Easy")), "Empty answer rejected");

            // --- Validation: marks ---
            expectIllegal(() -> addSafe(questionService, makeQuestion(sid, m1,
                    "Q marks zero?", "A", 0, "Easy")), "Marks = 0 rejected");
            expectIllegal(() -> addSafe(questionService, makeQuestion(sid, m1,
                    "Q marks neg?", "A", -3, "Easy")), "Negative marks rejected");

            // --- Validation: difficulty ---
            expectIllegal(() -> addSafe(questionService, makeQuestion(sid, m1,
                    "Q diff?", "A", 2, "Extreme")), "Invalid difficulty rejected");

            // --- Validation: module must belong to subject ---
            // (module2 belongs to subjectId, so this one is valid - add and delete)
            int crossId = questionService.addQuestion(makeQuestion(sid, m2,
                    "Q cross-module?", "A", 2, "Easy"));
            check("Valid cross module add", crossId > 0);
            questionService.deleteQuestion(crossId);

            // Module belonging to a DIFFERENT subject must be rejected
            int otherSubjectId = subjectService.addSubject("TEST Other Subject");
            int otherModuleId = moduleService.addModule(otherSubjectId, "Other Module");
            final int foreignModule = otherModuleId;
            final int ownSubject = subjectId;
            expectIllegal(() -> addSafe(questionService, makeQuestion(ownSubject, foreignModule,
                    "Q foreign module?", "A", 2, "Easy")),
                    "Module from another subject rejected");
            moduleService.deleteModule(otherModuleId);
            subjectService.deleteSubject(otherSubjectId);

            // --- Duplicate detection (normalized) ---
            expectIllegal(() -> addSafe(questionService, makeQuestion(sid, m1,
                    "  What   is inheritance? ", "different answer", 2, "Easy")),
                    "Duplicate question rejected (normalized)");

            // --- Search/filter tests ---
            List<Question> all = questionService.searchQuestions(null, null, null, null, null);
            check("Filter: all questions = baseline + 3", all.size() == globalBefore + 3);

            List<Question> bySubject = questionService.searchQuestions(subjectId, null, null, null, null);
            check("Filter: by subject = 3", bySubject.size() == 3);

            List<Question> byModule = questionService.searchQuestions(null, module2Id, null, null, null);
            check("Filter: by module (Module 2) = 1", byModule.size() == 1);

            List<Question> byMarks = questionService.searchQuestions(subjectId, null, 4, null, null);
            check("Filter: by marks (4) = 1", byMarks.size() == 1);

            List<Question> byDifficulty = questionService.searchQuestions(
                    subjectId, null, null, "Hard", null);
            check("Filter: by difficulty (Hard) = 1", byDifficulty.size() == 1);

            List<Question> byText = questionService.searchQuestions(
                    subjectId, null, null, null, "inheritance");
            check("Filter: by text search = 1", byText.size() == 1);

            List<Question> combined = questionService.searchQuestions(subjectId, module1Id, 2, "Easy", "inheritance");
            check("Filter: combined subject+module+marks+difficulty+text = 1", combined.size() == 1);

            List<Question> none = questionService.searchQuestions(null, null, 99, null, null);
            check("Filter: no match = 0", none.isEmpty());

            // --- Invalid filter values ---
            expectIllegal(() -> {
                try {
                    questionService.searchQuestions(null, null, null, "Impossible", null);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Invalid difficulty filter rejected");
            expectIllegal(() -> {
                try {
                    questionService.searchQuestions(null, null, -1, null, null);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Invalid marks filter rejected");

            // --- Update ---
            Question updated = questionService.getQuestionById(q2Id);
            updated.setMarks(5);
            updated.setDifficulty("Medium");
            updated.setQuestionText("Explain runtime polymorphism.");
            updated.setModuleId(module2Id);
            questionService.updateQuestion(updated);
            Question reloaded = questionService.getQuestionById(q2Id);
            check("Update: marks 4->5, diff Hard->Medium, text, module",
                    reloaded.getMarks() == 5
                            && reloaded.getDifficulty().equals("Medium")
                            && reloaded.getQuestionText().equals("Explain runtime polymorphism.")
                            && reloaded.getModuleId() == module2Id);

            // --- Update duplicate rejection ---
            Question dup = questionService.getQuestionById(q2Id);
            dup.setQuestionText("What is inheritance?"); // same as q1
            final Question dupFinal = dup;
            expectIllegal(() -> {
                try {
                    questionService.updateQuestion(dupFinal);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Update duplicate rejected");

            // --- Delete ---
            questionService.deleteQuestion(q3Id);
            all = questionService.searchQuestions(null, null, null, null, null);
            check("Delete question", all.size() == globalBefore + 2);
            q3Id = -1;

            // --- Delete missing question rejected ---
            expectIllegal(() -> {
                try {
                    questionService.deleteQuestion(999999);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, "Delete missing question rejected");

            // --- Bulk preview / delete (nothing pins these questions) ---
            int bulk1 = questionService.addQuestion(makeQuestion(sid, m1,
                    "Bulk preview question one?", "A", 2, "Easy"));
            int bulk2 = questionService.addQuestion(makeQuestion(sid, m1,
                    "Bulk preview question two?", "A", 4, "Medium"));
            check("Bulk: two extra questions added", bulk1 > 0 && bulk2 > 0);

            List<Integer> bulkIds = new java.util.ArrayList<>();
            bulkIds.add(bulk1);
            bulkIds.add(bulk2);

            QuestionService.BulkDeletePlan plan = questionService.previewDelete(bulkIds);
            check("Bulk preview: both unpinned questions are deletable",
                    plan.getDeletable().size() == 2 && plan.getKept().isEmpty()
                            && plan.getKeptPapers().isEmpty());

            QuestionService.BulkDeleteResult emptyResult =
                    questionService.deleteQuestions(new java.util.ArrayList<>());
            check("Bulk delete: an empty list deletes nothing",
                    emptyResult.getDeleted() == 0 && emptyResult.getKept().isEmpty());

            QuestionService.BulkDeleteResult bulkResult =
                    questionService.deleteQuestions(bulkIds);
            check("Bulk delete: both questions are gone",
                    bulkResult.getDeleted() == 2
                            && questionService.searchQuestions(
                                    subjectId, null, null, null, null).size() == 2);

            // --- Clear all questions of the subject ---
            QuestionService.BulkDeleteResult clearResult =
                    questionService.deleteAllForSubject(subjectId);
            check("Clear subject: every remaining question of the subject is removed",
                    clearResult.getDeleted() == 2 && clearResult.getKept().isEmpty()
                            && questionService.searchQuestions(
                                    subjectId, null, null, null, null).isEmpty());
            check("Clear subject: other subjects are untouched",
                    questionService.searchQuestions(null, null, null, null, null).size()
                            == globalBefore);
            q1Id = -1;
            q2Id = -1;

        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            // --- Cleanup (remove test data from the live DB) ---
            cleanup(questionService, moduleService, subjectService, q1Id, q2Id, q3Id,
                    module1Id, module2Id, subjectId);
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static Question makeQuestion(int subjectId, int moduleId, String text, String answer,
                                         int marks, String difficulty) {
        Question q = new Question();
        q.setSubjectId(subjectId);
        q.setModuleId(moduleId);
        q.setQuestionText(text);
        q.setAnswerText(answer);
        q.setMarks(marks);
        q.setDifficulty(difficulty);
        return q;
    }

    private static void addSafe(QuestionService service, Question q) {
        try {
            service.addQuestion(q);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanup(QuestionService questionService, ModuleService moduleService,
                                SubjectService subjectService, int q1, int q2, int q3,
                                int m1, int m2, int subjectId) {
        // Remove EVERY question of the test subject first: local ids of a
        // section that aborted early would otherwise block module deletion.
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
        }
        for (int qid : new int[]{q1, q2, q3}) {
            if (qid > 0) {
                try {
                    questionService.deleteQuestion(qid);
                } catch (Exception ignored) {
                }
            }
        }
        for (int mid : new int[]{m1, m2}) {
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
