package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a paper generation attempt (Stage 8).
 * Either a complete paper (success) or precise failure reasons - never a
 * partial paper.
 */
public class GenerationResult {

    private final boolean success;
    private final GeneratedPaper paper;
    private final List<String> failureReasons;

    private GenerationResult(boolean success, GeneratedPaper paper,
                             FeasibilityResult feasibility, List<String> failureReasons) {
        this.success = success;
        this.paper = paper;
        this.failureReasons = new ArrayList<>(failureReasons);
    }

    public static GenerationResult success(GeneratedPaper paper, FeasibilityResult feasibility) {
        return new GenerationResult(true, paper, feasibility, new ArrayList<>());
    }

    public static GenerationResult failure(List<String> reasons, FeasibilityResult feasibility) {
        return new GenerationResult(false, null, feasibility, reasons);
    }

    public boolean isSuccess() {
        return success;
    }

    /** Complete paper on success, always null on failure (no partial papers). */
    public GeneratedPaper getPaper() {
        return paper;
    }

    /** Failure reasons formatted for display - built by the service, never parsed by the UI. */
    public String getFailureMessage() {
        StringBuilder sb = new StringBuilder();
        for (String reason : failureReasons) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(reason);
        }
        return sb.toString();
    }
}
