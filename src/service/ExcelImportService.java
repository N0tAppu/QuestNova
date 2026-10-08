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
                // Module exists for selected subject
                Integer moduleId = moduleMap.get(moduleName.toLowerCase());
                if (moduleId == null) {
                    cellErrors.add(moduleName.isEmpty()
                            ? "Module is empty"
                            : "Module \"" + moduleName + "\" does not exist for the selected subject");
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
                    Question q = new Question();
                    q.setSubjectId(subjectId);
                    q.setModuleId(moduleId);
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
     * Reuses QuestionService.addQuestion for final validation + persistence.
     *
     * @return number of questions actually imported
     */
    public int importValidRows(ImportReport report) {        int imported = 0;
        for (Question q : report.getValidQuestions()) {
            try {
                questionService.addQuestion(q);
                imported++;
            } catch (Exception ex) {
                // A valid-validated row failed at insert time: skip it, never insert invalid data.
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
