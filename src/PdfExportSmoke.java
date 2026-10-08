import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.Subject;
import model.Module;
import service.ModuleService;
import service.PaperReviewService;
import service.QuestionService;
import service.SubjectService;
import ui.GeneratedPapersPanel;
import ui.GeneratePaperPanel;
import ui.ReviewPaperPanel;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Stage 10 GUI smoke test: drives the export buttons of the Generated Paper
 * Review screen through their public facades (the same code paths the
 * button handlers use) on the EDT - before and after finalization, and from
 * the Generated Papers history.
 *
 * Uses its own test subject/module/questions in the live DB and cleans
 * everything up (including the finalized paper and the exported files).
 */
public class PdfExportSmoke {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final PaperReviewService reviewService = new PaperReviewService();

    private static final String subjectName = "TEST PDF SMOKE";

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int paperId = -1;
    private static Path tempDir;

    public static void main(String[] args) {
        preClean();
        try {
            tempDir = Files.createTempDirectory("qpg-pdf-smoke");
            setup();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    driveExportScreen();
                } catch (Exception ex) {
                    failed++;
                    System.out.println("FAIL: unexpected exception on the export screen -> " + ex);
                    ex.printStackTrace();
                }
            });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    driveHistoryExport();
                } catch (Exception ex) {
                    failed++;
                    System.out.println("FAIL: unexpected exception on the history screen -> " + ex);
                    ex.printStackTrace();
                }
            });
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: unexpected exception -> " + ex);
            ex.printStackTrace();
        } finally {
            cleanup();
        }

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================
    // Fixture: 1 subject, 1 module, 3 questions
    //   blueprint total 6 = 3 slots (2/Easy x3)
    // =====================================================================

    private static void setup() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        m1 = moduleService.addModule(subjectId, "SMOKE PDF M1");
        for (int i = 1; i <= 3; i++) {
            Question q = new Question();
            q.setSubjectId(subjectId);
            q.setModuleId(m1);
            q.setQuestionText("PDF-SMOKE-Q" + i + " What is the definition of topic " + i + "?");
            q.setAnswerText("PDF-SMOKE-A" + i + " It is defined as topic " + i + ".");
            q.setMarks(2);
            q.setDifficulty("Easy");
            questionService.addQuestion(q);
        }
        check("Setup: subject + module + 3 questions",
                subjectId > 0 && m1 > 0 && allQuestions().size() == 3);
    }

    // =====================================================================
    // Export screen: disabled before finalization, enabled and working after
    // =====================================================================

    private static void driveExportScreen() throws Exception {
        GeneratePaperPanel blueprint = new GeneratePaperPanel();
        blueprint.refreshPanel();
        blueprint.selectSubject(subjectId);
        blueprint.setExamType(model.ExamBlueprint.TYPE_SEMESTER);
        blueprint.setExamDifficulty("Easy");
        blueprint.setTotalMarks(6);
        blueprint.addModulesToSyllabus(m1);
        blueprint.setModuleAllocation(m1, 6);
        blueprint.addPattern(m1, 2, 3);
        check("Blueprint built for the smoke subject",
                blueprint.getValidationErrors().isEmpty());

        model.GenerationResult result = blueprint.generatePaper();
        check("Generation succeeds", result.isSuccess() && result.getPaper() != null);
        if (!result.isSuccess()) {
            return;
        }

        PaperReview review = reviewService.openReview(result.getPaper(), blueprint.getSlots());
        ReviewPaperPanel panel = new ReviewPaperPanel();
        panel.setReview(review);
        check("Export screen binds the paper (3 rows)",
                panel.getRowCount() == 3 && !panel.isFinalized());
        check("Both export buttons are disabled before finalization",
                !panel.isExportQuestionPaperEnabled() && !panel.isExportAnswerKeyEnabled());

        File refused = new File(tempDir.toFile(), "too-early.pdf");
        boolean exportRefused = false;
        try {
            panel.exportQuestionPaper(refused);
        } catch (IllegalStateException ex) {
            exportRefused = true;
        }
        check("Export before finalization is refused", exportRefused);
        check("The refused export created no file", !refused.exists());

        paperId = panel.finalizeCurrentPaper();
        check("Finalize stored the paper", paperId > 0 && panel.getPaperId() == paperId);
        check("Both export buttons are enabled after finalization",
                panel.isExportQuestionPaperEnabled() && panel.isExportAnswerKeyEnabled());
        check("Suggested question paper name carries paper id and subject",
                panel.getSuggestedFileName(false).equals(
                        "Question_Paper_" + paperId + "_TEST_PDF_SMOKE.pdf"));
        check("Suggested answer key name carries paper id and subject",
                panel.getSuggestedFileName(true).equals(
                        "Answer_Key_" + paperId + "_TEST_PDF_SMOKE.pdf"));

        File paperFile = new File(tempDir.toFile(), "smoke-paper.pdf");
        File written = panel.exportQuestionPaper(paperFile);
        check("Facade export writes the suggested question paper file",
                written != null && paperFile.exists() && paperFile.length() > 1000
                        && magic(paperFile).equals("%PDF"));
        String paperText = bodyText(extract(paperFile));
        check("Exported question paper holds header, questions and marks",
                paperText.contains("QUESTION PAPER") && paperText.contains(subjectName)
                        && paperText.contains("Maximum Marks: 6")
                        && paperText.contains("PDF-SMOKE-Q1")
                        && paperText.contains("PDF-SMOKE-Q3")
                        && paperText.contains("2 Marks"));
        check("Exported question paper has at least one page", pageCount(paperFile) >= 1);

        File keyFile = new File(tempDir.toFile(), "smoke-key.pdf");
        File writtenKey = panel.exportAnswerKey(keyFile);
        check("Facade export writes the suggested answer key file",
                writtenKey != null && keyFile.exists() && keyFile.length() > 1000
                        && magic(keyFile).equals("%PDF"));
        String keyText = bodyText(extract(keyFile));
        check("Exported answer key holds answers and their marks",
                keyText.contains("ANSWER KEY") && keyText.contains("PDF-SMOKE-A1")
                        && keyText.contains("PDF-SMOKE-A3")
                        && keyText.contains("Marks: 2"));

        File again = new File(tempDir.toFile(), "smoke-paper.pdf");
        panel.exportQuestionPaper(again);
        check("Re-export over the same file succeeds",
                magic(again).equals("%PDF") && pageCount(again) >= 1);
    }

    // =====================================================================
    // History: open a finalized paper and export from there
    // =====================================================================

    private static void driveHistoryExport() throws Exception {
        GeneratedPapersPanel history = new GeneratedPapersPanel();
        check("History screen lists at least our paper", history.getRowCount() >= 1);

        int row = -1;
        for (int i = 1; i <= history.getRowCount(); i++) {
            history.selectRow(i - 1);
            if (history.getSelectedPaperId() == paperId) {
                row = i - 1;
            }
        }
        check("Our finalized paper appears in the history", row >= 0);
        history.selectRow(row);

        PaperReview loaded = history.loadSelected();
        ReviewPaperPanel panel = new ReviewPaperPanel();
        panel.setReview(loaded);
        check("Reopened paper shows both export buttons enabled",
                panel.isExportQuestionPaperEnabled() && panel.isExportAnswerKeyEnabled());

        File fromHistory = new File(tempDir.toFile(), "smoke-history.pdf");
        panel.exportQuestionPaper(fromHistory);
        check("Export from the reopened paper writes a valid PDF",
                fromHistory.exists() && magic(fromHistory).equals("%PDF")
                        && bodyText(extract(fromHistory)).contains("PDF-SMOKE-Q1"));
        check("Export from history uses the stored snapshot text",
                bodyText(extract(fromHistory)).contains(subjectName));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static String extract(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(document);
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

    private static String bodyText(String raw) {
        StringBuilder body = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            if (line.trim().matches("Page \\d+ of \\d+")) {
                continue;
            }
            body.append(line).append('\n');
        }
        return body.toString().replaceAll("\\s+", " ").trim();
    }

    private static List<Question> allQuestions() throws Exception {
        return questionService.searchQuestions(subjectId, null, null, null, null);
    }

    private static void preClean() {
        try {
            for (Subject s : subjectService.getAllSubjects()) {
                if (!subjectName.equals(s.getSubjectName())) {
                    continue;
                }
                int sid = s.getSubjectId();
                try {
                    for (QuestionPaper p : reviewService.getPapers()) {
                        if (p.getSubjectId() == sid) {
                            try {
                                new dao.PaperDAO().deletePaper(p.getPaperId());
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
            for (QuestionPaper p : reviewService.getPapers()) {
                if (p.getSubjectId() == subjectId) {
                    try {
                        new dao.PaperDAO().deletePaper(p.getPaperId());
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
            if (m1 > 0) {
                try {
                    moduleService.deleteModule(m1);
                } catch (Exception ignored) {
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
        System.out.println("CLEANED");
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
