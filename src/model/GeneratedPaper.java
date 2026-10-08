package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A complete in-memory generated question paper (Stage 8).
 * Questions are in exact blueprint sequence order.
 * Not persisted directly - a finalized paper keeps its snapshots in paper_questions (Stage 9).
 */
public class GeneratedPaper {

    private final int subjectId;
    private final String subjectName;
    private final String examType;
    private final int totalMarks;
    private final List<GeneratedQuestion> questions;

    public GeneratedPaper(int subjectId, String subjectName, String examType,
                          int totalMarks, List<GeneratedQuestion> questions) {
        this.subjectId = subjectId;
        this.subjectName = subjectName == null ? "" : subjectName;
        this.examType = examType == null ? "" : examType;
        this.totalMarks = totalMarks;
        this.questions = new ArrayList<>(questions);
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

    public List<GeneratedQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getQuestionCount() {
        return questions.size();
    }
}
