package service;

import dao.PaperDAO;
import model.BlueprintSlot;
import model.GeneratedPaper;
import model.PaperReview;
import model.Question;
import model.QuestionPaper;
import model.ReviewQuestion;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stage 9: review, editing and finalization of a generated paper.
 *
 * Two strictly separated operations:
 *  - "Edit for This Paper" / replace / regenerate change ONLY the in-memory
 *    review, until the paper is finalized.
 *  - "Update Question Bank" is the single explicit operation that writes a
 *    change into the question bank.
 *
 * Replacement and regeneration use the same eligibility key as generation
 * (subject + module + marks + difficulty, never text) and can never reuse a
 * question that is already used elsewhere in the same paper.
 *
 * Finalization re-validates from scratch and then saves everything in ONE
 * transaction (paper + snapshots + blueprint + slots). After that the review
 * is immutable.
 */
public class PaperReviewService {

    private static final List<String> VALID_DIFFICULTIES = Arrays.asList(
            Question.DIFFICULTY_EASY, Question.DIFFICULTY_MEDIUM, Question.DIFFICULTY_HARD);

    private final PaperDAO paperDAO = new PaperDAO();
    private final QuestionService questionService = new QuestionService();

    // =====================================================================
    // Opening a review
    // =====================================================================

    /** Opens an editable, in-memory review over a freshly generated paper. */
    public PaperReview openReview(GeneratedPaper paper, List<BlueprintSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("The blueprint contains no questions.");
        }
        return PaperReview.from(paper, slots);
    }

    // =====================================================================
    // Edit for This Paper (in-memory only)
    // =====================================================================

    /**
     * Replaces the text and answer of one question inside THIS paper.
     * The question bank is never touched by this operation.
     *
     * @throws IllegalStateException if the paper is finalized
     * @throws IllegalArgumentException for unknown sequence or empty text
     */
    public void editQuestionText(PaperReview review, int sequenceNo,
                                 String questionText, String answerText) {
        ensureOpen(review, "edited");
        ReviewQuestion question = requireQuestion(review, sequenceNo);
        if (questionText == null || questionText.trim().isEmpty()) {
            throw new IllegalArgumentException("Question text must not be empty.");
        }
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new IllegalArgumentException("Answer must not be empty.");
        }
        question.setQuestionText(questionText);
        question.setAnswerText(answerText);
    }

    // =====================================================================
    // Replace / Regenerate (exact slot eligibility, bank read-only)
    // =====================================================================

    /**
     * All bank questions that could replace the question at this position:
     * exact slot match (subject, module, marks, difficulty), excluding the
     * current question and every question already used in this paper.
     */
    public List<Question> getReplacementCandidates(PaperReview review, int sequenceNo)
            throws SQLException {
        BlueprintSlot slot = requireSlot(review, sequenceNo);
        List<Question> pool = questionService.searchQuestions(
                review.getSubjectId(), slot.getModuleId(), slot.getMarks(),
                slot.getDifficulty(), null);
        Set<Integer> usedInPaper = new HashSet<>();
        for (ReviewQuestion question : review.getQuestions()) {
            usedInPaper.add(question.getQuestionId());
        }
        List<Question> candidates = new ArrayList<>();
        for (Question question : pool) {
            if (!usedInPaper.contains(question.getQuestionId())) {
                candidates.add(question);
            }
        }
        return candidates;
    }

    /**
     * Swaps the question at this position for another eligible one.
     * The sequence number and slot stay exactly as they are; the bank is
     * read-only here.
     *
     * @throws IllegalStateException if the paper is finalized
     * @throws IllegalArgumentException if the id is unknown, ineligible or
     *                                  already used elsewhere in the paper
     */
    public void replaceQuestion(PaperReview review, int sequenceNo, int questionId)
            throws SQLException {
        ensureOpen(review, "changed");
        ReviewQuestion current = requireQuestion(review, sequenceNo);
        if (questionId == current.getQuestionId()) {
            throw new IllegalArgumentException(
                    "That question is already selected for this slot.");
        }
        for (ReviewQuestion question : review.getQuestions()) {
            if (question.getQuestionId() == questionId) {
                throw new IllegalArgumentException(
                        "That question is already used elsewhere in this paper.");
            }
        }
        BlueprintSlot slot = requireSlot(review, sequenceNo);
        Question replacement = null;
        for (Question candidate : questionService.searchQuestions(
                review.getSubjectId(), slot.getModuleId(), slot.getMarks(),
                slot.getDifficulty(), null)) {
            if (candidate.getQuestionId() == questionId) {
                replacement = candidate;
                break;
            }
        }
        if (replacement == null) {
            throw new IllegalArgumentException(
                    "That question does not match this slot (module, marks and difficulty).");
        }
        applyReplacement(review, current, replacement);
    }

    /**
     * Draws another unused question for this position from the exact
     * eligible pool (shuffle, one draw - no retry loops).
     *
     * @throws IllegalStateException if the paper is finalized
     * @throws IllegalArgumentException if no unused question remains
     */
    public void regenerateQuestion(PaperReview review, int sequenceNo) throws SQLException {
        ensureOpen(review, "changed");
        ReviewQuestion current = requireQuestion(review, sequenceNo);
        List<Question> candidates = getReplacementCandidates(review, sequenceNo);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "No unused questions are available for this requirement.");
        }
        Collections.shuffle(candidates);
        applyReplacement(review, current, candidates.get(0));
    }

    // =====================================================================
    // Update Question Bank (the ONLY write to the question bank)
    // =====================================================================

    /**
     * Writes the current review text and answer of this question back into
     * the question bank. Deliberately allowed even after finalization: it
     * changes the bank, never the paper - stored snapshots stay authoritative.
     *
     * @throws IllegalArgumentException for unknown sequence or missing bank row
     */
    public void updateQuestionBank(PaperReview review, int sequenceNo) throws SQLException {
        ReviewQuestion question = requireQuestion(review, sequenceNo);
        Question bankQuestion = questionService.getQuestionById(question.getQuestionId());
        if (bankQuestion == null) {
            throw new IllegalArgumentException(
                    "The original question no longer exists in the question bank.");
        }
        bankQuestion.setQuestionText(question.getQuestionText());
        bankQuestion.setAnswerText(question.getAnswerText());
        questionService.updateQuestion(bankQuestion);
    }

    // =====================================================================
    // Validation
    // =====================================================================

    /**
     * Checks the review against its blueprint. Pure in-memory check, used
     * for the live status label and again - from scratch - right before
     * finalization.
     *
     * @return every problem found (empty list = valid)
     */
    public List<String> validate(PaperReview review) {
        List<String> errors = new ArrayList<>();
        if (review == null) {
            errors.add("There is no paper to validate.");
            return errors;
        }
        List<BlueprintSlot> slots = review.getSlots();
        List<ReviewQuestion> questions = review.getQuestions();

        // 1: exactly one question per slot
        if (questions.size() != slots.size()) {
            errors.add("The paper has " + questions.size()
                    + " questions, but the blueprint has " + slots.size() + " slots.");
        }

        Set<Integer> seenIds = new HashSet<>();
        int questionSum = 0;
        int limit = Math.min(slots.size(), questions.size());
        for (int i = 0; i < limit; i++) {
            BlueprintSlot slot = slots.get(i);
            ReviewQuestion question = questions.get(i);

            // 2: sequence numbers line up with the blueprint
            if (question.getSequenceNo() != slot.getSequenceNo()) {
                errors.add("Slot " + slot.getSequenceNo() + ": question has sequence number "
                        + question.getSequenceNo() + ".");
            }
            // 3: no question used twice
            if (!seenIds.add(question.getQuestionId())) {
                errors.add("Question id " + question.getQuestionId()
                        + " appears more than once.");
            }
            // 4: usable field values
            if (question.getQuestionId() <= 0 || question.getSubjectId() <= 0
                    || question.getModuleId() <= 0 || question.getMarks() <= 0
                    || question.getDifficulty() == null
                    || !VALID_DIFFICULTIES.contains(question.getDifficulty())
                    || question.getSequenceNo() <= 0) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": invalid question details (id, subject, module, marks or difficulty).");
            }
            // 5: exact eligibility of the selection
            if (question.getSubjectId() != review.getSubjectId()) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": this question belongs to another subject.");
            }
            if (question.getModuleId() != slot.getModuleId()) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": this question belongs to another module.");
            }
            if (question.getMarks() != slot.getMarks()) {
                errors.add("Slot " + slot.getSequenceNo() + ": this question has "
                        + question.getMarks() + " marks, but the slot requires "
                        + slot.getMarks() + ".");
            }
            if (!Objects.equals(question.getDifficulty(), slot.getDifficulty())) {
                errors.add("Slot " + slot.getSequenceNo() + ": this question is "
                        + question.getDifficulty() + ", but the slot requires "
                        + slot.getDifficulty() + ".");
            }
            // 6: content present
            if (question.getQuestionText() == null
                    || question.getQuestionText().trim().isEmpty()) {
                errors.add("Slot " + slot.getSequenceNo()
                        + ": question text must not be empty.");
            }
            if (question.getAnswerText() == null
                    || question.getAnswerText().trim().isEmpty()) {
                errors.add("Slot " + slot.getSequenceNo() + ": answer must not be empty.");
            }
            questionSum += question.getMarks();
        }

        // 7: continuous 1..n sequence
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getSequenceNo() != i + 1) {
                errors.add("Sequence numbers are not continuous from 1.");
                break;
            }
        }

        // 8 + 9: totals agree with the blueprint and the examination
        int slotSum = 0;
        for (BlueprintSlot slot : slots) {
            slotSum += slot.getMarks();
        }
        if (questionSum != slotSum) {
            errors.add("The questions total " + questionSum
                    + " marks, but the blueprint slots total " + slotSum + " marks.");
        }
        if (questionSum != review.getTotalMarks()) {
            errors.add("The questions total " + questionSum
                    + " marks, but the examination total marks is "
                    + review.getTotalMarks() + ".");
        }
        return errors;
    }

    // =====================================================================
    // Finalization
    // =====================================================================

    /**
     * Validates from scratch and saves the paper atomically: paper header,
     * question snapshots, blueprint and slots in ONE transaction.
     * On success the review becomes immutable.
     *
     * @return the generated paper id
     * @throws IllegalStateException if already finalized
     * @throws IllegalArgumentException if validation finds any problem
     * @throws SQLException if the save fails - nothing is persisted then
     */
    public int finalizePaper(PaperReview review) throws SQLException {
        if (review == null) {
            throw new IllegalArgumentException("There is no paper to finalize.");
        }
        if (review.isFinalized()) {
            throw new IllegalStateException(
                    "This paper has already been finalized and cannot be changed.");
        }
        List<String> errors = validate(review);
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("The paper cannot be finalized:");
            for (String error : errors) {
                sb.append("\n- ").append(error);
            }
            throw new IllegalArgumentException(sb.toString());
        }
        int paperId = paperDAO.saveFinalizedPaper(review);
        review.markFinalized(paperId);
        return paperId;
    }

    /**
     * The finalized-paper history, newest first.
     */
    public List<QuestionPaper> getPapers() throws SQLException {
        return paperDAO.getPapers();
    }

    /**
     * Reloads a finalized paper as a read-only review (snapshots are the
     * authoritative text), or null when it does not exist.
     */
    public PaperReview loadPaper(int paperId) throws SQLException {
        return paperDAO.loadPaper(paperId);
    }

    /**
     * Stage 12: deletes a finalized question paper. Only this paper's
     * stored rows are removed (snapshots, blueprint and slots cascade);
     * the source questions in the question bank are never touched, and
     * no other paper is affected.
     *
     * @throws IllegalArgumentException for an invalid or unknown paper id
     * @throws SQLException if the delete fails - nothing is removed then
     */
    public void deletePaper(int paperId) throws SQLException {
        if (paperId <= 0) {
            throw new IllegalArgumentException("Please select a question paper to delete.");
        }
        if (paperDAO.loadPaper(paperId) == null) {
            throw new IllegalArgumentException(
                    "That question paper no longer exists. Please refresh the list.");
        }
        paperDAO.deletePaper(paperId);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void ensureOpen(PaperReview review, String verb) {
        if (review == null) {
            throw new IllegalArgumentException("There is no paper to review.");
        }
        if (review.isFinalized()) {
            throw new IllegalStateException("This paper has already been finalized"
                    + " and cannot be " + verb + ".");
        }
    }

    private ReviewQuestion requireQuestion(PaperReview review, int sequenceNo) {
        if (review == null) {
            throw new IllegalArgumentException("There is no paper to review.");
        }
        ReviewQuestion question = review.getQuestionBySequence(sequenceNo);
        if (question == null) {
            throw new IllegalArgumentException(
                    "No question with sequence number " + sequenceNo + " in this paper.");
        }
        return question;
    }

    private BlueprintSlot requireSlot(PaperReview review, int sequenceNo) {
        if (review == null) {
            throw new IllegalArgumentException("There is no paper to review.");
        }
        BlueprintSlot slot = review.getSlotBySequence(sequenceNo);
        if (slot == null) {
            throw new IllegalArgumentException(
                    "The blueprint has no slot with sequence number " + sequenceNo + ".");
        }
        return slot;
    }

    private void applyReplacement(PaperReview review, ReviewQuestion current,
                                  Question replacement) {
        ReviewQuestion updated = new ReviewQuestion(current);
        updated.setQuestionId(replacement.getQuestionId());
        updated.setSubjectId(replacement.getSubjectId());
        updated.setModuleId(replacement.getModuleId());
        updated.setModuleName(replacement.getModuleName() != null
                && !replacement.getModuleName().isEmpty()
                ? replacement.getModuleName() : current.getModuleName());
        updated.setQuestionText(replacement.getQuestionText());
        updated.setAnswerText(replacement.getAnswerText());
        updated.setMarks(replacement.getMarks());
        updated.setDifficulty(replacement.getDifficulty());
        review.setQuestion(current.getSequenceNo(), updated);
    }
}
