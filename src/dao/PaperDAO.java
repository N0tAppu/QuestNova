package dao;

import database.DatabaseConnection;
import model.BlueprintSlot;
import model.PaperReview;
import model.QuestionPaper;
import model.ReviewQuestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access for the finalized-paper tables (question_papers,
 * paper_questions, paper_blueprints, paper_blueprint_slots).
 * All SQL lives here (PreparedStatement).
 *
 * Finalization is ONE transaction: either the paper, every question
 * snapshot, its blueprint and all slots are stored, or nothing is.
 * Once stored, a paper is immutable: there is no update path here at all.
 */
public class PaperDAO {

    private static final String INSERT_PAPER =
            "INSERT INTO question_papers (subject_id, total_marks) VALUES (?, ?)";
    private static final String INSERT_QUESTION =
            "INSERT INTO paper_questions (paper_id, question_id, question_text, "
                    + "answer_text, marks, difficulty, sequence_no) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_BLUEPRINT =
            "INSERT INTO paper_blueprints (paper_id, subject_id) VALUES (?, ?)";
    private static final String INSERT_SLOT =
            "INSERT INTO paper_blueprint_slots (blueprint_id, sequence_no, module_id, "
                    + "marks, difficulty) VALUES (?, ?, ?, ?, ?)";

    /**
     * Persists a finalized review in a single transaction.
     *
     * @return the generated paper id
     * @throws SQLException on any failure - the transaction is rolled back
     *                      and no row of this paper remains
     */
    public int saveFinalizedPaper(PaperReview review) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int paperId = insertPaper(conn, review);
                insertQuestions(conn, paperId, review.getQuestions());
                int blueprintId = insertBlueprint(conn, paperId, review.getSubjectId());
                insertSlots(conn, blueprintId, review.getSlots());
                conn.commit();
                return paperId;
            } catch (SQLException | RuntimeException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        }
    }

    /**
     * The finalized-paper history, newest first (subject name joined for display).
     */
    public List<QuestionPaper> getPapers() throws SQLException {
        String sql = "SELECT p.paper_id, p.subject_id, s.subject_name, p.total_marks, "
                + "p.generated_at FROM question_papers p "
                + "JOIN subjects s ON p.subject_id = s.subject_id "
                + "ORDER BY p.generated_at DESC, p.paper_id DESC";
        List<QuestionPaper> papers = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                papers.add(new QuestionPaper(
                        rs.getInt("paper_id"),
                        rs.getInt("subject_id"),
                        rs.getString("subject_name"),
                        rs.getInt("total_marks"),
                        rs.getTimestamp("generated_at")));
            }
        }
        return papers;
    }

    /**
     * Rebuilds a finalized paper as a read-only review: question snapshots
     * are the authoritative text (the bank is never consulted), zipped with
     * the stored blueprint slots by sequence number.
     *
     * @return the review, or null when the paper does not exist
     */
    public PaperReview loadPaper(int paperId) throws SQLException {
        String headerSql = "SELECT p.subject_id, s.subject_name, p.total_marks "
                + "FROM question_papers p JOIN subjects s ON p.subject_id = s.subject_id "
                + "WHERE p.paper_id = ?";
        int subjectId;
        String subjectName;
        int totalMarks;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(headerSql)) {
            ps.setInt(1, paperId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                subjectId = rs.getInt("subject_id");
                subjectName = rs.getString("subject_name");
                totalMarks = rs.getInt("total_marks");
            }
        }

        List<BlueprintSlot> slots = loadSlots(paperId);
        Map<Integer, BlueprintSlot> slotBySequence = new LinkedHashMap<>();
        for (BlueprintSlot slot : slots) {
            slotBySequence.put(slot.getSequenceNo(), slot);
        }

        List<ReviewQuestion> questions = new ArrayList<>();
        String questionSql = "SELECT question_id, question_text, answer_text, marks, "
                + "difficulty, sequence_no FROM paper_questions WHERE paper_id = ? "
                + "ORDER BY sequence_no";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(questionSql)) {
            ps.setInt(1, paperId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sequenceNo = rs.getInt("sequence_no");
                    BlueprintSlot slot = slotBySequence.get(sequenceNo);
                    questions.add(new ReviewQuestion(
                            sequenceNo,
                            rs.getInt("question_id"),
                            subjectId,
                            slot != null ? slot.getModuleId() : -1,
                            slot != null ? slot.getModuleName() : "",
                            rs.getString("question_text"),
                            rs.getString("answer_text"),
                            rs.getInt("marks"),
                            rs.getString("difficulty")));
                }
            }
        }

        // The exam type is not part of the frozen schema, so a reloaded
        // paper carries none.
        PaperReview review = new PaperReview(subjectId, subjectName, "", totalMarks,
                slots, questions);
        review.markFinalized(paperId);
        return review;
    }

    /** Deletes a finalized paper; its snapshots, blueprint and slots cascade. */
    public void deletePaper(int paperId) throws SQLException {
        String sql = "DELETE FROM question_papers WHERE paper_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperId);
            ps.executeUpdate();
        }
    }

    /**
     * How many question snapshots a finalized paper stored. Used to prove
     * that deleting a paper removes its snapshots and nothing else.
     */
    public int countPaperQuestions(int paperId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM paper_questions WHERE paper_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Finds every finalized paper that pins at least one of the given
     * questions (via paper_questions). Only ids that are actually used
     * appear in the result; the papers come ordered by paper id.
     */
    public Map<Integer, List<QuestionPaper>> findPapersUsingQuestions(
            List<Integer> questionIds) throws SQLException {
        Map<Integer, List<QuestionPaper>> usage = new LinkedHashMap<>();
        if (questionIds == null || questionIds.isEmpty()) {
            return usage;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT pq.question_id, p.paper_id, p.subject_id, s.subject_name, "
                        + "p.total_marks, p.generated_at "
                        + "FROM paper_questions pq "
                        + "JOIN question_papers p ON pq.paper_id = p.paper_id "
                        + "JOIN subjects s ON p.subject_id = s.subject_id "
                        + "WHERE pq.question_id IN (");
        for (int i = 0; i < questionIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") ORDER BY pq.question_id, p.paper_id");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < questionIds.size(); i++) {
                ps.setInt(i + 1, questionIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int questionId = rs.getInt("question_id");
                    QuestionPaper paper = new QuestionPaper(
                            rs.getInt("paper_id"),
                            rs.getInt("subject_id"),
                            rs.getString("subject_name"),
                            rs.getInt("total_marks"),
                            rs.getTimestamp("generated_at"));
                    List<QuestionPaper> papers = usage.get(questionId);
                    if (papers == null) {
                        papers = new ArrayList<>();
                        usage.put(questionId, papers);
                    }
                    papers.add(paper);
                }
            }
        }
        return usage;
    }

    // =====================================================================
    // Transaction helpers
    // =====================================================================

    private int insertPaper(Connection conn, PaperReview review) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                INSERT_PAPER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, review.getSubjectId());
            ps.setInt(2, review.getTotalMarks());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Could not retrieve the new paper id.");
    }

    private void insertQuestions(Connection conn, int paperId, List<ReviewQuestion> questions)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_QUESTION)) {
            for (ReviewQuestion q : questions) {
                ps.setInt(1, paperId);
                ps.setInt(2, q.getQuestionId());
                ps.setString(3, q.getQuestionText());
                ps.setString(4, q.getAnswerText());
                ps.setInt(5, q.getMarks());
                ps.setString(6, q.getDifficulty());
                ps.setInt(7, q.getSequenceNo());
                ps.executeUpdate();
            }
        }
    }

    private int insertBlueprint(Connection conn, int paperId, int subjectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                INSERT_BLUEPRINT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, paperId);
            ps.setInt(2, subjectId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Could not retrieve the new blueprint id.");
    }

    private void insertSlots(Connection conn, int blueprintId, List<BlueprintSlot> slots)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SLOT)) {
            for (BlueprintSlot slot : slots) {
                ps.setInt(1, blueprintId);
                ps.setInt(2, slot.getSequenceNo());
                ps.setInt(3, slot.getModuleId());
                ps.setInt(4, slot.getMarks());
                ps.setString(5, slot.getDifficulty());
                ps.executeUpdate();
            }
        }
    }

    private List<BlueprintSlot> loadSlots(int paperId) throws SQLException {
        List<BlueprintSlot> slots = new ArrayList<>();
        String sql = "SELECT s.sequence_no, s.module_id, s.marks, s.difficulty, m.module_name "
                + "FROM paper_blueprint_slots s "
                + "JOIN paper_blueprints b ON s.blueprint_id = b.blueprint_id "
                + "LEFT JOIN modules m ON s.module_id = m.module_id "
                + "WHERE b.paper_id = ? ORDER BY s.sequence_no";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paperId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BlueprintSlot slot = new BlueprintSlot(
                            rs.getInt("sequence_no"),
                            rs.getInt("module_id"),
                            rs.getInt("marks"),
                            rs.getString("difficulty"));
                    slot.setModuleName(rs.getString("module_name"));
                    slots.add(slot);
                }
            }
        }
        return slots;
    }
}
