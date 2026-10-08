package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A generated question paper opened for review (Stage 9).
 *
 * Holds the blueprint slots it was built from, one editable ReviewQuestion
 * per slot, and - once finalized - the id of the persisted paper.
 * Before finalization everything is in-memory only: editing, replacing and
 * regenerating never touch the question bank.
 */
public class PaperReview {

    private final int subjectId;
    private final String subjectName;
    private final String examType;
    private final int totalMarks;
    private final List<BlueprintSlot> slots;
    private final List<ReviewQuestion> questions;

    private int paperId = -1;
    private boolean finalized;

    public PaperReview(int subjectId, String subjectName, String examType, int totalMarks,
                       List<BlueprintSlot> slots, List<ReviewQuestion> questions) {
        this.subjectId = subjectId;
        this.subjectName = subjectName == null ? "" : subjectName;
        this.examType = examType == null ? "" : examType;
        this.totalMarks = totalMarks;
        this.slots = new ArrayList<>(slots);
        this.questions = new ArrayList<>(questions);
    }

    /** Opens a review over a freshly generated paper (deep copy of both lists). */
    public static PaperReview from(GeneratedPaper paper, List<BlueprintSlot> slots) {
        if (paper == null) {
            throw new IllegalArgumentException("There is no generated paper to review.");
        }
        List<ReviewQuestion> questions = new ArrayList<>();
        for (GeneratedQuestion q : paper.getQuestions()) {
            questions.add(new ReviewQuestion(
                    q.getSequenceNo(), q.getQuestionId(), q.getSubjectId(),
                    q.getModuleId(), q.getModuleName(), q.getQuestionText(),
                    q.getAnswerText(), q.getMarks(), q.getDifficulty()));
        }
        return new PaperReview(paper.getSubjectId(), paper.getSubjectName(),
                paper.getExamType(), paper.getTotalMarks(), slots, questions);
    }

    public int getSubjectId() {
        return subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getExamType() {
        return examType;
    }

    public int getTotalMarks() {
        return totalMarks;
    }

    public List<BlueprintSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public List<ReviewQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    /** The question at a blueprint position, or null when there is none. */
    public ReviewQuestion getQuestionBySequence(int sequenceNo) {
        for (ReviewQuestion q : questions) {
            if (q.getSequenceNo() == sequenceNo) {
                return q;
            }
        }
        return null;
    }

    /** The blueprint slot at a sequence number, or null when there is none. */
    public BlueprintSlot getSlotBySequence(int sequenceNo) {
        for (BlueprintSlot slot : slots) {
            if (slot.getSequenceNo() == sequenceNo) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Replaces the question at a blueprint position (sequence number is
     * carried over). Used by PaperReviewService while the review is open.
     *
     * @throws IllegalArgumentException if there is no question at that position
     */
    public void setQuestion(int sequenceNo, ReviewQuestion question) {
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getSequenceNo() == sequenceNo) {
                questions.set(i, question);
                return;
            }
        }
        throw new IllegalArgumentException(
                "No question with sequence number " + sequenceNo + " in this paper.");
    }

    /** The persisted paper id, or -1 while the review is still open. */
    public int getPaperId() {
        return paperId;
    }

    public boolean isFinalized() {
        return finalized;
    }

    /**
     * Marks this review as a persisted, immutable paper.
     * Called by PaperReviewService only after the transactional save succeeds.
     */
    public void markFinalized(int paperId) {
        this.paperId = paperId;
        this.finalized = true;
    }
}
