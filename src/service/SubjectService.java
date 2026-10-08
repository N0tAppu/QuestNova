package service;

import dao.SubjectDAO;
import dao.ModuleDAO;
import model.Subject;

import java.sql.SQLException;
import java.util.List;

/**
 * Business logic for subjects: validation, duplicate checks, safe deletion.
 */
public class SubjectService {

    private final SubjectDAO subjectDAO = new SubjectDAO();
    private final ModuleDAO moduleDAO = new ModuleDAO();

    public List<Subject> getAllSubjects() throws SQLException {
        return subjectDAO.getAllSubjects();
    }

    /**
     * Validates and adds a subject. Returns the new subject id.
     *
     * @throws IllegalArgumentException for invalid or duplicate input
     */
    public int addSubject(String subjectName) throws SQLException {
        String name = validateName(subjectName, "Subject");
        if (subjectDAO.existsByName(name)) {
            throw new IllegalArgumentException("A subject named \"" + name + "\" already exists.");
        }
        return subjectDAO.addSubject(name);
    }

    /**
     * Validates and updates a subject.
     *
     * @throws IllegalArgumentException for invalid or duplicate input
     */
    public void updateSubject(int subjectId, String subjectName) throws SQLException {
        String name = validateName(subjectName, "Subject");
        if (!subjectDAO.existsById(subjectId)) {
            throw new IllegalArgumentException("The selected subject no longer exists.");
        }
        if (subjectDAO.existsByName(name)) {
            Subject current = subjectDAO.getSubjectById(subjectId);
            if (current == null || !current.getSubjectName().equals(name)) {
                throw new IllegalArgumentException("A subject named \"" + name + "\" already exists.");
            }
            return;
        }
        subjectDAO.updateSubject(subjectId, name);
    }

    /**
     * Deletes a subject only when it has no modules (referential safety).
     *
     * @throws IllegalArgumentException if the subject still has modules
     */
    public void deleteSubject(int subjectId) throws SQLException {
        if (!subjectDAO.existsById(subjectId)) {
            throw new IllegalArgumentException("The selected subject no longer exists.");
        }
        if (moduleDAO.countModulesBySubject(subjectId) > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete this subject because it still has modules. "
                    + "Delete its modules first.");
        }
        subjectDAO.deleteSubject(subjectId);
    }

    private String validateName(String name, String label) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " name must not be empty.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException(label + " name must be 100 characters or fewer.");
        }
        return trimmed;
    }
}
