package service;

import model.PaperReview;
import model.ReviewQuestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 10: PDF export of a finalized paper.
 *
 * Two separate documents are written from the SAME authoritative source -
 * the finalized snapshot stored in paper_questions:
 *   - the question paper (questions with their marks),
 *   - the answer key (snapshot answers with their marks).
 *
 * The question bank is never read here, so a later bank change can never
 * change an exported PDF. Only papers that exist as finalized rows can be
 * exported; a temporary, not yet finalized review has no paper id and is
 * therefore never exportable.
 *
 * Layout is driven by font metrics (PDFont.getStringWidth), never by
 * character counts: every question is wrapped to the text column, a whole
 * question moves to the next page when it does not fit, and only a question
 * that is taller than one page continues on the following page. Built-in
 * standard fonts only, so the result does not depend on the machine.
 */
public class PDFExportService {

    // =====================================================================
    // Layout - the single place where page geometry is defined
    // =====================================================================

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN_LEFT = 50f;
    private static final float MARGIN_RIGHT = 50f;
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 60f;
    private static final float FOOTER_BASELINE = 36f;

    private static final float TITLE_SIZE = 16f;
    private static final float SUBJECT_SIZE = 14f;
    private static final float BODY_SIZE = 11f;
    private static final float SMALL_SIZE = 10f;
    private static final float BODY_LEADING = 14f;

    private static final float NUMBER_COLUMN = 34f;
    private static final float MARKS_COLUMN = 54f;
    private static final float TEXT_COLUMN =
            PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - NUMBER_COLUMN - MARKS_COLUMN;
    private static final float TEXT_X = MARGIN_LEFT + NUMBER_COLUMN;
    private static final float RIGHT_EDGE = PAGE_WIDTH - MARGIN_RIGHT;

    private static final float TITLE_LEADING = 21f;
    private static final float SUBJECT_LEADING = 19f;
    private static final float HEADER_GAP = 12f;
    private static final float QUESTION_GAP = 9f;
    private static final float MARKS_LINE_GAP = 15f;

    private static final int MAX_FILE_NAME_PART = 40;
    private static final String UNSAFE_FILE_NAME_CHARS = "\\/:*?\"<>|";

    private final PaperReviewService reviewService = new PaperReviewService();

    // =====================================================================
    // Public API
    // =====================================================================

    /**
     * Writes the question paper PDF of a finalized paper.
     *
     * @throws IllegalArgumentException if the paper id or target is unusable,
     *                                  or the paper does not exist finalized
     * @throws SQLException if the paper cannot be loaded
     * @throws IOException if the PDF cannot be written
     */
    public File exportQuestionPaper(int paperId, File target)
            throws SQLException, IOException {
        requireTarget(target);
        return writePdf(loadFinalized(paperId), target, false);
    }

    /**
     * Writes the answer key PDF of a finalized paper - the answers exactly
     * as they were snapshotted at finalization.
     *
     * @throws IllegalArgumentException if the paper id or target is unusable,
     *                                  or the paper does not exist finalized
     * @throws SQLException if the paper cannot be loaded
     * @throws IOException if the PDF cannot be written
     */
    public File exportAnswerKey(int paperId, File target)
            throws SQLException, IOException {
        requireTarget(target);
        return writePdf(loadFinalized(paperId), target, true);
    }

    /**
     * The suggested save name: Question_Paper_&lt;id&gt;[_&lt;subject&gt;].pdf
     * or Answer_Key_&lt;id&gt;[_&lt;subject&gt;].pdf, with the subject made
     * safe for the file system (no path separators, quotes, wildcards,
     * control characters or trailing dots).
     */
    public static String buildDefaultFileName(int paperId, String subjectName,
                                              boolean answerKey) {
        String base = (answerKey ? "Answer_Key_" : "Question_Paper_") + paperId;
        String subject = safeFileNamePart(subjectName);
        return subject.isEmpty() ? base + ".pdf" : base + "_" + subject + ".pdf";
    }

    // =====================================================================
    // Loading - finalized rows only, snapshots only
    // =====================================================================

    private PaperReview loadFinalized(int paperId) throws SQLException {
        if (paperId <= 0) {
            throw new IllegalArgumentException("There is no finalized paper to export.");
        }
        PaperReview review = reviewService.loadPaper(paperId);
        if (review == null || !review.isFinalized() || review.getPaperId() <= 0) {
            throw new IllegalArgumentException(
                    "That finalized paper does not exist.");
        }
        if (review.getQuestions().isEmpty()) {
            throw new IllegalArgumentException(
                    "That finalized paper contains no questions.");
        }
        return review;
    }

    private static void requireTarget(File target) {
        if (target == null) {
            throw new IllegalArgumentException("There is no file to save the PDF to.");
        }
    }

    // =====================================================================
    // Writing
    // =====================================================================

    private File writePdf(PaperReview review, File target, boolean answerKey)
            throws IOException {
        boolean existedBefore = target.exists();
        boolean written = false;
        try (PDDocument document = new PDDocument()) {
            Canvas canvas = new Canvas(document);
            if (answerKey) {
                drawAnswerKey(canvas, review);
            } else {
                drawQuestionPaper(canvas, review);
            }
            canvas.finishPage();
            drawFooters(document, canvas.pages);
            document.save(target);
            written = true;
        } finally {
            if (!written && !existedBefore && target.isFile() && !target.isDirectory()) {
                target.delete();
            }
        }
        return target;
    }

    private void drawQuestionPaper(Canvas canvas, PaperReview review) throws IOException {
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        drawDocumentHeader(canvas, review, regular, bold, "QUESTION PAPER");

        canvas.drawText(MARGIN_LEFT, "No.", bold, SMALL_SIZE);
        canvas.drawText(TEXT_X, "Question", bold, SMALL_SIZE);
        canvas.drawTextRight(RIGHT_EDGE, "Marks", bold, SMALL_SIZE);
        canvas.advance(SUBJECT_LEADING);
        canvas.rule(MARGIN_LEFT, RIGHT_EDGE, canvas.y() + 4f);
        canvas.advance(HEADER_GAP);

        for (ReviewQuestion question : review.getQuestions()) {
            List<String> lines = wrap(regular, BODY_SIZE, TEXT_COLUMN,
                    sanitize(regular, question.getQuestionText()));
            if (!canvas.fits(blockHeight(lines)) && fitsOnFreshPage(lines)) {
                canvas.newPage();
            }
            boolean numberDrawn = false;
            int index = 0;
            while (index < lines.size()) {
                if (!canvas.fits(0f)) {
                    canvas.newPage();
                    continue;
                }
                if (!numberDrawn) {
                    canvas.drawText(MARGIN_LEFT, question.getSequenceNo() + ".",
                            bold, BODY_SIZE);
                    canvas.drawTextRight(RIGHT_EDGE,
                            question.getMarks() + " Marks", bold, BODY_SIZE);
                    numberDrawn = true;
                }
                canvas.drawText(TEXT_X, lines.get(index), regular, BODY_SIZE);
                canvas.advance(BODY_LEADING);
                index++;
            }
            canvas.advance(QUESTION_GAP);
        }
    }

    private void drawAnswerKey(Canvas canvas, PaperReview review) throws IOException {
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        drawDocumentHeader(canvas, review, regular, bold, "ANSWER KEY");
        canvas.rule(MARGIN_LEFT, RIGHT_EDGE, canvas.y() + 4f);
        canvas.advance(HEADER_GAP);

        for (ReviewQuestion question : review.getQuestions()) {
            List<String> lines = wrap(regular, BODY_SIZE, TEXT_COLUMN,
                    sanitize(regular, question.getAnswerText()));
            float needed = blockHeight(lines) + MARKS_LINE_GAP;
            if (!canvas.fits(needed) && fitsOnFreshPage(lines)) {
                canvas.newPage();
            }
            boolean numberDrawn = false;
            int index = 0;
            while (index < lines.size()) {
                if (!canvas.fits(0f)) {
                    canvas.newPage();
                    continue;
                }
                if (!numberDrawn) {
                    canvas.drawText(MARGIN_LEFT, question.getSequenceNo() + ".",
                            bold, BODY_SIZE);
                    numberDrawn = true;
                }
                canvas.drawText(TEXT_X, lines.get(index), regular, BODY_SIZE);
                canvas.advance(BODY_LEADING);
                index++;
            }
            if (!canvas.fits(0f)) {
                canvas.newPage();
            }
            canvas.drawText(TEXT_X, "Marks: " + question.getMarks(), regular, SMALL_SIZE);
            canvas.advance(MARKS_LINE_GAP);
            canvas.advance(QUESTION_GAP);
        }
    }

    /**
     * QUESTION PAPER / ANSWER KEY title, subject, exam type only when the
     * finalized data actually carries one, and the maximum marks.
     */
    private void drawDocumentHeader(Canvas canvas, PaperReview review, PDFont regular,
                                    PDFont bold, String title) throws IOException {
        canvas.drawTextCentered(title, bold, TITLE_SIZE);
        canvas.advance(TITLE_LEADING);
        canvas.drawTextCentered(sanitize(bold, review.getSubjectName()), bold,
                SUBJECT_SIZE);
        canvas.advance(SUBJECT_LEADING);
        String examType = review.getExamType();
        if (examType != null && !examType.trim().isEmpty()) {
            canvas.drawTextCentered(sanitize(regular, examType), regular, BODY_SIZE);
            canvas.advance(BODY_LEADING);
        }
        canvas.drawText(MARGIN_LEFT,
                "Maximum Marks: " + review.getTotalMarks(), bold, BODY_SIZE);
        canvas.advance(TITLE_LEADING);
    }

    private void drawFooters(PDDocument document, List<PDPage> pages)
            throws IOException {
        if (pages.isEmpty()) {
            return;
        }
        PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (int i = 0; i < pages.size(); i++) {
            String text = "Page " + (i + 1) + " of " + pages.size();
            float width = regular.getStringWidth(text) / 1000f * SMALL_SIZE;
            try (PDPageContentStream footer = new PDPageContentStream(document,
                    pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                footer.beginText();
                footer.setFont(regular, SMALL_SIZE);
                footer.newLineAtOffset((PAGE_WIDTH - width) / 2f, FOOTER_BASELINE);
                footer.showText(text);
                footer.endText();
            }
        }
    }

    // =====================================================================
    // Text helpers
    // =====================================================================

    /** Vertical space a wrapped block occupies between first and last line. */
    private static float blockHeight(List<String> lines) {
        return Math.max(0, lines.size() - 1) * BODY_LEADING;
    }

    /** Could this block be drawn completely on an empty page? */
    private static boolean fitsOnFreshPage(List<String> lines) {
        return blockHeight(lines)
                <= PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM - MARKS_LINE_GAP;
    }

    /**
     * Greedy word wrap measured with the real font width - never with
     * character counts. A single word longer than the column is split at
     * the last character that still fits.
     */
    private static List<String> wrap(PDFont font, float size, float maxWidth,
                                     String text) throws IOException {
        List<String> lines = new ArrayList<>();
        String content = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (content.isEmpty()) {
            lines.add("");
            return lines;
        }
        String line = "";
        for (String word : content.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (widthOf(font, size, candidate) <= maxWidth) {
                line = candidate;
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line);
                line = "";
            }
            if (widthOf(font, size, word) <= maxWidth) {
                line = word;
                continue;
            }
            String part = "";
            for (int i = 0; i < word.length(); i++) {
                String next = part + word.charAt(i);
                if (!part.isEmpty() && widthOf(font, size, next) > maxWidth) {
                    lines.add(part);
                    part = "";
                }
                part = next;
            }
            line = part;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static float widthOf(PDFont font, float size, String text)
            throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /**
     * WinAnsi (the encoding of the built-in fonts) cannot represent every
     * character - for example the rupee sign. Anything the font cannot
     * encode is replaced by "?" so that drawing never fails.
     */
    private static String sanitize(PDFont font, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                safe.append(' ');
            } else if (c < 128) {
                safe.append(c);
            } else {
                try {
                    font.getStringWidth(String.valueOf(c));
                    safe.append(c);
                } catch (Exception ex) {
                    safe.append('?');
                }
            }
        }
        return safe.toString();
    }

    /** Makes a name part safe to use inside a file name. */
    private static String safeFileNamePart(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        boolean pendingSeparator = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean usable = c > 31 && c != 127
                    && UNSAFE_FILE_NAME_CHARS.indexOf(c) < 0
                    && c != ' ' && c != '\t';
            if (!usable) {
                if (safe.length() > 0) {
                    pendingSeparator = true;
                }
                continue;
            }
            if (pendingSeparator) {
                safe.append('_');
                pendingSeparator = false;
            }
            safe.append(c);
        }
        String part = safe.toString();
        int end = part.length();
        while (end > 0 && (part.charAt(end - 1) == '_'
                || part.charAt(end - 1) == '.' || part.charAt(end - 1) == ' ')) {
            end--;
        }
        part = part.substring(0, end);
        if (part.length() > MAX_FILE_NAME_PART) {
            part = part.substring(0, MAX_FILE_NAME_PART);
            end = part.length();
            while (end > 0 && (part.charAt(end - 1) == '_'
                    || part.charAt(end - 1) == '.')) {
                end--;
            }
            part = part.substring(0, end);
        }
        return part;
    }

    // =====================================================================
    // Canvas - one A4 page at a time, cursor from top to bottom
    // =====================================================================

    private static final class Canvas {

        private final PDDocument document;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream stream;
        /** Baseline of the next line to draw. */
        private float y;

        Canvas(PDDocument document) throws IOException {
            this.document = document;
            addPage();
        }

        float y() {
            return y;
        }

        void advance(float amount) {
            y -= amount;
        }

        /** Is a line (or block end) at the current baseline still inside the text area? */
        boolean fits(float needed) {
            return y - needed >= MARGIN_BOTTOM;
        }

        void newPage() throws IOException {
            addPage();
        }

        void finishPage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        void drawText(float x, String text, PDFont font, float size)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        }

        void drawTextCentered(String text, PDFont font, float size)
                throws IOException {
            float width = font.getStringWidth(text) / 1000f * size;
            drawText((PAGE_WIDTH - width) / 2f, text, font, size);
        }

        void drawTextRight(float right, String text, PDFont font, float size)
                throws IOException {
            float width = font.getStringWidth(text) / 1000f * size;
            drawText(right - width, text, font, size);
        }

        void rule(float fromX, float toX, float lineY) throws IOException {
            stream.setStrokingColor(0.55f);
            stream.setLineWidth(0.8f);
            stream.moveTo(fromX, lineY);
            stream.lineTo(toX, lineY);
            stream.stroke();
            stream.setStrokingColor(0f);
        }

        private void addPage() throws IOException {
            finishPage();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN_TOP;
        }
    }
}
