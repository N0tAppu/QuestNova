package model;

import java.sql.Timestamp;

/**
 * One finalized question paper in the history list (question_papers table
 * shape, joined with the subject name for display).
 */
public class QuestionPaper {

    private int paperId;
    private int subjectId;
    private String subjectName;
    private int totalMarks;
    private Timestamp generatedAt;

    public QuestionPaper() {
    }

    public QuestionPaper(int paperId, int subjectId, String subjectName,
                         int totalMarks, Timestamp generatedAt) {
        this.paperId = paperId;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.totalMarks = totalMarks;
        this.generatedAt = generatedAt;
    }

    public int getPaperId() {
        return paperId;
    }

    public void setPaperId(int paperId) {
        this.paperId = paperId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(int totalMarks) {
        this.totalMarks = totalMarks;
    }

    public Timestamp getGeneratedAt() {
        return generatedAt;
    }
}
