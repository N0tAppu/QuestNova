package model;

/**
 * Represents a module belonging to a subject (e.g. Module 1 - OOP).
 */
public class Module {

    private int moduleId;
    private int subjectId;
    private String moduleName;

    public Module() {
    }

    public Module(int moduleId, int subjectId, String moduleName) {
        this.moduleId = moduleId;
        this.subjectId = subjectId;
        this.moduleName = moduleName;
    }

    public int getModuleId() {
        return moduleId;
    }

    public void setModuleId(int moduleId) {
        this.moduleId = moduleId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public String toString() {
        return moduleName;
    }
}
