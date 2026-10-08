import dao.PaperDAO;
import database.DatabaseConnection;
import model.BlueprintSlot;
import model.ExamBlueprint;
import model.GeneratedPaper;
import model.GenerationResult;
import model.Module;
import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.ReviewQuestion;
import model.Subject;
import service.BlueprintService;
import service.ModuleService;
import service.PDFExportService;
import service.PaperGenerator;
import service.PaperReviewService;
import service.QuestionService;
import service.SubjectService;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Stage 10 test: PDF export of a finalized paper (question paper PDF and
 * separate answer key PDF).
 *
 * Fixture (own test data, cleaned up in the finally block):
 *   subject "TEST PDF EXPORT"
 *   module P1: 6/Medium x1 (very long question text with markers),
 *              4/Medium x1 (text contains the non-WinAnsi character,
 *                           very long answer with markers), 2/Easy x3
 *   module P2: 2/Easy x30
 *   blueprint total 76 = P1 16 (6+4+2+2+2) + P2 60 (30 x 2), 35 slots
 *
 * Before finalization question 1 of the paper is edited "for this paper"
 * only, so the stored snapshot deliberately differs from the question bank.
 */
public class PdfExportTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final BlueprintService blueprintService = new BlueprintService();
    private static final PaperGenerator generator = new PaperGenerator();
    private static final PaperReviewService reviewService = new PaperReviewService();
    private static final PaperDAO paperDAO = new PaperDAO();
    private static final PDFExportService exportService = new PDFExportService();

    private static final String subjectName = "TEST PDF EXPORT";
    private static final String examType = ExamBlueprint.TYPE_SEMESTER;
    private static final char RUPEE = '\u20B9';
    private static final String LONG_Q_START = "LONG-QUESTION-START-MARKER";
    private static final String LONG_Q_END = "LONG-QUESTION-END-MARKER";
    private static final String LONG_A_START = "ANSWER-START-MARKER";
    private static final String LONG_A_END = "ANSWER-END-MARKER";
    private static final String SNAPSHOT_Q = "SNAPSHOT-EDITED-Q1";
    private static final String SNAPSHOT_A = "SNAPSHOT-EDITED-A1";
    private static final String BANK_Q = "BANK-EDITED-QUESTION-UNIQUE";
    private static final String BANK_A = "BANK-EDITED-ANSWER-UNIQUE";

    private static int subjectId = -1;
    private static int p1 = -1;
    private static int p2 = -1;
    private static int longSeq = -1;
    private static int longQId = -1;
    private static int questionCount = 35;
    private static PaperReview reviewA;
    private static int paperAId = -1;

    private static Path tempDir;
    private static File paperFile;
    private static File keyFile;

    public static void main(String[] args) {
        try {
            tempDir = Files.createTempDirectory("qpg-pdf-export");
            preClean();
            setup();
            scenarioA();
            scenarioB();
            scenarioC();
            scenarioD();
            scenarioE();
            scenarioF();
            scenarioG();
            scenarioH();
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
    // Fixture
    // =====================================================================

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        p1 = moduleService.addModule(subjectId, "TEST PDF M1");
        p2 = moduleService.addModule(subjectId, "TEST PDF M2");

        String longBankText = buildText(LONG_Q_START, LONG_Q_END, 7600);
        String longBankAnswer = "Answer " + longBankText;
        String longAnswer = buildText(LONG_A_START, LONG_A_END, 6700);

        addQ(p1, 6, "Medium", longBankText, longBankAnswer);
        addQ(p1, 4, "Medium",
                "PDF-RUPEE-Q1 What is the total cost in " + RUPEE + " for the given data?",
                longAnswer);
        for (int i = 1; i <= 3; i++) {
            addQ(p1, 2, "Easy", "PDF-EASY-P1-" + i + " What is the definition of topic " + i + "?",
                    "PDF-EASY-ANS-P1-" + i + " It is defined as topic " + i + ".");
        }
        for (int i = 1; i <= 30; i++) {
            addQ(p2, 2, "Easy", "PDF-EASY-P2-" + i + " What is the definition of topic " + i + "?",
                    "PDF-EASY-ANS-P2-" + i + " It is defined as topic " + i + ".");
        }

        int actual = allQuestions().size();
        if (actual != questionCount) {
            System.out.println("  SETUP: subject=" + subjectId + " p1=" + p1 + " p2=" + p2
                    + " questions=" + actual + " expected=" + questionCount);
        }
        check("Setup: subject + 2 modules + 35 questions",
                subjectId > 0 && p1 > 0 && p2 > 0 && actual == questionCount);

        ExamBlueprint bp = new ExamBlueprint();
        bp.setSubjectId(subjectId);
        bp.setSubjectName(subjectName);
        bp.setExamType(examType);
        bp.setExamDifficulty("Easy");
        bp.setTotalMarks(76);
        bp.selectModule(p1, "TEST PDF M1");
        bp.setAllocation(p1, 16);
        blueprintService.addPatternRow(bp, p1, 6, 1);
        blueprintService.addPatternRow(bp, p1, 4, 1);
        blueprintService.addPatternRow(bp, p1, 2, 3);
        bp.selectModule(p2, "TEST PDF M2");
        bp.setAllocation(p2, 60);
        blueprintService.addPatternRow(bp, p2, 2, 30);

        List<BlueprintSlot> slots = blueprintService.generateSlots(bp);
        check("Setup: blueprint yields the expected 35 slots", slots.size() == 35);

        GenerationResult result = generator.generatePaper(subjectId, subjectName,
                examType, "Easy", 76, slots);
        check("Setup: generation succeeds for the fixture blueprint",
                result.isSuccess() && result.getPaper() != null);
        if (!result.isSuccess()) {
            return;
        }
        GeneratedPaper generated = result.getPaper();
        reviewA = reviewService.openReview(generated, slots);
        check("Setup: review opened with one question per slot",
                reviewA != null && reviewA.getQuestions().size() == 35);

        for (ReviewQuestion rq : reviewA.getQuestions()) {
            if (rq.getQuestionText().contains(LONG_Q_START)) {
                longSeq = rq.getSequenceNo();
                longQId = rq.getQuestionId();
            }
        }
        check("Setup: the very long question was drawn into the paper",
                longSeq > 0 && longQId > 0);
        if (longSeq <= 0) {
            return;
        }

        String snapshotText = reviewA.getQuestionBySequence(longSeq).getQuestionText()
                + " " + SNAPSHOT_Q;
        reviewService.editQuestionText(reviewA, longSeq, snapshotText, SNAPSHOT_A);
        check("Setup: edited only for this paper before finalization",
                reviewA.getQuestionBySequence(longSeq).getQuestionText().contains(SNAPSHOT_Q)
                        && questionService.getQuestionById(longQId).getQuestionText()
                        .equals(reviewA.getQuestionBySequence(longSeq).getQuestionText()
                                .replace(" " + SNAPSHOT_Q, "")));
        check("Setup: the review validates clean",
                reviewService.validate(reviewA).isEmpty());

        paperAId = reviewService.finalizePaper(reviewA);
        check("Setup: paper finalized with an id", paperAId > 0 && reviewA.isFinalized());

        paperFile = new File(tempDir.toFile(), "paper1.pdf");
        keyFile = new File(tempDir.toFile(), "key1.pdf");
    }

    private static String buildText(String startMarker, String endMarker, int minLength) {
        StringBuilder sb = new StringBuilder();
        sb.append(startMarker).append(' ');
        String unit = "revision concept definition explanation example topic clarity overview "
                + "summary reference study notes derivation conclusion readability practice ";
        while (sb.length() < minLength) {
            sb.append(unit);
        }
        sb.append(endMarker);
        return sb.toString();
    }

    private static void addQ(int moduleId, int marks, String difficulty, String text,
                             String answer) throws Exception {
        Question q = new Question();
        q.setSubjectId(subjectId);
        q.setModuleId(moduleId);
        q.setQuestionText(text);
        q.setAnswerText(answer);
        q.setMarks(marks);
        q.setDifficulty(difficulty);
        questionService.addQuestion(q);
    }

    // =====================================================================
    // A. Both documents are produced, open cleanly and are real PDFs
    // =====================================================================

    private static void scenarioA() throws Exception {
        File returned = exportService.exportQuestionPaper(paperAId, paperFile);
        check("A: question paper export returns the target file",
                returned != null && returned.equals(paperFile) && paperFile.exists());
        check("A: question paper file is a non-empty PDF",
                paperFile.length() > 1000 && magic(paperFile).equals("%PDF"));
        check("A: question paper opens with PDFBox and has at least two pages",
                pageCount(paperFile) >= 2);

        File returnedKey = exportService.exportAnswerKey(paperAId, keyFile);
        check("A: answer key export returns the target file",
                returnedKey != null && returnedKey.equals(keyFile) && keyFile.exists());
        check("A: answer key file is a non-empty PDF",
                keyFile.length() > 1000 && magic(keyFile).equals("%PDF"));
        check("A: answer key opens with PDFBox and has at least two pages",
                pageCount(keyFile) >= 2);
    }

    // =====================================================================
    // B. Question paper content: header, every snapshot question, marks
    // =====================================================================

    private static void scenarioB() throws Exception {
        String raw = extract(paperFile);
        String norm = bodyText(raw);

        check("B: header carries QUESTION PAPER, subject and maximum marks",
                norm.contains("QUESTION PAPER")
                        && norm.contains(subjectName)
                        && norm.contains("Maximum Marks: 76"));

        int misses = 0;
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            if (!norm.contains(normalize(expectedInPdf(rq.getQuestionText())))) {
                misses++;
                if (misses <= 6) {
                    System.out.println("  B missing seq=" + rq.getSequenceNo()
                            + " [" + abbreviate(expectedInPdf(rq.getQuestionText())) + "]");
                    if (misses == 1) {
                        reportDivergence(normalize(expectedInPdf(rq.getQuestionText())), norm);
                    }
                }
            }
        }
        check("B: every snapshot question text appears in full", misses == 0);

        check("B: the paper numbers every question exactly once",
                countLines(raw, "\\d+\\.\\s.*") == 35);
        check("B: the marks column shows every marks value",
                norm.contains("6 Marks") && norm.contains("4 Marks")
                        && norm.contains("2 Marks"));
        check("B: marks column carries the right number of rows",
                countOccurrences(norm, "2 Marks") == 33
                        && countOccurrences(norm, "4 Marks") == 1
                        && countOccurrences(norm, "6 Marks") == 1);
        check("B: paper shows the edited-for-this-paper snapshot, not the bank text",
                norm.contains(SNAPSHOT_Q) && norm.contains(LONG_Q_START)
                        && norm.contains(LONG_Q_END) && !norm.contains(BANK_Q));
        check("B: non-WinAnsi character does not break the export",
                norm.contains("PDF-RUPEE-Q1 What is the total cost in ? for the given data?"));
    }

    // =====================================================================
    // C. Answer key content: snapshot answers with their marks
    // =====================================================================

    private static void scenarioC() throws Exception {
        String raw = extract(keyFile);
        String norm = bodyText(raw);

        check("C: answer key header names the document and the subject",
                norm.contains("ANSWER KEY") && norm.contains(subjectName));

        int misses = 0;
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            if (!norm.contains(normalize(expectedInPdf(rq.getAnswerText())))) {
                misses++;
                if (misses <= 6) {
                    System.out.println("  C missing seq=" + rq.getSequenceNo()
                            + " [" + abbreviate(expectedInPdf(rq.getAnswerText())) + "]");
                    if (misses == 1) {
                        reportDivergence(normalize(expectedInPdf(rq.getAnswerText())), norm);
                    }
                }
            }
        }
        check("C: every snapshot answer appears in full", misses == 0);

        check("C: the answer key numbers every answer exactly once",
                countLines(raw, "\\d+\\.\\s.*") == 35);
        check("C: the answer key states the marks of every answer",
                countLines(raw, "Marks: \\d+") == 35
                        && norm.contains("Marks: 6") && norm.contains("Marks: 4")
                        && norm.contains("Marks: 2"));
        check("C: answer key carries the snapshot answer, not the bank answer",
                norm.contains(SNAPSHOT_A) && norm.contains(LONG_A_START)
                        && norm.contains(LONG_A_END) && !norm.contains(BANK_A));
    }

    // =====================================================================
    // D. Pagination: no blank pages, footers, long block fully rendered
    // =====================================================================

    private static void scenarioD() throws Exception {
        int paperPages = pageCount(paperFile);
        check("D: the long question forces at least three pages", paperPages >= 3);
        check("D: every question page carries its Page X of Y footer",
                hasFooterOnEveryPage(paperFile, paperPages));

        String norm = bodyText(extract(paperFile));
        check("D: the whole long question is rendered, start marker and end marker",
                norm.contains(LONG_Q_START) && norm.contains(LONG_Q_END));
        check("D: no page of the question paper is blank", blankPages(paperFile) == 0);

        int keyPages = pageCount(keyFile);
        check("D: the long answer forces at least three pages", keyPages >= 3);
        check("D: every answer page carries its Page X of Y footer",
                hasFooterOnEveryPage(keyFile, keyPages));
        String keyNorm = bodyText(extract(keyFile));
        check("D: the whole long answer is rendered, start marker and end marker",
                keyNorm.contains(LONG_A_START) && keyNorm.contains(LONG_A_END));
        check("D: no page of the answer key is blank", blankPages(keyFile) == 0);
    }

    // =====================================================================
    // E. Export always reads the finalized snapshot, never the bank
    // =====================================================================

    private static void scenarioE() throws Exception {
        Question bank = questionService.getQuestionById(longQId);
        bank.setQuestionText(BANK_Q);
        bank.setAnswerText(BANK_A);
        questionService.updateQuestion(bank);

        File paper2 = new File(tempDir.toFile(), "paper2.pdf");
        File key2 = new File(tempDir.toFile(), "key2.pdf");
        exportService.exportQuestionPaper(paperAId, paper2);
        exportService.exportAnswerKey(paperAId, key2);

        String norm2 = bodyText(extract(paper2));
        check("E: paper export ignores the later bank change",
                norm2.contains(SNAPSHOT_Q) && !norm2.contains(BANK_Q));
        String keyNorm2 = bodyText(extract(key2));
        check("E: answer key export ignores the later bank change",
                keyNorm2.contains(SNAPSHOT_A) && !keyNorm2.contains(BANK_A));

        PaperReview loaded = reviewService.loadPaper(paperAId);
        boolean unchanged = loaded != null && loaded.getQuestions().size() == 35
                && loaded.getQuestionBySequence(longSeq) != null
                && loaded.getQuestionBySequence(longSeq).getQuestionText().contains(SNAPSHOT_Q)
                && loaded.getQuestionBySequence(longSeq).getAnswerText().equals(SNAPSHOT_A);
        check("E: exporting never changes the stored paper", unchanged);
        check("E: exporting never writes to the database",
                paperQuestionCount() == 35
                        && count("SELECT COUNT(*) FROM question_papers WHERE subject_id = ?",
                        subjectId) == 1);
    }

    // =====================================================================
    // F. Default file names are safe and suggested per document type
    // =====================================================================

    private static void scenarioF() {
        String plain = PDFExportService.buildDefaultFileName(paperAId, "", false);
        String key = PDFExportService.buildDefaultFileName(paperAId, null, true);
        String withSubject = PDFExportService.buildDefaultFileName(
                paperAId, subjectName, false);
        String unsafe = PDFExportService.buildDefaultFileName(
                paperAId, "CSE/3:4*?<>|\\\"", false);

        check("F: default question paper name follows Question_Paper_<id>.pdf",
                plain.equals("Question_Paper_" + paperAId + ".pdf"));
        check("F: default answer key name follows Answer_Key_<id>.pdf",
                key.equals("Answer_Key_" + paperAId + ".pdf"));
        check("F: subject is appended in file-system safe form",
                withSubject.matches(
                        "Question_Paper_" + paperAId + "_TEST_PDF_EXPORT\\.pdf"));
        check("F: unsafe subject characters are replaced",
                unsafe.matches("Question_Paper_" + paperAId
                        + "_[A-Za-z0-9][A-Za-z0-9_.-]*\\.pdf"));
        boolean noUnsafeChars = true;
        for (char c : unsafe.toCharArray()) {
            if (c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0) {
                noUnsafeChars = false;
            }
        }
        check("F: the suggested name contains no unsafe characters", noUnsafeChars);
    }

    // =====================================================================
    // G. Error handling: bad ids, missing parents, unusable targets
    // =====================================================================

    private static void scenarioG() throws Exception {
        File never1 = new File(tempDir.toFile(), "never1.pdf");
        check("G: negative paper id is refused", refused(-1, never1, false));
        check("G: a refused export creates no file", !never1.exists());

        File never2 = new File(tempDir.toFile(), "never2.pdf");
        check("G: paper id zero is refused", refused(0, never2, false));
        File never3 = new File(tempDir.toFile(), "never3.pdf");
        check("G: an unknown paper id is refused", refused(999999, never3, true));
        check("G: unknown answer key export is refused", refused(-1, never3, true)
                && !never3.exists());

        File never4 = new File(tempDir.toFile(), "never4.pdf");
        boolean nullTarget = false;
        try {
            exportService.exportQuestionPaper(paperAId, null);
        } catch (IllegalArgumentException ex) {
            nullTarget = true;
        }
        check("G: a missing target file is refused", nullTarget);
        check("G: no file was created for the missing target", !never4.exists());

        File missingParent = new File(
                new File(tempDir.toFile(), "no-such-dir").getAbsolutePath(), "child.pdf");
        boolean parentRefused = false;
        try {
            exportService.exportQuestionPaper(paperAId, missingParent);
        } catch (java.io.IOException ex) {
            parentRefused = true;
        }
        check("G: a target in a missing directory fails with an IO error", parentRefused);
        check("G: nothing is created in the missing directory", !missingParent.exists());

        File directory = new File(tempDir.toFile(), "a-directory");
        check("G: directory target fixture exists", directory.mkdir());
        boolean dirRefused = false;
        try {
            exportService.exportQuestionPaper(paperAId, directory);
        } catch (java.io.IOException ex) {
            dirRefused = true;
        }
        check("G: a directory as target fails with an IO error", dirRefused);
        check("G: the directory is not deleted or replaced",
                directory.isDirectory());
    }

    // =====================================================================
    // H. Re-export over an existing file replaces it completely
    // =====================================================================

    private static void scenarioH() throws Exception {
        File target = new File(tempDir.toFile(), "overwrite.pdf");
        Files.write(target.toPath(), new byte[0]);
        check("H: pre-existing empty file fixture", target.exists() && target.length() == 0);

        exportService.exportQuestionPaper(paperAId, target);
        check("H: export over an existing file produces a valid PDF",
                magic(target).equals("%PDF") && pageCount(target) >= 2);
        int pages = pageCount(target);

        exportService.exportQuestionPaper(paperAId, target);
        check("H: a second export over the same file succeeds",
                magic(target).equals("%PDF") && pageCount(target) == pages);
        check("H: the repeated export still holds the snapshot text",
                bodyText(extract(target)).contains(SNAPSHOT_Q));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static boolean refused(int id, File target, boolean answerKey) {
        try {
            if (answerKey) {
                exportService.exportAnswerKey(id, target);
            } else {
                exportService.exportQuestionPaper(id, target);
            }
            return false;
        } catch (IllegalArgumentException ex) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean hasFooterOnEveryPage(File file, int pages) throws Exception {
        for (int page = 1; page <= pages; page++) {
            String text = extractPage(file, page);
            if (!normalize(text).contains("Page " + page + " of " + pages)) {
                return false;
            }
        }
        return true;
    }

    private static int blankPages(File file) throws Exception {
        int pages = pageCount(file);
        int blank = 0;
        for (int page = 1; page <= pages; page++) {
            if (normalize(extractPage(file, page)).length() <= 20) {
                blank++;
            }
        }
        return blank;
    }

    private static String extract(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private static String extractPage(File file, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        }
    }

    private static int pageCount(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            return document.getNumberOfPages();
        }
    }

    private static String magic(File file) throws Exception {
        byte[] head = new byte[4];
        try (InputStream in = new FileInputStream(file)) {
            in.read(head);
        }
        return new String(head, StandardCharsets.US_ASCII);
    }

    /** The text as it has to appear inside the PDF (WinAnsi cannot encode the rupee). */
    private static String expectedInPdf(String text) {
        return String.valueOf(text).replace(RUPEE, '?');
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /**
     * The extracted text without the page footers. A question that runs over
     * a page boundary is interrupted by the footer of the previous page in
     * the extraction order, so content comparisons use this view while the
     * footers themselves are verified separately, page by page.
     */
    private static String bodyText(String raw) {
        StringBuilder body = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            if (line.trim().matches("Page \\d+ of \\d+")) {
                continue;
            }
            body.append(line).append('\n');
        }
        return normalize(body.toString());
    }

    private static String abbreviate(String text) {
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }

    /** Shows where the extracted text stops following the expected text. */    private static void reportDivergence(String expected, String actual) {
        String head = expected.substring(0, Math.min(40, expected.length()));
        int anchor = actual.indexOf(head);
        if (anchor < 0) {
            int k = 0;
            while (k < expected.length()
                    && actual.contains(expected.substring(0, k + 1))) {
                k++;
            }
            System.out.println("  diverge: longest contained prefix=" + k);
            System.out.println("  expected next: [" + expected.substring(k,
                    Math.min(expected.length(), k + 90)) + "]");
            return;
        }
        int k = 0;
        while (anchor + k < actual.length() && k < expected.length()
                && actual.charAt(anchor + k) == expected.charAt(k)) {
            k++;
        }
        System.out.println("  diverge at offset " + k);
        System.out.println("  expected next: [" + expected.substring(k,
                Math.min(expected.length(), k + 90)) + "]");
        System.out.println("  actual   next: [" + actual.substring(anchor + k,
                Math.min(actual.length(), anchor + k + 90)) + "]");
    }

    private static int countLines(String raw, String regex) {
        int count = 0;
        for (String line : raw.split("\\r?\\n")) {
            if (line.trim().matches(regex)) {
                count++;
            }
        }
        return count;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static List<Question> allQuestions() throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null);
    }

    private static int count(String sql, Object... params) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    private static int paperQuestionCount() throws Exception {
        return count("SELECT COUNT(*) FROM paper_questions pq "
                        + "JOIN question_papers p ON pq.paper_id = p.paper_id "
                        + "WHERE p.subject_id = ?", subjectId);
    }

    /** Removes leftovers of a previous crashed run of this test. */
    private static void preClean() {
        try {
            for (Subject s : subjectService.getAllSubjects()) {
                if (!subjectName.equals(s.getSubjectName())) {
                    continue;
                }
                int sid = s.getSubjectId();
                try {
                    for (QuestionPaper p : paperDAO.getPapers()) {
                        if (p.getSubjectId() == sid) {
                            try {
                                paperDAO.deletePaper(p.getPaperId());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
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
        } catch (Exception ignored) {
        }
    }

    private static void cleanup() {
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
        if (subjectId > 0) {
            try {
                for (Question q : allQuestions()) {
                    try {
                        questionService.deleteQuestion(q.getQuestionId());
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            for (int mid : new int[]{p1, p2}) {
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
        if (tempDir != null) {
            deleteRecursively(tempDir.toFile());
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            System.out.println("WARNING: could not delete " + file);
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
}
