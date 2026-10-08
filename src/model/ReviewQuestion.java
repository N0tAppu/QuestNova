package model;

/**
 * One question inside a paper under review (Stage 9).
 *
 * Mutable on purpose: the faculty may edit the text for THIS paper and the
 * service may swap the question for another eligible one, until the paper is
 * finalized. sequenceNo is the blueprint position and never changes.
 */
public class ReviewQuestion {

    private int sequenceNo;
    private int questionId;
    private int subjectId;
    private int moduleId;
    private String moduleName;
    private String questionText;
    private String answerText;
    private int marks;
    private String difficulty; // Easy | Medium | Hard

    public ReviewQuestion() {
    }

    public ReviewQuestion(int sequenceNo, int questionId, int subjectId, int moduleId,
                          String moduleName, String questionText, String answerText,
                          int marks, String difficulty) {
        this.sequenceNo = sequenceNo;
        this.questionId = questionId;
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.questionText = questionText;
        this.answerText = answerText;
        this.marks = marks;
        this.difficulty = difficulty;
    }

    public ReviewQuestion(ReviewQuestion other) {
        this(other.sequenceNo, other.questionId, other.subjectId, other.moduleId,
                other.moduleName, other.questionText, other.answerText,
                other.marks, other.difficulty);
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public int getModuleId() {
        return moduleId;
    }

    public void setModuleId(int moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public int getMarks() {
        return marks;
    }

    public void setMarks(int marks) {
        this.marks = marks;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
}
