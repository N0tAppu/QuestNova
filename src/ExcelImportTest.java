import model.Question;
import model.Subject;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import service.ExcelImportService;
import service.QuestionService;
import service.SubjectService;
import service.ModuleService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Stage 6 test for Excel Question Bank Import.
 * Creates its own xlsx files, runs against live question_paper_db,
 * and cleans up only its own test data.
 */
public class ExcelImportTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final ExcelImportService importService = new ExcelImportService();
    private static final QuestionService questionService = new QuestionService();
    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();

    private static File dir;

    public static void main(String[] args) {
        int subjectId = -1;
        int module1Id = -1;
        int module2Id = -1;
        int subject2Id = -1;
        int subject2ModuleId = -1;

        try {
            dir = Files.createTempDirectory("xlsxtest").toFile();

            // --- Setup: own subject + modules (user data untouched) ---
            subjectId = subjectService.addSubject("TEST Import Subject");
            module1Id = moduleService.addModule(subjectId, "Module 1");
            module2Id = moduleService.addModule(subjectId, "Module 2");
            check("Setup: test subject + 2 modules", subjectId > 0 && module1Id > 0 && module2Id > 0);

            // Second subject carrying a question whose text matches a
            // subject-1 question: proves matching/deleting stays subject-scoped.
            subject2Id = subjectService.addSubject("TEST Import Subject 2");
            subject2ModuleId = moduleService.addModule(subject2Id, "Module 1");
            Question sameText = new Question();
            sameText.setSubjectId(subject2Id);
            sameText.setModuleId(subject2ModuleId);
            sameText.setQuestionText("What is OOP?");
            sameText.setAnswerText("Answer for the other subject");
            sameText.setMarks(2);
            sameText.setDifficulty("Easy");
            int sameTextId = questionService.addQuestion(sameText);
            check("Setup: second subject with a same-text question",
                    subject2Id > 0 && subject2ModuleId > 0 && sameTextId > 0);

            // =========================================================
            // 1. VALID IMPORT (all rows valid)
            // =========================================================
            File validFile = write("valid.xlsx",
                    new String[]{"Question", "Answer", "Module", "Marks", "Difficulty"},
                    new String[][]{
                            {"What is OOP?", "Object oriented programming", "Module 1", "2", "Easy"},
                            {"Explain classes.", "A class is a blueprint", "Module 1", "4", "Medium"},
                            {"Explain lists.", "Lists store elements", "Module 2", "8", "Hard"}
                    });
            ExcelImportService.ImportReport r = importService.validate(validFile, subjectId);
            check("T1: valid file - header ok", r.isHeaderValid());
            check("T1: valid file - 3 total / 3 valid / 0 invalid",
                    r.getTotalRows() == 3 && r.getValidCount() == 3 && r.getInvalidCount() == 0);
            check("T1: valid file - allValid()", r.allValid());
            int imported = importService.importValidRows(r);
            check("T1: Import All inserts 3", imported == 3);
            check("T1: DB now has 3 questions", countQuestions(subjectId) == 3);

            // =========================================================
            // 7. DUPLICATES AGAINST EXISTING DB QUESTIONS
            // =========================================================
            File dbDupFile = write("dbdup.xlsx",
                    new String[]{"Question", "Answer", "Module", "Marks", "Difficulty"},
                    new String[][]{
                            {"What is OOP?", "Different answer", "Module 1", "2", "Easy"},
                            {"Brand new question?", "A", "Module 1", "2", "Easy"}
                    });
            r = importService.validate(dbDupFile, subjectId);
            check("T7: DB duplicate detected",
                    r.getInvalidCount() == 1 && r.getValidCount() == 1
                            && r.getErrors().get(0).getMessage().contains("already exists in the question bank"));
            check("T7: DB unchanged after validate-only (Cancel behavior)", countQuestions(subjectId) == 3);

            // =========================================================
            // 11. CANCEL LEAVES DATABASE UNCHANGED
            // =========================================================
            int before = countQuestions(subjectId);
            r = importService.validate(dbDupFile, subjectId); // validate only - do NOT import (Cancel)
            check("T11: Cancel (no import) -> DB unchanged", countQuestions(subjectId) == before);

            // =========================================================
            // 2-6. INVALID ROWS
            // =========================================================
            File invalidFile = write("invalid.xlsx",
                    new String[]{"Question", "Answer", "Module", "Marks", "Difficulty"},
                    new String[][]{
                            {"", "Some answer", "Module 1", "2", "Easy"},          // 2: empty question
                            {"What is X?", "", "Module 1", "2", "Easy"},           // 3: empty answer
                            {"What is Y?", "A", "Module 99", "2", "Easy"},         // 4: nonexistent module
                            {"What is Z?", "A", "Module 1", "0", "Easy"},          // 5: marks = 0
                            {"What is W?", "A", "Module 1", "abc", "Easy"},        // 5: marks non-numeric
                            {"What is V?", "A", "Module 1", "-4", "Easy"},         // 5: negative marks
                            {"What is U?", "A", "Module 1", "2.5", "Easy"},        // 5: non-integer marks
                            {"What is T?", "A", "Module 1", "2", "Extreme"},       // 6: invalid difficulty
                            {"What is S?", "A", "Module 1", "2", ""}              // 6: empty difficulty
                    });
            r = importService.validate(invalidFile, subjectId);
            check("T2-6: 9 rows, 0 valid, 9 invalid",
                    r.getTotalRows() == 9 && r.getValidCount() == 0 && r.getInvalidCount() == 9);
            check("T2: empty question error", hasError(r, "Question is empty"));
            check("T3: empty answer error", hasError(r, "Answer is empty"));
            check("T4: nonexistent module error", hasError(r, "does not exist for the selected subject"));
            check("T5: invalid marks errors (0/abc/-4/2.5)", countErrors(r, "Invalid marks") == 4);
            check("T6: invalid difficulty errors (1 invalid value + 1 empty)",
                    countErrors(r, "ifficulty") == 2);
            importService.importValidRows(r); // allValid false; simulate attempted import
            check("T2-6: nothing inserted (0 valid)", countQuestions(subjectId) == 3);

            // =========================================================
            // 8. DUPLICATE ROWS WITHIN SAME FILE
            // =========================================================
            File batchDupFile = write("batchdup.xlsx",
                    new String[]{"Question", "Answer", "Module", "Marks", "Difficulty"},
                    new String[][]{
                            {"Same question here.", "A", "Module 1", "2", "Easy"},
                            {"same   question   here.", "B", "Module 1", "4", "Hard"},
                            {"Different question.", "C", "Module 1", "2", "Easy"}
                    });
            r = importService.validate(batchDupFile, subjectId);
            check("T8: in-file duplicate detected (normalized), 2 valid / 1 invalid",
                    r.getValidCount() == 2 && r.getInvalidCount() == 1
                            && r.getErrors().get(0).getMessage().contains("in this file"));

            // =========================================================
            // 9. MIXED VALID/INVALID
            // =========================================================
            File mixedFile = write("mixed.xlsx",
                    new String[]{"Question", "Answer", "Module", "Marks", "Difficulty"},
                    new String[][]{
                            {"Mixed valid one.", "A", "Module 1", "2", "Easy"},
                            {"", "B", "Module 1", "4", "Easy"},                 // invalid
                            {"Mixed valid two.", "C", "Module 2", "6", "Medium"},
                            {"D", "E", "Module 404", "2", "Hard"},              // invalid
                            {"Mixed valid three.", "F", "Module 1", "8", "Hard"}
                    });
            r = importService.validate(mixedFile, subjectId);
            check("T9: mixed file - 5 total / 3 valid / 2 invalid",
                    r.getTotalRows() == 5 && r.getValidCount() == 3 && r.getInvalidCount() == 2);
            check("T9: mixed file - not allValid", !r.allValid() && r.anyValid());
            check("T9: summary contains counts",
                    r.summaryText().contains("Total rows: 5")
                            && r.summaryText().contains("Valid: 3")
                            && r.summaryText().contains("Invalid: 2"));

            // =========================================================
            // 10. IMPORT VALID ROWS ONLY
            // =========================================================
            int beforeMixed = countQuestions(subjectId);
            imported = importService.importValidRows(r);
            check("T10: Import Valid Rows Only inserts exactly 3", imported == 3);
            check("T10: DB increased by exactly 3 (invalid never inserted)",
                    countQuestions(subjectId) == beforeMixed + 3);

            // =========================================================
            // HEADER VALIDATION
            // =========================================================
            File badHeaders = write("badheaders.xlsx",
                    new String[]{"Q", "A", "Module", "Marks", "Difficulty"},
                    new String[][]{{"Some Q", "Some A", "Module 1", "2", "Easy"}});
            r = importService.validate(badHeaders, subjectId);
            check("Headers: wrong header rejected", !r.isHeaderValid()
                    && r.getHeaderError() != null);
            check("Headers: wrong header inserts nothing", countQuestions(subjectId) == beforeMixed + 3);

            // =========================================================
            // 12. DOWNLOADABLE TEMPLATE
            // =========================================================
            File template = new File(dir, "template.xlsx");
            importService.saveTemplate(template);
            check("T12: template file created", template.exists() && template.length() > 0);
            List<String> headers = readHeaderRow(template);
            check("T12: template headers exact: " + headers,
                    headers.size() == 5
                            && headers.get(0).equals("Question")
                            && headers.get(1).equals("Answer")
                            && headers.get(2).equals("Module")
                            && headers.get(3).equals("Marks")
                            && headers.get(4).equals("Difficulty"));

            // A template can be re-imported as a valid (header-checked) file
            r = importService.validate(template, subjectId);
            check("T12: template passes header validation", r.isHeaderValid());
            check("T12: template has 0 data rows", r.getTotalRows() == 0);

            // =========================================================
            // 13. MATCH EXISTING QUESTIONS FROM A FILE
            // =========================================================
            File matchFile = write("match.xlsx",
                    new String[]{"Question", "Answer", "Module", "Marks", "Difficulty"},
                    new String[][]{
                            {"What is OOP?", "x", "Module 1", "2", "Easy"},
                            {"  explain   classes. ", "x", "Module 1", "4", "Medium"},
                            {"A question that is not in the bank?", "x", "Module 1", "2", "Easy"},
                            {"", "", "", "", ""}
                    });
            ExcelImportService.MatchReport mr = importService.matchExistingByFile(
                    matchFile, subjectId);
            check("T13: header ok and the blank row is not counted",
                    mr.isHeaderValid() && mr.getTotalRows() == 3);
            check("T13: normalized text match finds exactly 2 subject-1 questions",
                    mr.getMatched().size() == 2);
            mr = importService.matchExistingByFile(badHeaders, subjectId);
            check("T13: wrong headers reported, nothing matched",
                    !mr.isHeaderValid() && mr.getHeaderError() != null
                            && mr.getMatched().isEmpty());

            // =========================================================
            // 14. DELETE QUESTIONS IMPORTED FROM A FILE (subject-scoped)
            // =========================================================
            mr = importService.matchExistingByFile(matchFile, subjectId);
            QuestionService.BulkDeleteResult fromFile =
                    questionService.deleteQuestions(mr.getMatched());
            check("T14: the 2 matched questions are deleted",
                    fromFile.getDeleted() == 2 && fromFile.getKept().isEmpty());
            check("T14: subject 1 now has 4 questions", countQuestions(subjectId) == 4);
            check("T14: the same text in the other subject is untouched",
                    countQuestions(subject2Id) == 1);

            // =========================================================
            // 15. CLEAR ALL QUESTIONS OF A SUBJECT
            // =========================================================
            QuestionService.BulkDeleteResult cleared =
                    questionService.deleteAllForSubject(subjectId);
            check("T15: clearing removes the remaining 4 questions",
                    cleared.getDeleted() == 4 && countQuestions(subjectId) == 0);
            check("T15: the other subject keeps its question",
                    countQuestions(subject2Id) == 1);

        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            cleanup(subjectId, module1Id, module2Id, subject2Id, subject2ModuleId);
            deleteTempDir();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // --- helpers ---

    private static File write(String name, String[] headers, String[][] rows) throws IOException {
        File f = new File(dir, name);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Questions");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                wb.write(out);
            }
        }
        return f;
    }

    private static List<String> readHeaderRow(File f) throws IOException {
        List<String> headers = new java.util.ArrayList<>();
        try (java.io.FileInputStream in = new java.io.FileInputStream(f);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Row h = wb.getSheetAt(0).getRow(0);
            for (int i = 0; i < 5; i++) {
                headers.add(h.getCell(i) == null ? "" : h.getCell(i).getStringCellValue());
            }
        }
        return headers;
    }

    private static int countQuestions(int subjectId) throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null).size();
    }

    private static boolean hasError(ExcelImportService.ImportReport r, String text) {
        for (ExcelImportService.RowError e : r.getErrors()) {
            if (e.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static int countErrors(ExcelImportService.ImportReport r, String text) {
        int n = 0;
        for (ExcelImportService.RowError e : r.getErrors()) {
            if (e.getMessage().contains(text)) {
                n++;
            }
        }
        return n;
    }

    private static void cleanup(int subjectId, int m1, int m2,
                                int subject2Id, int subject2ModuleId) {
        // Remove ONLY test-created data (never the user's subjects/modules)
        try {
            for (int sid : new int[]{subjectId, subject2Id}) {
                if (sid <= 0) {
                    continue;
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
            }
            for (int mid : new int[]{m1, m2, subject2ModuleId}) {
                if (mid > 0) {
                    try {
                        moduleService.deleteModule(mid);
                    } catch (Exception ignored) {
                    }
                }
            }
            for (int sid : new int[]{subjectId, subject2Id}) {
                if (sid > 0) {
                    try {
                        subjectService.deleteSubject(sid);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteTempDir() {
        if (dir == null) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
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
