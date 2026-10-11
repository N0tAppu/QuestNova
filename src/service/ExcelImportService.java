package service;

import dao.ModuleDAO;
import dao.QuestionDAO;
import model.Module;
import model.Question;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel (.xlsx) question-bank import using Apache POI.
 *
 * Workflow: read entire file -> validate all rows -> show summary ->
 * (teacher confirms) -> insert only valid rows.
 * Validation reuses QuestionService / QuestionDAO rules where appropriate.
 */
public class ExcelImportService {

    public static final String[] REQUIRED_HEADERS =
            {"Question", "Answer", "Module", "Marks", "Difficulty"};

    private final QuestionService questionService = new QuestionService();
    private final ModuleDAO moduleDAO = new ModuleDAO();
    private final QuestionDAO questionDAO = new QuestionDAO();
    private final ModuleService moduleService = new ModuleService();

    /** Validation error for one Excel row. */
    public static class RowError {
        private final int excelRow;   // 1-based Excel row number (header = 1)
        private final String message;

        public RowError(int excelRow, String message) {
            this.excelRow = excelRow;
            this.message = message;
        }

        public int getExcelRow() {
            return excelRow;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "Row " + excelRow + ": " + message;
        }
    }

    /** Result of validating a whole file (nothing has been inserted yet). */
    public static class ImportReport {
        private final File file;
        private final int subjectId;
        private String headerError;
        private final List<Question> validQuestions = new ArrayList<>();
        private final List<RowError> errors = new ArrayList<>();
        private final List<String> insertFailures = new ArrayList<>();
        private int totalRows;

        // Module discovery (auto-creation preview)
        private final List<String> distinctModules = new ArrayList<>();
        private final List<String> existingModules = new ArrayList<>();
        private final List<String> newModules = new ArrayList<>();
        private final Map<String, Integer> moduleQuestionCounts = new LinkedHashMap<>();
        private final List<String> invalidModuleValues = new ArrayList<>();
        private final List<String> modulesCreated = new ArrayList<>();

        ImportReport(File file, int subjectId) {
            this.file = file;
            this.subjectId = subjectId;
        }

        public File getFile() {
            return file;
        }

        public int getSubjectId() {
            return subjectId;
        }

        public String getHeaderError() {
            return headerError;
        }

        public boolean isHeaderValid() {
            return headerError == null;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getValidCount() {
            return validQuestions.size();
        }

        public int getInvalidCount() {
            return errors.size();
        }

        public List<Question> getValidQuestions() {
            return validQuestions;
        }

        public List<RowError> getErrors() {
            return errors;
        }

        public List<String> getInsertFailures() {
            return insertFailures;
        }

        public void addInsertFailure(String reason) {
            insertFailures.add(reason);
        }

        /** Distinct valid module names found in the file, in first-seen order. */
        public List<String> getDistinctModules() {
            return distinctModules;
        }

        /** Subset of distinctModules that already exist for the subject. */
        public List<String> getExistingModules() {
            return existingModules;
        }

        /** Subset of distinctModules that will be created during import. */
        public List<String> getNewModules() {
            return newModules;
        }

        /** Valid-question count per module name (first-seen order). */
        public Map<String, Integer> getModuleQuestionCounts() {
            return moduleQuestionCounts;
        }

        /** Descriptions of blank or invalid module cells (validation-time). */
        public List<String> getInvalidModuleValues() {
            return invalidModuleValues;
        }

        /** Module names actually created during importValidRows. */
        public List<String> getModulesCreated() {
            return modulesCreated;
        }

        /** Modules reused (already existed) during importValidRows. */
        public int getModulesReusedCount() {
            return existingModules.size();
        }

        public boolean allValid() {
            return headerError == null && totalRows > 0 && errors.isEmpty();
        }

        public boolean anyValid() {
            return !validQuestions.isEmpty();
        }

        /** Human-readable summary for the confirmation dialog. */
        public String summaryText() {
            if (headerError != null) {
                return "The file headers are not correct.\n\n" + headerError
                        + "\n\nExpected header row:\n"
                        + String.join(" | ", REQUIRED_HEADERS);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Question Bank Import - Validation Summary\n\n");
            sb.append("File: ").append(file.getName()).append('\n');
            sb.append("Total rows: ").append(totalRows).append('\n');
            sb.append("Valid: ").append(getValidCount()).append('\n');
            sb.append("Invalid: ").append(getInvalidCount()).append('\n');
            sb.append('\n');
            sb.append("Modules:\n");
            sb.append("  Distinct modules found: ").append(distinctModules.size()).append('\n');
            sb.append("  Existing modules: ").append(existingModules.size()).append('\n');
            sb.append("  New modules to create: ").append(newModules.size()).append('\n');
            if (!distinctModules.isEmpty()) {
                sb.append('\n').append("Module details:\n");
                for (String mod : distinctModules) {
                    int count = moduleQuestionCounts.getOrDefault(mod, 0);
                    boolean isNew = newModules.contains(mod);
                    sb.append("  ").append(mod).append(": ").append(count)
                            .append(" question").append(count == 1 ? "" : "s")
                            .append(isNew ? " (new - will be created)" : " (existing)")
                            .append('\n');
                }
            }
            if (!invalidModuleValues.isEmpty()) {
                sb.append('\n').append("Invalid module values:\n");
                for (String inv : invalidModuleValues) {
                    sb.append("  ").append(inv).append('\n');
                }
            }
            if (!errors.isEmpty()) {
                sb.append("\nErrors:\n");
                for (RowError e : errors) {
                    sb.append(e).append('\n');
                }
            }
            return sb.toString();
        }
    }

    /**
     * Reads and validates the entire .xlsx file for the given subject.
     * Nothing is inserted into the database by this method.
     */
    public ImportReport validate(File file, int subjectId) throws IOException, SQLException {
        ImportReport report = new ImportReport(file, subjectId);

        try (java.io.FileInputStream in = new java.io.FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                report.headerError = "The Excel file has no sheets.";
                return report;
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                report.headerError = "The Excel file is empty.";
                return report;
            }
            String headerError = checkHeaders(headerRow);
            if (headerError != null) {
                report.headerError = headerError;
                return report;
            }

            // Module map for the selected subject (name -> id)
            Map<String, Integer> moduleMap = new HashMap<>();
            for (Module m : moduleDAO.getModulesBySubject(subjectId)) {
                moduleMap.put(m.getModuleName().trim().toLowerCase(), m.getModuleId());
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> batchSeen = new LinkedHashMap<>(); // normalized -> first excel row

            int firstDataRow = headerRow.getRowNum() + 1;
            int lastRow = sheet.getLastRowNum();
            for (int r = firstDataRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                int excelRow = r + 1; // Excel is 1-based
                if (row == null) {
                    continue; // completely blank physical row: skip
                }

                List<String> cellErrors = new ArrayList<>();
                String questionText = cell(row, 0, formatter).trim();
                String answerText = cell(row, 1, formatter).trim();
                String moduleName = cell(row, 2, formatter).trim();
                String marksText = cell(row, 3, formatter).trim();
                String difficultyText = cell(row, 4, formatter).trim();

                boolean rowBlank = questionText.isEmpty() && answerText.isEmpty()
                        && moduleName.isEmpty() && marksText.isEmpty()
                        && difficultyText.isEmpty();
                if (rowBlank) {
                    continue;
                }

                // Question
                if (questionText.isEmpty()) {
                    cellErrors.add("Question is empty");
                }
                // Answer
                if (answerText.isEmpty()) {
                    cellErrors.add("Answer is empty");
                }
                // Module: blank or too-long are errors; unknown names are
                // discovered as new modules to create during import.
                Integer moduleId = null;
                boolean moduleKnown = false;
                if (moduleName.isEmpty()) {
                    cellErrors.add("Module is empty");
                    report.invalidModuleValues.add(
                            "Row " + excelRow + ": Module is empty");
                } else if (moduleName.length() > 100) {
                    cellErrors.add("Module name must be 100 characters or fewer");
                    report.invalidModuleValues.add(
                            "Row " + excelRow + ": Module name exceeds 100 characters");
                } else {
                    String key = moduleName.toLowerCase();
                    moduleId = moduleMap.get(key);
                    moduleKnown = moduleId != null;
                    // Track distinct module names (first-seen order)
                    if (!report.distinctModules.contains(moduleName)) {
                        report.distinctModules.add(moduleName);
                        if (moduleKnown) {
                            report.existingModules.add(moduleName);
                        } else {
                            report.newModules.add(moduleName);
                        }
                    }
                    // Count valid questions per module (after row passes all checks)
                }
                // Marks: integer > 0
                Integer marks = parsePositiveInt(marksText);
                if (marks == null) {
                    cellErrors.add(marksText.isEmpty()
                            ? "Marks is empty"
                            : "Invalid marks \"" + marksText + "\" (must be a whole number greater than 0)");
                }
                // Difficulty: Easy / Medium / Hard
                String difficulty = normalizeDifficulty(difficultyText);
                if (difficulty == null) {
                    cellErrors.add(difficultyText.isEmpty()
                            ? "Difficulty is empty"
                            : "Invalid difficulty \"" + difficultyText
                            + "\" (must be Easy, Medium or Hard)");
                }
                // Duplicate within this import batch
                if (cellErrors.isEmpty()) {
                    String normalized = QuestionDAO.normalize(questionText);
                    Integer firstSeen = batchSeen.putIfAbsent(normalized, excelRow);
                    if (firstSeen != null) {
                        cellErrors.add("Duplicate question (same as row " + firstSeen
                                + " in this file)");
                    }
                }
                // Duplicate against existing database questions for this subject
                if (cellErrors.isEmpty()) {
                    try {
                        if (questionDAO.existsSameText(subjectId, questionText)) {
                            cellErrors.add("Duplicate question (already exists in the question bank)");
                        }
                    } catch (SQLException ex) {
                        cellErrors.add("Database error while checking duplicates");
                    }
                }

                report.totalRows++;
                if (cellErrors.isEmpty()) {
                    // Count valid questions per module name
                    if (!moduleName.isEmpty()) {
                        report.moduleQuestionCounts.merge(moduleName, 1, Integer::sum);
                    }
                    Question q = new Question();
                    q.setSubjectId(subjectId);
                    if (moduleKnown) {
                        q.setModuleId(moduleId);
                    } else {
                        // New module: id resolved during importValidRows
                        q.setModuleId(0);
                        q.setModuleName(moduleName);
                    }
                    q.setQuestionText(questionText);
                    q.setAnswerText(answerText);
                    q.setMarks(marks);
                    q.setDifficulty(difficulty);
                    report.validQuestions.add(q);
                } else {
                    report.errors.add(new RowError(excelRow, String.join("; ", cellErrors)));
                }
            }
        }
        return report;
    }

    /**
     * Result of matching a file's question texts against existing questions
     * of one subject (used by "Delete Questions from This File").
     */
    public static class MatchReport {
        private String headerError;
        private int totalRows;
        private final List<Integer> matched = new ArrayList<>();

        public boolean isHeaderValid() {
            return headerError == null;
        }

        public String getHeaderError() {
            return headerError;
        }

        /** Non-blank data rows found in the file. */
        public int getTotalRows() {
            return totalRows;
        }

        /** Ids of existing questions of the subject whose text appears in the file. */
        public List<Integer> getMatched() {
            return matched;
        }
    }

    /**
     * Reads the question-text column (column 0) of the .xlsx file and returns
     * the ids of existing questions of the given subject whose normalized
     * text appears in the file. Blank rows are skipped and duplicate texts
     * count once. Nothing is deleted here.
     */
    public MatchReport matchExistingByFile(File file, int subjectId)
            throws IOException, SQLException {
        MatchReport report = new MatchReport();
        try (java.io.FileInputStream in = new java.io.FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                report.headerError = "The Excel file has no sheets.";
                return report;
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                report.headerError = "The Excel file is empty.";
                return report;
            }
            String headerError = checkHeaders(headerRow);
            if (headerError != null) {
                report.headerError = headerError;
                return report;
            }

            DataFormatter formatter = new DataFormatter();
            java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
            int firstDataRow = headerRow.getRowNum() + 1;
            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String questionText = cell(row, 0, formatter).trim();
                String answerText = cell(row, 1, formatter).trim();
                String moduleName = cell(row, 2, formatter).trim();
                String marksText = cell(row, 3, formatter).trim();
                String difficultyText = cell(row, 4, formatter).trim();
                boolean rowBlank = questionText.isEmpty() && answerText.isEmpty()
                        && moduleName.isEmpty() && marksText.isEmpty()
                        && difficultyText.isEmpty();
                if (rowBlank) {
                    continue;
                }
                report.totalRows++;
                if (!questionText.isEmpty()) {
                    wanted.add(QuestionDAO.normalize(questionText));
                }
            }
            if (wanted.isEmpty()) {
                return report;
            }
            for (Question q : questionDAO.searchQuestions(subjectId, null, null, null, null)) {
                if (wanted.contains(QuestionDAO.normalize(q.getQuestionText()))) {
                    report.matched.add(q.getQuestionId());
                }
            }
        }
        return report;
    }

    /**
     * Inserts the valid rows of a validated report (never invalid rows).
     * Creates any new modules discovered during validation, then inserts
     * questions with the correct module associations. Newly created modules
     * that end up with zero imported questions are removed so no orphans
     * are left behind.
     *
     * @return number of questions actually imported
     */
    public int importValidRows(ImportReport report) {
        // 1. Create new modules that have at least one valid question
        Map<String, Integer> moduleIdByName = new HashMap<>();
        try {
            for (Module m : moduleDAO.getModulesBySubject(report.getSubjectId())) {
                moduleIdByName.put(m.getModuleName().trim().toLowerCase(), m.getModuleId());
            }
        } catch (SQLException ex) {
            report.addInsertFailure("Could not load existing modules: " + ex.getMessage());
            return 0;
        }
        List<Integer> createdModuleIds = new ArrayList<>();
        for (String modName : report.getNewModules()) {
            Integer existing = moduleIdByName.get(modName.toLowerCase());
            if (existing != null) {
                // Module appeared between validate and import (concurrent import)
                moduleIdByName.put(modName.toLowerCase(), existing);
                continue;
            }
            try {
                int newId = moduleService.addModule(report.getSubjectId(), modName);
                moduleIdByName.put(modName.toLowerCase(), newId);
                createdModuleIds.add(newId);
                report.modulesCreated.add(modName);
            } catch (IllegalArgumentException ex) {
                // Duplicate detected at insert time: look up the id
                try {
                    for (Module m : moduleDAO.getModulesBySubject(report.getSubjectId())) {
                        if (m.getModuleName().trim().equalsIgnoreCase(modName)) {
                            moduleIdByName.put(modName.toLowerCase(), m.getModuleId());
                            break;
                        }
                    }
                } catch (SQLException lookupEx) {
                    report.addInsertFailure(
                            "Module \"" + modName + "\" could not be resolved: "
                            + lookupEx.getMessage());
                }
            } catch (SQLException ex) {
                report.addInsertFailure(
                        "Module \"" + modName + "\" could not be created: "
                        + ex.getMessage());
            }
        }

        // 2. Resolve moduleId on questions that reference new modules
        for (Question q : report.getValidQuestions()) {
            if (q.getModuleId() == 0 && q.getModuleName() != null) {
                Integer resolved = moduleIdByName.get(q.getModuleName().trim().toLowerCase());
                if (resolved != null) {
                    q.setModuleId(resolved);
                }
            }
        }

        // 3. Insert questions (existing partial-import behaviour)
        int imported = 0;
        for (Question q : report.getValidQuestions()) {
            try {
                questionService.addQuestion(q);
                imported++;
            } catch (Exception ex) {
                String reason;
                if (ex instanceof SQLException) {
                    reason = "database error";
                } else {
                    reason = ex.getMessage() == null ? "unexpected error" : ex.getMessage();
                }
                report.addInsertFailure("Question \""
                        + (q.getQuestionText() == null || q.getQuestionText().length() > 60
                                ? (q.getQuestionText() == null ? ""
                                        : q.getQuestionText().substring(0, 60) + "...")
                                : q.getQuestionText())
                        + "\" could not be inserted: " + reason);
            }
        }

        // 4. Remove newly created modules that no imported question references
        for (int modId : createdModuleIds) {
            try {
                if (moduleDAO.countQuestionsInModule(modId) == 0) {
                    moduleDAO.deleteModule(modId);
                    // Remove from modulesCreated so the report stays accurate
                    for (int i = report.modulesCreated.size() - 1; i >= 0; i--) {
                        Integer id = moduleIdByName.get(
                                report.modulesCreated.get(i).trim().toLowerCase());
                        if (id != null && id == modId) {
                            report.modulesCreated.remove(i);
                            break;
                        }
                    }
                }
            } catch (SQLException ex) {
                // Best-effort cleanup; leave the module if delete fails
            }
        }
        return imported;
    }

    /**
     * Writes the approved Excel template with the exact required headers.
     */
    public void saveTemplate(File file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Question Bank");
            Row header = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int i = 0; i < REQUIRED_HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(REQUIRED_HEADERS[i]);
                c.setCellStyle(style);
            }
            for (int i = 0; i < REQUIRED_HEADERS.length; i++) {
                sheet.setColumnWidth(i, 25 * 256);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
        }
    }

    // --- helpers ---

    private String checkHeaders(Row headerRow) {
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < REQUIRED_HEADERS.length; i++) {
            String actual = cell(headerRow, i, formatter).trim();
            if (!actual.equalsIgnoreCase(REQUIRED_HEADERS[i])) {
                return "Column " + (i + 1) + " is \"" + (actual.isEmpty() ? "(empty)" : actual)
                        + "\" but expected \"" + REQUIRED_HEADERS[i] + "\".";
            }
        }
        String extra = cell(headerRow, REQUIRED_HEADERS.length, formatter).trim();
        if (!extra.isEmpty()) {
            return "Unexpected extra column \"" + extra + "\" after the required headers.";
        }
        return null;
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        Cell c = row.getCell(index);
        if (c == null) {
            return "";
        }
        return formatter.formatCellValue(c);
    }

    private Integer parsePositiveInt(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            double d = Double.parseDouble(text);
            if (d != Math.floor(d) || Double.isInfinite(d)) {
                return null; // not a whole number
            }
            int value = (int) d;
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeDifficulty(String text) {
        for (String d : QuestionService.getValidDifficulties()) {
            if (d.equalsIgnoreCase(text.trim())) {
                return d;
            }
        }
        return null;
    }
}
