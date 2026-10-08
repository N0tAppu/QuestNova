import dao.PaperDAO;
import database.DatabaseConnection;
import model.BlueprintSlot;
import model.ExamBlueprint;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 11 end-to-end integrity test: the complete workflow from subject
 * creation to the exported PDFs, exactly as a faculty member would run it.
 *
 * Steps:
 *  1 create subject + modules + questions      2 build and validate blueprint
 *  3 generate the paper                        4 open the review
 *  5 edit for this paper (bank untouched)      6 replace + regenerate (+ refusal)
 *  7 validate the review                       8 finalize atomically
 *  9 bank change keeps the stored snapshot    10 deleting a used question is refused
 * 11 reopen read-only, snapshots authoritative 12 question paper PDF content
 * 13 answer key PDF content                    14 export of an unknown paper refused
 * 15 precise service validation messages       16 cleanup leaves the real data intact
 *
 * Fixture (own test data, cleaned up in the finally block):
 *   subject "TEST STAGE11 E2E"
 *   module M1: 2/Easy x2, 4/Medium x2
 *   module M2: 4/Medium x2, 6/Hard x1
 *   blueprint total 16 = M1 6 (2/Easy + 4/Medium) + M2 10 (4/Medium + 6/Hard)
 */
public class Stage11EndToEndTest {

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

    private static final String subjectName = "TEST STAGE11 E2E";
    private static final String examType = ExamBlueprint.TYPE_SEMESTER;

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;
    private static List<BlueprintSlot> slots = new ArrayList<>();
    private static PaperReview review;
    private static int paperId = -1;
    private static int sourceId = -1;
    private static String snapshotSeq1 = "";

    private static Path tempDir;
    private static File paperFile;
    private static File keyFile;

    public static void main(String[] args) {
        try {
            tempDir = Files.createTempDirectory("qpg-stage11");
            paperFile = new File(tempDir.toFile(), "paper.pdf");
            keyFile = new File(tempDir.toFile(), "key.pdf");
            preClean();
            step1();
            step2();
            step3();
            step4();
            step5();
            step6();
            step7();
            step8();
            step9();
            step10();
            step11();
            step12();
            step13();
            step14();
            step15();
        } catch (Exception ex) {
            failed++;
            System.out.println("UNEXPECTED EXCEPTION: " + ex);
            ex.printStackTrace();
        } finally {
            cleanup();
        }
        step16();

        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // =====================================================================
    // 1. Create subject, modules and questions
    // =====================================================================

    private static void step1() throws Exception {
        subjectId = subjectService.addSubject(subjectName);
        m1 = moduleService.addModule(subjectId, "E2E M1");
        m2 = moduleService.addModule(subjectId, "E2E M2");

        addQ(m1, 2, "Easy", "E2E-E1 What is encapsulation?");
        addQ(m1, 2, "Easy", "E2E-E2 What is abstraction?");
        addQ(m1, 4, "Medium", "E2E-B1 Explain inheritance with an example.");
        addQ(m1, 4, "Medium", "E2E-B2 Explain method overriding with an example.");
        addQ(m2, 4, "Medium", "E2E-M1 Explain polymorphism with an example.");
        addQ(m2, 4, "Medium", "E2E-M2 Explain interfaces with an example.");
        addQ(m2, 6, "Hard", "E2E-H1 Design a class hierarchy with justification.");

        check("1: subject, two modules and seven questions created",
                subjectId > 0 && m1 > 0 && m2 > 0 && allQuestions().size() == 7);
    }

    // =====================================================================
    // 2. Build and validate the blueprint
    // =====================================================================

    private static void step2() {
        ExamBlueprint bp = new ExamBlueprint();
        bp.setSubjectId(subjectId);
        bp.setSubjectName(subjectName);
        bp.setExamType(examType);
        bp.setExamDifficulty("Medium");
        bp.setTotalMarks(16);
        bp.selectModule(m1, "E2E M1");
        bp.setAllocation(m1, 6);
        blueprintService.addPatternRow(bp, m1, 2, 1);
        blueprintService.addPatternRow(bp, m1, 4, 1);
        bp.selectModule(m2, "E2E M2");
        bp.setAllocation(m2, 10);
        blueprintService.addPatternRow(bp, m2, 4, 1);
        blueprintService.addPatternRow(bp, m2, 6, 1);

        check("2: the blueprint validates with no problems",
                blueprintService.validateBlueprint(bp).isEmpty());

        slots = blueprintService.generateSlots(bp);
        check("2: four slots in blueprint order (no difficulty yet)",
                slots.size() == 4
                        && matchesSlot(0, m1, 2, null)
                        && matchesSlot(1, m1, 4, null)
                        && matchesSlot(2, m2, 4, null)
                        && matchesSlot(3, m2, 6, null));
    }

    private static boolean matchesSlot(int index, int moduleId, int marks, String difficulty) {
        BlueprintSlot s = slots.get(index);
        return s.getSequenceNo() == index + 1 && s.getModuleId() == moduleId
                && s.getMarks() == marks
                && java.util.Objects.equals(s.getDifficulty(), difficulty);
    }

    // =====================================================================
    // 3. Generate the paper
    // =====================================================================

    private static model.GeneratedPaper generatedPaper;

    private static void step3() {
        GenerationResult result = generator.generatePaper(subjectId, subjectName,
                examType, "Medium", 16, slots);
        check("3: generation succeeds for the blueprint",
                result.isSuccess() && result.getPaper() != null);
        if (!result.isSuccess()) {
            return;
        }
        check("3: difficulties assigned - each group offers only one "
                        + "difficulty, so the assignment is forced",
                matchesSlot(0, m1, 2, "Easy")
                        && matchesSlot(1, m1, 4, "Medium")
                        && matchesSlot(2, m2, 4, "Medium")
                        && matchesSlot(3, m2, 6, "Hard"));
        generatedPaper = result.getPaper();
        check("3: the generated paper holds one question per slot",
                generatedPaper.getQuestions().size() == 4);
    }

    // =====================================================================
    // 4. Open the review
    // =====================================================================

    private static void step4() {
        if (generatedPaper == null) {
            return;
        }
        review = reviewService.openReview(generatedPaper, slots);
        check("4: the review mirrors subject, type, marks and state",
                review != null && review.getSubjectId() == subjectId
                        && review.getSubjectName().equals(subjectName)
                        && review.getExamType().equals(examType)
                        && review.getTotalMarks() == 16
                        && !review.isFinalized() && review.getPaperId() == -1);

        boolean mirrored = review != null
                && review.getQuestions().size() == generatedPaper.getQuestions().size();
        if (mirrored) {
            for (int i = 0; i < review.getQuestions().size(); i++) {
                ReviewQuestion rq = review.getQuestions().get(i);
                model.GeneratedQuestion gq = generatedPaper.getQuestions().get(i);
                if (rq.getSequenceNo() != i + 1
                        || rq.getQuestionId() != gq.getQuestionId()
                        || !rq.getQuestionText().equals(gq.getQuestionText())
                        || !rq.getAnswerText().equals(gq.getAnswerText())
                        || rq.getModuleName() == null || rq.getModuleName().isEmpty()) {
                    mirrored = false;
                    break;
                }
            }
        }
        check("4: every review question mirrors the generated paper in order", mirrored);
    }

    // =====================================================================
    // 5. Edit for this paper leaves the bank untouched
    // =====================================================================

    private static void step5() throws Exception {
        Map<Integer, String> before = snapshotBank();
        reviewService.editQuestionText(review, 1, "E2E-EDITED-Q", "E2E-EDITED-A");
        check("5: the review copy carries the edited text",
                review.getQuestionBySequence(1).getQuestionText().equals("E2E-EDITED-Q")
                        && review.getQuestionBySequence(1).getAnswerText()
                        .equals("E2E-EDITED-A"));
        check("5: the question bank is untouched by the edit",
                before.size() == 7 && before.equals(snapshotBank()));
    }

    // =====================================================================
    // 6. Replace and regenerate keep the slots; refusal without a spare
    // =====================================================================

    private static void step6() throws Exception {
        Map<Integer, String> before = snapshotBank();

        List<Question> candidates = reviewService.getReplacementCandidates(review, 3);
        check("6: one unused candidate for the second medium slot", candidates.size() == 1);
        int replacementId = candidates.get(0).getQuestionId();
        reviewService.replaceQuestion(review, 3, replacementId);
        ReviewQuestion replaced = review.getQuestionBySequence(3);
        check("6: replacement installed with sequence and slot kept",
                replaced.getQuestionId() == replacementId && replaced.getSequenceNo() == 3
                        && replaced.getModuleId() == m2 && replaced.getMarks() == 4
                        && replaced.getDifficulty().equals("Medium"));

        int oldSeq2 = review.getQuestionBySequence(2).getQuestionId();
        reviewService.regenerateQuestion(review, 2);
        ReviewQuestion regenerated = review.getQuestionBySequence(2);
        check("6: regeneration draws the spare medium question of module 1",
                regenerated.getQuestionId() != oldSeq2 && regenerated.getSequenceNo() == 2
                        && regenerated.getModuleId() == m1 && regenerated.getMarks() == 4
                        && regenerated.getDifficulty().equals("Medium"));

        check("6: replacement and regeneration never touched the bank",
                before.equals(snapshotBank()));
        check("6: the review still validates clean after both operations",
                reviewService.validate(review).isEmpty());

        boolean refused = false;
        boolean messageOk = false;
        try {
            reviewService.regenerateQuestion(review, 4);
        } catch (IllegalArgumentException ex) {
            refused = true;
            messageOk = ex.getMessage() != null
                    && ex.getMessage().contains("No unused questions are available");
        }
        check("6: regeneration of the fully used hard slot is refused", refused);
        check("6: the refusal explains the situation", messageOk);
    }

    // =====================================================================
    // 7. Validation passes on the healthy review
    // =====================================================================

    private static void step7() {
        check("7: the healthy review validates with no problems",
                reviewService.validate(review).isEmpty());
    }

    // =====================================================================
    // 8. Finalize: everything is persisted atomically
    // =====================================================================

    private static void step8() throws Exception {
        paperId = reviewService.finalizePaper(review);
        check("8: finalize returns an id and marks the review finalized",
                paperId > 0 && review.getPaperId() == paperId && review.isFinalized());
        check("8: one paper row for the subject",
                count("SELECT COUNT(*) FROM question_papers WHERE subject_id = ?",
                        subjectId) == 1);
        check("8: four snapshot rows", paperQuestionCount() == 4);
        check("8: one blueprint row with four slots",
                count("SELECT COUNT(*) FROM paper_blueprints WHERE subject_id = ?",
                        subjectId) == 1 && blueprintSlotCount() == 4);

        boolean snapshotsMatch = true;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sequence_no, question_text, answer_text FROM paper_questions "
                             + "WHERE paper_id = ? ORDER BY sequence_no")) {
            ps.setInt(1, paperId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    if (index >= review.getQuestions().size()) {
                        snapshotsMatch = false;
                        break;
                    }
                    ReviewQuestion rq = review.getQuestions().get(index);
                    if (rs.getInt("sequence_no") != rq.getSequenceNo()
                            || !rs.getString("question_text").equals(rq.getQuestionText())
                            || !rs.getString("answer_text").equals(rq.getAnswerText())) {
                        snapshotsMatch = false;
                        break;
                    }
                    index++;
                }
                if (index != review.getQuestions().size()) {
                    snapshotsMatch = false;
                }
            }
        }
        check("8: the stored snapshots match the finalized review", snapshotsMatch);

        QuestionPaper listed = null;
        for (QuestionPaper p : paperDAO.getPapers()) {
            if (p.getPaperId() == paperId) {
                listed = p;
            }
        }
        check("8: the history lists the paper with subject and total marks",
                listed != null && listed.getSubjectId() == subjectId
                        && listed.getSubjectName().equals(subjectName)
                        && listed.getTotalMarks() == 16
                        && listed.getGeneratedAt() != null);

        sourceId = review.getQuestionBySequence(1).getQuestionId();
        snapshotSeq1 = review.getQuestionBySequence(1).getQuestionText();
        check("8: the snapshot deliberately differs from the bank text",
                !questionService.getQuestionById(sourceId).getQuestionText()
                        .equals(snapshotSeq1));
    }

    // =====================================================================
    // 9. A later bank change does not alter the stored paper
    // =====================================================================

    private static void step9() throws Exception {
        Question bankQuestion = questionService.getQuestionById(sourceId);
        bankQuestion.setQuestionText("E2E-BANK-CHANGED-AFTER-FINALIZE");
        questionService.updateQuestion(bankQuestion);
        check("9: the bank now carries the new text",
                questionService.getQuestionById(sourceId).getQuestionText()
                        .equals("E2E-BANK-CHANGED-AFTER-FINALIZE"));
        check("9: the stored snapshot keeps the finalized text",
                paperDAO.loadPaper(paperId).getQuestionBySequence(1)
                        .getQuestionText().equals(snapshotSeq1));
    }

    // =====================================================================
    // 10. A question used by a finalized paper cannot be deleted
    // =====================================================================

    private static void step10() throws Exception {
        boolean refused = false;
        try {
            questionService.deleteQuestion(sourceId);
        } catch (IllegalArgumentException ex) {
            refused = ex.getMessage() != null && ex.getMessage().contains("Paper #");
        } catch (SQLException ex) {
            refused = false;
        }
        check("10: deleting the used question is refused with a message naming the paper",
                refused);
        check("10: the question is still in the bank",
                questionService.getQuestionById(sourceId) != null);
    }

    // =====================================================================
    // 11. Reopened paper: read-only, snapshots authoritative
    // =====================================================================

    private static void step11() throws Exception {
        PaperReview loaded = paperDAO.loadPaper(paperId);
        check("11: the paper reopens finalized with its identity",
                loaded != null && loaded.isFinalized()
                        && loaded.getPaperId() == paperId
                        && loaded.getSubjectId() == subjectId
                        && loaded.getSubjectName().equals(subjectName)
                        && loaded.getTotalMarks() == 16
                        && loaded.getExamType().isEmpty());
        if (loaded == null) {
            return;
        }

        boolean same = loaded.getQuestions().size() == 4 && loaded.getSlots().size() == 4;
        for (int i = 0; same && i < 4; i++) {
            ReviewQuestion lq = loaded.getQuestions().get(i);
            ReviewQuestion rq = review.getQuestions().get(i);
            if (lq.getSequenceNo() != rq.getSequenceNo()
                    || lq.getQuestionId() != rq.getQuestionId()
                    || !lq.getQuestionText().equals(rq.getQuestionText())
                    || !lq.getAnswerText().equals(rq.getAnswerText())
                    || lq.getMarks() != rq.getMarks()
                    || !lq.getDifficulty().equals(rq.getDifficulty())) {
                same = false;
            }
        }
        for (int i = 0; same && i < 4; i++) {
            BlueprintSlot ls = loaded.getSlots().get(i);
            BlueprintSlot rs = review.getSlots().get(i);
            if (ls.getSequenceNo() != rs.getSequenceNo()
                    || ls.getModuleId() != rs.getModuleId()
                    || ls.getMarks() != rs.getMarks()
                    || !ls.getDifficulty().equals(rs.getDifficulty())) {
                same = false;
            }
        }
        check("11: reopening reproduces every question and slot", same);

        check("11: the snapshots outrank the changed bank",
                loaded.getQuestionBySequence(1).getQuestionText().equals(snapshotSeq1)
                        && questionService.getQuestionById(sourceId).getQuestionText()
                        .equals("E2E-BANK-CHANGED-AFTER-FINALIZE"));

        boolean editRefused = false;
        try {
            reviewService.editQuestionText(loaded, 1, "x", "y");
        } catch (IllegalStateException ex) {
            editRefused = true;
        }
        check("11: the reopened paper refuses edits", editRefused);

        boolean refinalizeRefused = false;
        try {
            reviewService.finalizePaper(loaded);
        } catch (IllegalStateException ex) {
            refinalizeRefused = true;
        }
        check("11: the reopened paper refuses a second finalization", refinalizeRefused);
    }

    // =====================================================================
    // 12. Question paper PDF content
    // =====================================================================

    private static void step12() throws Exception {
        File written = exportService.exportQuestionPaper(paperId, paperFile);
        check("12: export returns the target file",
                written != null && written.equals(paperFile) && paperFile.exists());
        check("12: the file is a non-empty real PDF",
                paperFile.length() > 1000 && magic(paperFile).equals("%PDF"));

        int pages = pageCount(paperFile);
        check("12: the document has at least one page", pages >= 1);

        String body = bodyText(extract(paperFile));
        check("12: header says QUESTION PAPER", body.contains("QUESTION PAPER"));
        check("12: header carries the subject", body.contains(subjectName));
        check("12: header carries the maximum marks", body.contains("Maximum Marks: 16"));

        boolean allQuestions = true;
        for (int seq = 1; seq <= 4; seq++) {
            String text = normalize(review.getQuestionBySequence(seq).getQuestionText());
            if (!body.contains(text)) {
                allQuestions = false;
                System.out.println("  missing in PDF: " + abbreviate(text));
            }
        }
        check("12: every finalized question text appears in the PDF", allQuestions);
        check("12: answers never appear in the question paper",
                !body.contains("E2E-EDITED-A") && !body.contains("Answer E2E-H1"));
        check("12: the footer reports the page numbers",
                normalize(extractPage(paperFile, 1)).contains("Page 1 of " + pages));
    }

    // =====================================================================
    // 13. Answer key PDF content
    // =====================================================================

    private static void step13() throws Exception {
        File written = exportService.exportAnswerKey(paperId, keyFile);
        check("13: export returns the target file",
                written != null && written.equals(keyFile) && keyFile.exists());
        check("13: the file is a non-empty real PDF",
                keyFile.length() > 1000 && magic(keyFile).equals("%PDF"));

        int pages = pageCount(keyFile);
        String body = bodyText(extract(keyFile));
        check("13: header says ANSWER KEY", body.contains("ANSWER KEY"));
        check("13: header carries the subject", body.contains(subjectName));

        boolean allAnswers = true;
        for (int seq = 1; seq <= 4; seq++) {
            String answer = normalize(review.getQuestionBySequence(seq).getAnswerText());
            if (!body.contains(answer)) {
                allAnswers = false;
                System.out.println("  missing in key: " + abbreviate(answer));
            }
        }
        check("13: every finalized answer appears in the answer key", allAnswers);
        check("13: the key marks each answer",
                body.contains("Marks: 2") && body.contains("Marks: 4")
                        && body.contains("Marks: 6"));
        check("13: the key is not the question paper",
                !body.contains("QUESTION PAPER")
                        && !body.contains(normalize(
                        review.getQuestionBySequence(1).getQuestionText())));
        check("13: the footer reports the page numbers",
                normalize(extractPage(keyFile, 1)).contains("Page 1 of " + pages));
    }

    // =====================================================================
    // 14. Exporting an unknown paper is refused
    // =====================================================================

    private static void step14() {
        File missing = new File(tempDir.toFile(), "never.pdf");
        boolean paperRefused = false;
        try {
            exportService.exportQuestionPaper(999999, missing);
        } catch (IllegalArgumentException ex) {
            paperRefused = true;
        } catch (Exception ex) {
            // the expected refusal is IllegalArgumentException
        }
        check("14: the question paper export of an unknown paper is refused",
                paperRefused && !missing.exists());

        boolean keyRefused = false;
        try {
            exportService.exportAnswerKey(999999, missing);
        } catch (IllegalArgumentException ex) {
            keyRefused = true;
        } catch (Exception ex) {
            // the expected refusal is IllegalArgumentException
        }
        check("14: the answer key export of an unknown paper is refused",
                keyRefused && !missing.exists());
    }

    // =====================================================================
    // 15. Service validation is precise (fields before existence)
    // =====================================================================

    private static void step15() {
        check("15: updateQuestion(null) is refused as IllegalArgumentException",
                expectIAE(() -> {
                    try {
                        questionService.updateQuestion(null);
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, null));

        Question ghost = new Question();
        ghost.setQuestionId(999999);
        ghost.setQuestionText("   ");
        ghost.setAnswerText("answer");
        ghost.setMarks(2);
        ghost.setDifficulty("Easy");
        check("15: field validation runs before the existence check",
                expectIAE(() -> {
                    try {
                        questionService.updateQuestion(ghost);
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, "Question text must not be empty"));

        check("15: subject name validation runs before the existence check",
                expectIAE(() -> {
                    try {
                        subjectService.updateSubject(999999, "   ");
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, "must not be empty"));
    }

    // =====================================================================
    // 16. Cleanup left the real data intact and removed the fixture
    // =====================================================================

    private static void step16() {
        List<Subject> subjects;
        try {
            subjects = subjectService.getAllSubjects();
        } catch (Exception ex) {
            failed++;
            System.out.println("FAIL: 16: cannot read the subjects -> " + ex);
            return;
        }
        boolean fixtureGone = true;
        boolean userOop = false;
        boolean userDs = false;
        for (Subject s : subjects) {
            if (subjectName.equals(s.getSubjectName())) {
                fixtureGone = false;
            }
            if ("Object Oriented Programming".equals(s.getSubjectName())) {
                userOop = true;
            }
            if ("Data Structures".equals(s.getSubjectName())) {
                userDs = true;
            }
        }
        check("16: the test subject was removed completely", fixtureGone);
        check("16: the user's own subjects are untouched", userOop && userDs);

        int papersLeft = -1;
        try {
            papersLeft = subjectId > 0
                    ? count("SELECT COUNT(*) FROM question_papers WHERE subject_id = ?",
                    subjectId)
                    : 0;
        } catch (Exception ex) {
            papersLeft = -1;
        }
        check("16: no papers of the test subject remain", papersLeft == 0);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void addQ(int moduleId, int marks, String difficulty, String text)
            throws Exception {
        Question q = new Question();
        q.setSubjectId(subjectId);
        q.setModuleId(moduleId);
        q.setQuestionText(text);
        q.setAnswerText("Answer " + text);
        q.setMarks(marks);
        q.setDifficulty(difficulty);
        questionService.addQuestion(q);
    }

    /** True iff the action throws IllegalArgumentException (with the fragment). */
    private static boolean expectIAE(Runnable action, String fragment) {
        try {
            action.run();
            return false;
        } catch (Throwable t) {
            if (!(t instanceof IllegalArgumentException)) {
                return false;
            }
            String message = ((IllegalArgumentException) t).getMessage();
            return fragment == null || (message != null && message.contains(fragment));
        }
    }

    private static Map<Integer, String> snapshotBank() throws Exception {
        Map<Integer, String> snapshot = new LinkedHashMap<>();
        for (Question q : allQuestions()) {
            snapshot.put(q.getQuestionId(),
                    q.getModuleId() + "|" + q.getMarks() + "|" + q.getDifficulty()
                            + "|" + q.getQuestionText() + "|" + q.getAnswerText());
        }
        return snapshot;
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

    private static int blueprintSlotCount() throws Exception {
        return count("SELECT COUNT(*) FROM paper_blueprint_slots s "
                        + "JOIN paper_blueprints b ON s.blueprint_id = b.blueprint_id "
                        + "WHERE b.subject_id = ?", subjectId);
    }

    private static String extract(File file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(document);
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

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /** Extracted text without the page footers (content comparisons). */
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
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
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
            for (int mid : new int[]{m1, m2}) {
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
        System.out.println("CLEANED");
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
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
