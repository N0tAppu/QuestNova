import dao.PaperDAO;
import database.DatabaseConnection;
import model.BlueprintSlot;
import model.ExamBlueprint;
import model.GeneratedPaper;
import model.GeneratedQuestion;
import model.GenerationResult;
import model.Module;
import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.ReviewQuestion;
import model.Subject;
import service.BlueprintService;
import service.ModuleService;
import service.PaperGenerator;
import service.PaperReviewService;
import service.QuestionService;
import service.SubjectService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 9 test: generated paper review, editing, replacement and
 * transactional finalization.
 *
 * Fixture (own test data, cleaned up in the finally block):
 *   subject "TEST REVIEW GEN"
 *   module R1: 2/Easy x4, 4/Medium x1
 *   module R2: 4/Medium x4, 6/Hard x2
 *   blueprint total 20 = R1 6 (2/Easy x1 + 4/Medium x1)
 *                     + R2 14 (4/Medium x2 + 6/Hard x1)
 *
 * Deliberate pool shapes:
 *   R1/4Medium pool = 1 and fully used  -> no replacement / regeneration
 *   R2/4Medium pool = 4, 2 used        -> replacement with exclusions
 *   R2/6Hard    pool = 2, 1 used       -> regeneration has a spare
 */
public class PaperReviewFinalizationTest {

    private static int passed = 0;
    private static int failed = 0;

    private static final SubjectService subjectService = new SubjectService();
    private static final ModuleService moduleService = new ModuleService();
    private static final QuestionService questionService = new QuestionService();
    private static final BlueprintService blueprintService = new BlueprintService();
    private static final PaperGenerator generator = new PaperGenerator();
    private static final PaperReviewService reviewService = new PaperReviewService();
    private static final PaperDAO paperDAO = new PaperDAO();

    private static final String subjectName = "TEST REVIEW GEN";
    private static final String examType = ExamBlueprint.TYPE_SEMESTER;

    private static int subjectId = -1;
    private static int m1 = -1;
    private static int m2 = -1;
    private static List<BlueprintSlot> slots = new ArrayList<>();
    private static GeneratedPaper generatedA;
    private static PaperReview reviewA;
    private static int paperAId = -1;

    public static void main(String[] args) {
        try {
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
            scenarioI();
            scenarioJ();
            scenarioK();
            scenarioL();
            scenarioM();
            scenarioN();
            scenarioO();
            scenarioP();
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
        m1 = moduleService.addModule(subjectId, "TEST REVIEW M1");
        m2 = moduleService.addModule(subjectId, "TEST REVIEW M2");

        for (int i = 1; i <= 4; i++) {
            addQ(m1, 2, "Easy", "REVIEW-A" + i);
        }
        addQ(m1, 4, "Medium", "REVIEW-B1");
        for (int i = 1; i <= 4; i++) {
            addQ(m2, 4, "Medium", "REVIEW-C" + i);
        }
        addQ(m2, 6, "Hard", "REVIEW-D1");
        addQ(m2, 6, "Hard", "REVIEW-D2");

        check("Setup: subject + 2 modules + 11 questions",
                subjectId > 0 && m1 > 0 && m2 > 0 && allQuestions().size() == 11);

        ExamBlueprint bp = new ExamBlueprint();
        bp.setSubjectId(subjectId);
        bp.setSubjectName(subjectName);
        bp.setExamType(examType);
        bp.setExamDifficulty("Medium");
        bp.setTotalMarks(20);
        bp.selectModule(m1, "TEST REVIEW M1");
        bp.setAllocation(m1, 6);
        blueprintService.addPatternRow(bp, m1, 2, 1);
        blueprintService.addPatternRow(bp, m1, 4, 1);
        bp.selectModule(m2, "TEST REVIEW M2");
        bp.setAllocation(m2, 14);
        blueprintService.addPatternRow(bp, m2, 4, 2);
        blueprintService.addPatternRow(bp, m2, 6, 1);

        slots = blueprintService.generateSlots(bp);
        check("Setup: blueprint yields the expected 5 slots in order (no difficulty yet)",
                slots.size() == 5
                        && matchesSlot(0, m1, 2, null)
                        && matchesSlot(1, m1, 4, null)
                        && matchesSlot(2, m2, 4, null)
                        && matchesSlot(3, m2, 4, null)
                        && matchesSlot(4, m2, 6, null));

        GenerationResult result = generator.generatePaper(subjectId, subjectName,
                examType, "Medium", 20, slots);
        check("Setup: generation succeeds for the fixture blueprint",
                result.isSuccess() && result.getPaper() != null);
        if (!result.isSuccess()) {
            return;
        }
        check("Setup: difficulties assigned; each group offers only one "
                        + "difficulty, so the assignment is forced",
                matchesSlot(0, m1, 2, "Easy")
                        && matchesSlot(1, m1, 4, "Medium")
                        && matchesSlot(2, m2, 4, "Medium")
                        && matchesSlot(3, m2, 4, "Medium")
                        && matchesSlot(4, m2, 6, "Hard"));
        generatedA = result.getPaper();
        reviewA = reviewService.openReview(generatedA, slots);
        check("Setup: review opened from the generated paper",
                reviewA != null && reviewA.getQuestions().size() == 5);
    }

    private static boolean matchesSlot(int index, int moduleId, int marks, String difficulty) {
        BlueprintSlot s = slots.get(index);
        return s.getSequenceNo() == index + 1 && s.getModuleId() == moduleId
                && s.getMarks() == marks
                && java.util.Objects.equals(s.getDifficulty(), difficulty);
    }

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

    // =====================================================================
    // A. openReview builds a faithful, editable, open review
    // =====================================================================

    private static void scenarioA() {
        check("A: review carries subject, exam type and total marks",
                reviewA.getSubjectId() == subjectId
                        && reviewA.getSubjectName().equals(subjectName)
                        && reviewA.getExamType().equals(examType)
                        && reviewA.getTotalMarks() == 20);
        check("A: new review is open (not finalized, no paper id)",
                !reviewA.isFinalized() && reviewA.getPaperId() == -1);
        check("A: review keeps the blueprint slots", reviewA.getSlots().size() == 5);

        boolean mirrored = true;
        List<ReviewQuestion> reviewQuestions = reviewA.getQuestions();
        List<GeneratedQuestion> generatedQuestions = generatedA.getQuestions();
        for (int i = 0; i < generatedQuestions.size(); i++) {
            ReviewQuestion rq = reviewQuestions.get(i);
            GeneratedQuestion gq = generatedQuestions.get(i);
            if (rq.getSequenceNo() != gq.getSequenceNo()
                    || rq.getQuestionId() != gq.getQuestionId()
                    || rq.getSubjectId() != gq.getSubjectId()
                    || rq.getModuleId() != gq.getModuleId()
                    || !rq.getQuestionText().equals(gq.getQuestionText())
                    || !rq.getAnswerText().equals(gq.getAnswerText())
                    || rq.getMarks() != gq.getMarks()
                    || !rq.getDifficulty().equals(gq.getDifficulty())
                    || rq.getModuleName() == null || rq.getModuleName().isEmpty()) {
                mirrored = false;
                break;
            }
        }
        check("A: every review question mirrors the generated question", mirrored);

        boolean sequence = true;
        for (int i = 0; i < reviewQuestions.size(); i++) {
            if (reviewQuestions.get(i).getSequenceNo() != i + 1) {
                sequence = false;
            }
        }
        check("A: questions follow blueprint sequence 1..5", sequence);
    }

    // =====================================================================
    // B. Edit for This Paper is in-memory only
    // =====================================================================

    private static void scenarioB() throws Exception {
        Map<Integer, String> before = snapshotBank();

        reviewService.editQuestionText(reviewA, 1,
                "REVIEW-EDITED-1", "REVIEW-EDITED-ANS1");
        check("B: the review copy carries the edited text",
                reviewA.getQuestionBySequence(1).getQuestionText().equals("REVIEW-EDITED-1")
                        && reviewA.getQuestionBySequence(1).getAnswerText()
                        .equals("REVIEW-EDITED-ANS1"));

        Map<Integer, String> after = snapshotBank();
        check("B: question bank untouched by an edit for this paper",
                before.size() == 11 && before.equals(after));

        boolean refused = false;
        try {
            reviewService.editQuestionText(reviewA, 99, "x", "y");
        } catch (IllegalArgumentException ex) {
            refused = true;
        }
        check("B: editing an unknown sequence is refused", refused);
    }

    // =====================================================================
    // C. Replacement candidates: exact slot eligibility + exclusions
    // =====================================================================

    private static void scenarioC() throws Exception {
        check("C: no candidates when the exact pool is fully used",
                reviewService.getReplacementCandidates(reviewA, 2).isEmpty());

        List<Question> forSlot4 = reviewService.getReplacementCandidates(reviewA, 4);
        boolean exact = true;
        for (Question q : forSlot4) {
            if (q.getModuleId() != m2 || q.getMarks() != 4
                    || !q.getDifficulty().equals("Medium")
                    || q.getSubjectId() != subjectId) {
                exact = false;
            }
        }
        check("C: candidates match the slot pattern exactly", exact && !forSlot4.isEmpty());

        int current4 = reviewA.getQuestionBySequence(4).getQuestionId();
        int other3 = reviewA.getQuestionBySequence(3).getQuestionId();
        boolean excluded = true;
        for (Question q : forSlot4) {
            if (q.getQuestionId() == current4 || q.getQuestionId() == other3) {
                excluded = false;
            }
        }
        check("C: candidates exclude the current question and questions used elsewhere",
                excluded && forSlot4.size() == 2);

        check("C: slot 1 candidates exclude its own question (3 of 4 remain)",
                reviewService.getReplacementCandidates(reviewA, 1).size() == 3);
        check("C: slot 5 has exactly one unused candidate",
                reviewService.getReplacementCandidates(reviewA, 5).size() == 1);
    }

    // =====================================================================
    // D. Replace question: same slot, same sequence, bank untouched
    // =====================================================================

    private static void scenarioD() throws Exception {
        Map<Integer, String> before = snapshotBank();
        int oldId = reviewA.getQuestionBySequence(4).getQuestionId();
        List<Question> candidates = reviewService.getReplacementCandidates(reviewA, 4);
        int newId = candidates.get(0).getQuestionId();

        reviewService.replaceQuestion(reviewA, 4, newId);
        ReviewQuestion replaced = reviewA.getQuestionBySequence(4);
        check("D: replacement installed at the same sequence and slot",
                replaced.getQuestionId() == newId && replaced.getSequenceNo() == 4
                        && replaced.getModuleId() == m2 && replaced.getMarks() == 4
                        && replaced.getDifficulty().equals("Medium"));

        boolean oldGone = true;
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            if (rq.getQuestionId() == oldId) {
                oldGone = false;
            }
        }
        check("D: the replaced question is no longer used in the paper", oldGone);
        check("D: replacement never changes the question bank",
                before.equals(snapshotBank()));
        check("D: paper still validates after a replacement",
                reviewService.validate(reviewA).isEmpty());

        boolean wrongSlotRefused = false;
        try {
            List<Question> easy = questionService.searchQuestions(
                    subjectId, m1, 2, "Easy", null);
            reviewService.replaceQuestion(reviewA, 2, easy.get(0).getQuestionId());
        } catch (IllegalArgumentException ex) {
            wrongSlotRefused = true;
        }
        check("D: a question from another pattern is refused for the slot", wrongSlotRefused);

        boolean usedRefused = false;
        try {
            reviewService.replaceQuestion(reviewA, 4,
                    reviewA.getQuestionBySequence(3).getQuestionId());
        } catch (IllegalArgumentException ex) {
            usedRefused = true;
        }
        check("D: a question already used elsewhere in the paper is refused", usedRefused);
    }

    // =====================================================================
    // E. Regenerate question: another unused question, exact slot kept
    // =====================================================================

    private static void scenarioE() throws Exception {
        Map<Integer, String> before = snapshotBank();
        int oldId = reviewA.getQuestionBySequence(5).getQuestionId();

        reviewService.regenerateQuestion(reviewA, 5);
        ReviewQuestion regenerated = reviewA.getQuestionBySequence(5);
        check("E: regeneration swaps in a different question for the same slot",
                regenerated.getQuestionId() != oldId
                        && regenerated.getSequenceNo() == 5
                        && regenerated.getModuleId() == m2
                        && regenerated.getMarks() == 6
                        && regenerated.getDifficulty().equals("Hard"));

        boolean distinct = true;
        int seen = 0;
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            if (rq.getQuestionId() == regenerated.getQuestionId()) {
                seen++;
            }
        }
        distinct = seen == 1;
        check("E: regenerated question is used exactly once in the paper", distinct);
        check("E: regeneration never changes the question bank",
                before.equals(snapshotBank()));
        check("E: paper still validates after regeneration",
                reviewService.validate(reviewA).isEmpty());
    }

    // =====================================================================
    // F. Regeneration refuses when no unused question remains
    // =====================================================================

    private static void scenarioF() throws Exception {
        boolean refused = false;
        boolean messageOk = false;
        try {
            reviewService.regenerateQuestion(reviewA, 2);
        } catch (IllegalArgumentException ex) {
            refused = true;
            messageOk = ex.getMessage() != null && ex.getMessage().contains(
                    "No unused questions are available for this requirement.");
        }
        check("F: regeneration refused when the pool has no unused question", refused);
        check("F: refusal explains the exact situation", messageOk);
    }

    // =====================================================================
    // G. Update Question Bank: the only operation that writes the bank
    // =====================================================================

    private static void scenarioG() throws Exception {
        Map<Integer, String> before = snapshotBank();
        int editedId = reviewA.getQuestionBySequence(1).getQuestionId();

        reviewService.updateQuestionBank(reviewA, 1);
        Question bank = questionService.getQuestionById(editedId);
        check("G: explicit Update Question Bank writes the review text into the bank",
                bank != null
                        && bank.getQuestionText().equals("REVIEW-EDITED-1")
                        && bank.getAnswerText().equals("REVIEW-EDITED-ANS1"));

        Map<Integer, String> after = snapshotBank();
        boolean onlyOneChanged = before.size() == after.size()
                && !before.get(editedId).equals(after.get(editedId));
        for (Map.Entry<Integer, String> entry : before.entrySet()) {
            if (entry.getKey() != editedId
                    && !entry.getValue().equals(after.get(entry.getKey()))) {
                onlyOneChanged = false;
            }
        }
        check("G: only the selected question changed in the bank", onlyOneChanged);

        boolean missingRefused = false;
        try {
            reviewService.updateQuestionBank(reviewA, 99);
        } catch (IllegalArgumentException ex) {
            missingRefused = true;
        }
        check("G: updating an unknown sequence is refused", missingRefused);
    }

    // =====================================================================
    // H. Validation passes on the healthy review
    // =====================================================================

    private static void scenarioH() {
        check("H: a healthy review validates with no errors",
                reviewService.validate(reviewA).isEmpty());
    }

    // =====================================================================
    // I. Validation catches structural problems (count, sequence)
    // =====================================================================

    private static void scenarioI() {
        PaperReview fresh = reviewService.openReview(generatedA, slots);
        List<ReviewQuestion> questions = deepCopyQuestions(fresh);
        questions.remove(questions.size() - 1);
        questions.get(0).setSequenceNo(7);

        PaperReview broken = new PaperReview(subjectId, subjectName, examType, 20,
                new ArrayList<>(slots), questions);
        List<String> errors = reviewService.validate(broken);
        check("I: missing question reported against the blueprint",
                containsError(errors, "but the blueprint has 5 slots"));
        check("I: broken sequence reported",
                containsError(errors, "not continuous from 1"));
        check("I: a healthy review still validates clean",
                reviewService.validate(fresh).isEmpty());
    }

    // =====================================================================
    // J. Validation catches slot mismatches and bad field values
    // =====================================================================

    private static void scenarioJ() {
        PaperReview fresh = reviewService.openReview(generatedA, slots);
        List<ReviewQuestion> questions = deepCopyQuestions(fresh);
        questions.get(4).setMarks(9);
        questions.get(4).setDifficulty("Easy");
        questions.get(4).setModuleId(m1);
        questions.get(4).setQuestionText("");
        questions.get(3).setQuestionId(questions.get(2).getQuestionId());
        questions.get(0).setQuestionId(0);

        PaperReview broken = new PaperReview(subjectId, subjectName, examType, 20,
                new ArrayList<>(slots), questions);
        List<String> errors = reviewService.validate(broken);
        check("J: marks mismatch reported", containsError(errors, "but the slot requires 6"));
        check("J: difficulty mismatch reported",
                containsError(errors, "but the slot requires Hard"));
        check("J: wrong module reported", containsError(errors, "belongs to another module"));
        check("J: empty question text reported",
                containsError(errors, "question text must not be empty"));
        check("J: duplicate question id reported",
                containsError(errors, "appears more than once"));
        check("J: invalid field values reported",
                containsError(errors, "invalid question details"));
    }

    // =====================================================================
    // K. Finalization refuses an invalid review - and writes nothing
    // =====================================================================

    private static void scenarioK() throws Exception {
        PaperReview fresh = reviewService.openReview(generatedA, slots);
        fresh.getQuestions().get(0).setQuestionText("");

        boolean refused = false;
        boolean messageOk = false;
        try {
            reviewService.finalizePaper(fresh);
        } catch (IllegalArgumentException ex) {
            refused = true;
            messageOk = ex.getMessage() != null
                    && ex.getMessage().contains("question text must not be empty");
        }
        check("K: finalize refuses an invalid review", refused);
        check("K: refusal lists the validation problems", messageOk);
        check("K: refused review stays open", fresh.getPaperId() == -1 && !fresh.isFinalized());
        check("K: refused finalize wrote nothing",
                count("SELECT COUNT(*) FROM question_papers WHERE subject_id = ?",
                        subjectId) == 0
                        && paperQuestionCount() == 0
                        && count("SELECT COUNT(*) FROM paper_blueprints WHERE subject_id = ?",
                        subjectId) == 0
                        && blueprintSlotCount() == 0);
    }

    // =====================================================================
    // L. Finalize is transactional: a mid-save failure writes nothing
    // =====================================================================

    private static void scenarioL() throws Exception {
        int loneId = addQReturnId(m1, 2, "Easy", "REVIEW-LONE");
        BlueprintSlot oneSlot = new BlueprintSlot(1, m1, 2, "Easy");
        oneSlot.setModuleName("TEST REVIEW M1");
        List<BlueprintSlot> oneSlots = new ArrayList<>();
        oneSlots.add(oneSlot);
        ReviewQuestion lone = new ReviewQuestion(1, loneId, subjectId, m1,
                "TEST REVIEW M1", "REVIEW-LONE", "Answer REVIEW-LONE", 2, "Easy");
        List<ReviewQuestion> oneQuestion = new ArrayList<>();
        oneQuestion.add(lone);
        PaperReview loneReview = new PaperReview(subjectId, subjectName, examType, 2,
                oneSlots, oneQuestion);
        check("L: lone review validates before the bank row disappears",
                reviewService.validate(loneReview).isEmpty());

        questionService.deleteQuestion(loneId);

        boolean failed = false;
        try {
            reviewService.finalizePaper(loneReview);
        } catch (SQLException ex) {
            failed = true;
        }
        check("L: finalize fails after its bank question is deleted", failed);
        check("L: failed finalize rolled back every paper table",
                count("SELECT COUNT(*) FROM question_papers WHERE subject_id = ?",
                        subjectId) == 0
                        && paperQuestionCount() == 0
                        && count("SELECT COUNT(*) FROM paper_blueprints WHERE subject_id = ?",
                        subjectId) == 0
                        && blueprintSlotCount() == 0);
        check("L: failed review stays open", loneReview.getPaperId() == -1);
    }

    // =====================================================================
    // M. Finalization persists the paper atomically
    // =====================================================================

    private static void scenarioM() throws Exception {
        paperAId = reviewService.finalizePaper(reviewA);
        check("M: finalize returns a paper id and marks the review finalized",
                paperAId > 0 && reviewA.getPaperId() == paperAId && reviewA.isFinalized());
        check("M: one row in question_papers",
                count("SELECT COUNT(*) FROM question_papers WHERE subject_id = ?",
                        subjectId) == 1);
        check("M: five snapshot rows in paper_questions", paperQuestionCount() == 5);
        check("M: one blueprint row with five slots",
                count("SELECT COUNT(*) FROM paper_blueprints WHERE subject_id = ?",
                        subjectId) == 1 && blueprintSlotCount() == 5);

        List<ReviewQuestion> expected = reviewA.getQuestions();
        boolean snapshotsMatch = true;
        List<int[]> unused = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT question_id, question_text, answer_text, marks, difficulty, "
                             + "sequence_no FROM paper_questions WHERE paper_id = ? "
                             + "ORDER BY sequence_no")) {
            ps.setInt(1, paperAId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    if (index >= expected.size()) {
                        snapshotsMatch = false;
                        break;
                    }
                    ReviewQuestion rq = expected.get(index);
                    if (rs.getInt("question_id") != rq.getQuestionId()
                            || !rs.getString("question_text").equals(rq.getQuestionText())
                            || !rs.getString("answer_text").equals(rq.getAnswerText())
                            || rs.getInt("marks") != rq.getMarks()
                            || !rs.getString("difficulty").equals(rq.getDifficulty())
                            || rs.getInt("sequence_no") != rq.getSequenceNo()) {
                        snapshotsMatch = false;
                        break;
                    }
                    index++;
                }
                if (index != expected.size()) {
                    snapshotsMatch = false;
                }
            }
        }
        check("M: persisted snapshots match the review exactly", snapshotsMatch);

        boolean slotsMatch = true;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT s.sequence_no, s.module_id, s.marks, s.difficulty "
                             + "FROM paper_blueprint_slots s "
                             + "JOIN paper_blueprints b ON s.blueprint_id = b.blueprint_id "
                             + "WHERE b.paper_id = ? ORDER BY s.sequence_no")) {
            ps.setInt(1, paperAId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    if (index >= reviewA.getSlots().size()) {
                        slotsMatch = false;
                        break;
                    }
                    BlueprintSlot slot = reviewA.getSlots().get(index);
                    if (rs.getInt("sequence_no") != slot.getSequenceNo()
                            || rs.getInt("module_id") != slot.getModuleId()
                            || rs.getInt("marks") != slot.getMarks()
                            || !rs.getString("difficulty").equals(slot.getDifficulty())) {
                        slotsMatch = false;
                        break;
                    }
                    index++;
                }
                if (index != reviewA.getSlots().size()) {
                    slotsMatch = false;
                }
            }
        }
        check("M: persisted blueprint slots match the review slots", slotsMatch);

        QuestionPaper listed = null;
        for (QuestionPaper p : paperDAO.getPapers()) {
            if (p.getPaperId() == paperAId) {
                listed = p;
            }
        }
        check("M: history lists the finalized paper with subject and total marks",
                listed != null && listed.getSubjectId() == subjectId
                        && listed.getSubjectName().equals(subjectName)
                        && listed.getTotalMarks() == 20
                        && listed.getGeneratedAt() != null);
    }

    // =====================================================================
    // N. A finalized review is immutable
    // =====================================================================

    private static void scenarioN() throws Exception {
        Map<Integer, String> before = new LinkedHashMap<>();
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            before.put(rq.getSequenceNo(), rq.getQuestionId() + "|" + rq.getQuestionText());
        }

        boolean editRefused = false;
        try {
            reviewService.editQuestionText(reviewA, 1, "x", "y");
        } catch (IllegalStateException ex) {
            editRefused = true;
        }
        check("N: editing a finalized paper is refused", editRefused);

        boolean replaceRefused = false;
        try {
            reviewService.replaceQuestion(reviewA, 1, 1);
        } catch (IllegalStateException ex) {
            replaceRefused = true;
        }
        check("N: replacing a question in a finalized paper is refused", replaceRefused);

        boolean regenRefused = false;
        try {
            reviewService.regenerateQuestion(reviewA, 1);
        } catch (IllegalStateException ex) {
            regenRefused = true;
        }
        check("N: regenerating a question in a finalized paper is refused", regenRefused);

        boolean refinalizeRefused = false;
        try {
            reviewService.finalizePaper(reviewA);
        } catch (IllegalStateException ex) {
            refinalizeRefused = true;
        }
        check("N: finalizing an already finalized paper is refused", refinalizeRefused);

        boolean unchanged = true;
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            String expected = before.get(rq.getSequenceNo());
            if (expected == null
                    || !expected.equals(rq.getQuestionId() + "|" + rq.getQuestionText())) {
                unchanged = false;
            }
        }
        check("N: refused operations left the finalized review untouched", unchanged);
    }

    // =====================================================================
    // O. Reloaded paper is read-only; snapshots outrank the bank
    // =====================================================================

    private static void scenarioO() throws Exception {
        PaperReview loaded = paperDAO.loadPaper(paperAId);
        check("O: finalized paper reloads as a finalized review",
                loaded != null && loaded.isFinalized()
                        && loaded.getPaperId() == paperAId
                        && loaded.getSubjectId() == subjectId
                        && loaded.getSubjectName().equals(subjectName)
                        && loaded.getTotalMarks() == 20
                        && loaded.getExamType().isEmpty());
        if (loaded == null) {
            return;
        }

        boolean same = loaded.getQuestions().size() == reviewA.getQuestions().size()
                && loaded.getSlots().size() == reviewA.getSlots().size();
        for (int i = 0; same && i < reviewA.getQuestions().size(); i++) {
            ReviewQuestion lq = loaded.getQuestions().get(i);
            ReviewQuestion rq = reviewA.getQuestions().get(i);
            if (lq.getSequenceNo() != rq.getSequenceNo()
                    || lq.getQuestionId() != rq.getQuestionId()
                    || lq.getSubjectId() != rq.getSubjectId()
                    || lq.getModuleId() != rq.getModuleId()
                    || !lq.getQuestionText().equals(rq.getQuestionText())
                    || !lq.getAnswerText().equals(rq.getAnswerText())
                    || lq.getMarks() != rq.getMarks()
                    || !lq.getDifficulty().equals(rq.getDifficulty())) {
                same = false;
            }
        }
        for (int i = 0; same && i < reviewA.getSlots().size(); i++) {
            BlueprintSlot ls = loaded.getSlots().get(i);
            BlueprintSlot rs = reviewA.getSlots().get(i);
            if (ls.getSequenceNo() != rs.getSequenceNo()
                    || ls.getModuleId() != rs.getModuleId()
                    || ls.getMarks() != rs.getMarks()
                    || !ls.getDifficulty().equals(rs.getDifficulty())) {
                same = false;
            }
        }
        check("O: reload reproduces every question and slot", same);

        int editedId = reviewA.getQuestionBySequence(1).getQuestionId();
        Question bank = questionService.getQuestionById(editedId);
        bank.setQuestionText("REVIEW-BANK-CHANGED");
        questionService.updateQuestion(bank);

        check("O: bank change does not alter the stored paper",
                paperDAO.loadPaper(paperAId).getQuestionBySequence(1)
                        .getQuestionText().equals("REVIEW-EDITED-1"));
        check("O: the bank carries the new text",
                questionService.getQuestionById(editedId).getQuestionText()
                        .equals("REVIEW-BANK-CHANGED"));

        boolean deleteRefused = false;
        try {
            questionService.deleteQuestion(editedId);
        } catch (IllegalArgumentException ex) {
            deleteRefused = ex.getMessage() != null
                    && ex.getMessage().contains("Paper #");
        } catch (SQLException ex) {
            deleteRefused = false;
        }
        check("O: a question used by a finalized paper cannot be deleted",
                deleteRefused
                        && questionService.getQuestionById(editedId) != null);

        QuestionPaper found = null;
        for (QuestionPaper p : paperDAO.getPapers()) {
            if (p.getPaperId() == paperAId) {
                found = p;
            }
        }
        check("O: history entry keeps subject name, total marks and timestamp",
                found != null && found.getSubjectName().equals(subjectName)
                        && found.getTotalMarks() == 20
                        && found.getGeneratedAt() != null);

        boolean loadRefused = false;
        try {
            reviewService.editQuestionText(loaded, 1, "x", "y");
        } catch (IllegalStateException ex) {
            loadRefused = true;
        }
        check("O: the reloaded paper is read-only", loadRefused);
    }

    // =====================================================================
    // P. Bank deletion protects questions pinned by a finalized paper
    // =====================================================================

    private static void scenarioP() throws Exception {
        // P1: single pinned delete is refused with a message naming the paper
        int pinnedId = reviewA.getQuestionBySequence(1).getQuestionId();
        boolean refused = false;
        try {
            questionService.deleteQuestion(pinnedId);
        } catch (IllegalArgumentException ex) {
            refused = ex.getMessage() != null && ex.getMessage().contains("Paper #");
        } catch (SQLException ex) {
            refused = false;
        }
        check("P1: deleting a pinned question is refused with a paper reference",
                refused);
        check("P1: the pinned question is still in the bank",
                questionService.getQuestionById(pinnedId) != null);

        // P2: preview splits deletable ids from pinned ones
        List<Integer> ids = new ArrayList<>();
        for (ReviewQuestion rq : reviewA.getQuestions()) {
            ids.add(rq.getQuestionId());
        }
        int unpinnedId = addQReturnId(m2, 4, "Medium", "REVIEW-EXTRA-FOR-BULK");
        ids.add(unpinnedId);

        QuestionService.BulkDeletePlan plan = questionService.previewDelete(ids);
        check("P2: the preview keeps the 5 pinned questions",
                plan.getKept().size() == 5 && plan.getDeletable().size() == 1);
        check("P2: the preview names the finalized paper",
                !plan.getKeptPapers().isEmpty()
                        && plan.getKeptPapers().get(0).contains("Paper #"));

        // P3: bulk delete removes only the unpinned question
        QuestionService.BulkDeleteResult result = questionService.deleteQuestions(ids);
        check("P3: bulk delete removes 1 and keeps 5",
                result.getDeleted() == 1 && result.getKept().size() == 5
                        && !result.getKeptPapers().isEmpty());
        check("P3: only the unpinned question disappeared",
                questionService.getQuestionById(unpinnedId) == null
                        && questionService.getQuestionById(pinnedId) != null
                        && allQuestions().size() == 11);

        // P4: the finalized paper still loads with all 5 questions
        PaperReview loaded = paperDAO.loadPaper(paperAId);
        check("P4: the finalized paper is intact",
                loaded != null && loaded.getQuestions().size() == 5
                        && paperQuestionCount() == 5
                        && count("SELECT COUNT(*) FROM question_papers "
                                + "WHERE subject_id = ?", subjectId) == 1);

        // P5: clearing the whole subject keeps the pinned questions
        QuestionService.BulkDeleteResult cleared =
                questionService.deleteAllForSubject(subjectId);
        check("P5: clear-all deletes the 6 unpinned and keeps the 5 pinned",
                cleared.getDeleted() == 6 && cleared.getKept().size() == 5
                        && allQuestions().size() == 5);
        check("P5: the finalized paper still owns its 5 snapshots",
                paperQuestionCount() == 5);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static boolean containsError(List<String> errors, String fragment) {
        for (String error : errors) {
            if (error != null && error.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static List<ReviewQuestion> deepCopyQuestions(PaperReview review) {
        List<ReviewQuestion> copy = new ArrayList<>();
        for (ReviewQuestion question : review.getQuestions()) {
            copy.add(new ReviewQuestion(question));
        }
        return copy;
    }

    private static int addQReturnId(int moduleId, int marks, String difficulty, String text)
            throws Exception {
        Question q = new Question();
        q.setSubjectId(subjectId);
        q.setModuleId(moduleId);
        q.setQuestionText(text);
        q.setAnswerText("Answer " + text);
        q.setMarks(marks);
        q.setDifficulty(difficulty);
        return questionService.addQuestion(q);
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
        if (subjectId <= 0) {
            return;
        }
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
