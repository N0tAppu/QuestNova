package service;

import dao.ModuleDAO;
import dao.SubjectDAO;
import model.Module;

import java.sql.SQLException;
import java.util.List;

/**
 * Business logic for modules: validation, duplicate checks, safe deletion.
 */
public class ModuleService {

    private final ModuleDAO moduleDAO = new ModuleDAO();
    private final SubjectDAO subjectDAO = new SubjectDAO();

    public List<Module> getModulesBySubject(int subjectId) throws SQLException {
        return moduleDAO.getModulesBySubject(subjectId);
    }

    /**
     * Validates and adds a module to a subject. Returns the new module id.
     *
     * @throws IllegalArgumentException for invalid or duplicate input
     */
    public int addModule(int subjectId, String moduleName) throws SQLException {
        String name = validateName(moduleName);
        if (!subjectDAO.existsById(subjectId)) {
            throw new IllegalArgumentException("The selected subject no longer exists.");
        }
        if (moduleDAO.existsByName(subjectId, name)) {
            throw new IllegalArgumentException(
                    "A module named \"" + name + "\" already exists for this subject.");
        }
        return moduleDAO.addModule(subjectId, name);
    }

    /**
     * Validates and updates a module name.
     *
     * @throws IllegalArgumentException for invalid or duplicate input
     */
    public void updateModule(int moduleId, int subjectId, String moduleName) throws SQLException {
        String name = validateName(moduleName);
        if (moduleDAO.existsByName(subjectId, name)) {
            List<Module> modules = moduleDAO.getModulesBySubject(subjectId);
            for (Module m : modules) {
                if (m.getModuleId() == moduleId && m.getModuleName().equals(name)) {
                    return;
                }
            }
            throw new IllegalArgumentException(
                    "A module named \"" + name + "\" already exists for this subject.");
        }
        moduleDAO.updateModule(moduleId, name);
    }

    /**
     * Deletes a module only when it has no questions (referential safety).
     *
     * @throws IllegalArgumentException if the module still has questions
     */
    public void deleteModule(int moduleId) throws SQLException {
        if (moduleDAO.countQuestionsInModule(moduleId) > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete this module because questions still reference it. "
                    + "Delete or move those questions first.");
        }
        moduleDAO.deleteModule(moduleId);
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name must not be empty.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Module name must be 100 characters or fewer.");
        }
        return trimmed;
    }
}
