# AGENTS.md — Question Paper Generator

Tracking file for agent work. Update this after every meaningful change.

## 1. What this project is

Java Swing desktop application that generates exam question papers.
Not a web app, not NetBeans GUI Builder — all Swing code is hand-written
(no `.form` files exist anywhere in the repo).

- Entry point: `src/Main.java` -> `ui.MainFrame.launch()`
- Java: JDK 24 installed, sources compile as plain Java (design refs use
  Java 17 records/switch expressions)
- Look and Feel: FlatLaf 3.7.2 (`lib/flatlaf-3.7.2.jar`)
- DB: MySQL/MariaDB via JDBC (XAMPP), database `question_paper_db`,
  user `root`, empty password, `localhost:3306`
- Other libs in `lib/`: Apache POI 5.2.5 (Excel .xlsx),
  Apache PDFBox 3.0.3 (PDF export), MySQL Connector/J 8.4.0

## 2. Build and run (verified working)

There is no build.xml / Maven / Gradle. Compile with javac directly:

```
javac -encoding UTF-8 -d build -classpath "lib/*;build" <all src\**\*.java>
java  -classpath "build;lib/*" Main
```

Latest check: `javac` exit 0 on the full source tree. `ConnectionTest`
reports SUCCESS, so MySQL is reachable right now.

Run a test suite the same way, e.g.:
`java -classpath "build;lib/*" QuestionCrudTest`

## 3. Architecture

```
src/
  Main.java                  entry point
  database/
    DatabaseConnection.java  single source of JDBC connections
  dao/                       raw JDBC, no ORM
    SubjectDAO, ModuleDAO, QuestionDAO, PaperDAO
  service/                   all business logic lives here
    SubjectService, ModuleService, QuestionService,
    BlueprintService, BlueprintValidator, PatternPolicy,
    ExamDifficultyPolicy, PaperGenerator, PaperReviewService,
    PDFExportService, ExcelImportService
  model/                     POJOs
    Subject, Module, Question, QuestionPaper,
    ExamBlueprint, ExamPatternRow, BlueprintSlot, FeasibilityResult,
    GeneratedPaper, GeneratedQuestion, GenerationResult,
    PaperReview, ReviewQuestion
  ui/                        Swing presentation only (Quest redesign)
    MainFrame        AppShell sidebar host, refresh-on-enter, Ctrl+1..5
    Theme            design tokens + FlatLaf install (replaces UiTheme)
    Ui               reusable components: buttons, cards, tables, Meter
    AppShell         light sidebar (groups Content/Papers, badges, status)
    SubjectPanel     subjects + modules CRUD (master-detail)
    QuestionPanel    question bank: live filters, master-detail
    ImportPanel      3-step Excel import + collapsed destructive actions
    GeneratePaperPanel  blueprint editor (Meter, cards, pinned actions)
    GeneratedPapersPanel  finalized paper history (master-detail)
    ReviewPaperDialog / ReviewPaperPanel  post-generation review + PDF export
    QuestionDeleteConfirm  bulk-delete preview/confirm (JOptionPane-based)
  *.java (root)               19 test/smoke main() suites, run against live DB
```

Layering rule observed throughout: `ui -> service -> dao -> jdbc`.
UI never touches SQL directly. Keep it that way. New UI code reads
colours/spacing only from `Theme.*` / `Ui.*` — no raw colour literals.

### Screens in AppShell (registration order)

0. Subjects & modules  -> `subjectPanel.refreshIfChanged()`
1. Import questions    -> `importPanel.refreshPanel()`
2. Question bank       -> `questionPanel.refreshFilters()`
3. Blueprint           -> `generatePaperPanel.refreshPanel()`
4. Generated papers    -> `generatedPapersPanel.refreshPanel()`

Every panel exposes a public facade of small methods
(`isEditEnabled()`, `selectSubjectRow(int)`, `getQuestionRowCount()`,
...) consumed by the GUI test suites. Do not rename or remove those
facade methods without updating the tests.

## 4. Database

Schema: `database_schema.sql` (root of repo). Tables: `subjects`,
`modules`, `questions`, `question_papers`, plus paper question/slot/
blueprint tables. `ON DELETE CASCADE` is used for paper-owned rows;
`modules` uses `ON DELETE RESTRICT` against subject deletion.

Seed data reported by prior runs: 2 subjects (123 Object Oriented
Programming, 124 Data Structures), 10 modules, 500 questions.

**Never change the schema or seed data to make a UI task easier.**
Test suites create their own `TEST ...` fixtures and clean them up.

## 5. Git state

Branch: `fix/blueprint-ui-regressions` (checked out)
Remote: origin configured, nothing pushed.

```
main                    c2aa087  pre-redesign baseline (untouched)
backup/pre-ui-redesign  c2aa087  safety copy
ui/redesign             77f2b22  redesign head
backup/pre-blueprint-fix 77f2b22 recovery point
fix/blueprint-ui-regressions 6051d75  current work branch (HEAD)
```

Work already committed on this branch: pattern-selection repair,
accessibility/ergonomics pass, dialog safety defaults, review/history
polish, blueprint redesign, richer palette, and the acceptance docs.

### UNCOMMITTED changes (in the working tree right now)

The full `questDesign` Quest restyle (see section 7 item 1) — ALL of
`src/ui/` except service/dao/model code:

- NEW: `Theme.java`, `Ui.java`, `AppShell.java`
- REWRITTEN: `MainFrame.java` (AppShell host), `SubjectPanel`,
  `QuestionPanel`, `ImportPanel`, `GeneratePaperPanel`,
  `GeneratedPapersPanel`, `ReviewPaperPanel`
- DELETED: `UiTheme.java` (alias facade removed once last caller went;
  `MainFrame` now calls `Theme.install()`)
- `QuestionDeleteConfirm.java` and `ReviewPaperDialog.java` unchanged
  (JOptionPane-based / thin host — already theme-compliant)

All 18 test suites pass on this tree (679 assertions, 0 failed).
Verified: WCAG contrast 19/19 palette pairs >= 4.5:1; offscreen
screenshots of all 5 screens at 1000x640 and 1400x900 show no clipping
(question-bank/papers tables scroll horizontally at 1000px by design).
NOT yet committed — ask the user before committing.

## 6. Prior session deliverables (evidence in artifacts/)

- `artifacts/blueprint_ui_acceptance.md` — 56/56 acceptance items PASS
- `artifacts/blueprint_ui_report.md` — full A-M report: root-caused two
  real defects (pattern-row selection wipe; disabled buttons looking
  enabled), ran E2E harnesses (40/40, 81/81, 10/10), 18-suite regression
  661 passed / 0 failed, verified DB counts returned to baseline
  (2 subjects / 10 modules / 500 questions).

Temporary harnesses from that session (`ReproBlueprintFlow`,
`Stage11Show`, `Stage12E2E`, `Stage13DbDown`, `run-regress.ps1`,
`palette-candidate.ps1`) were NOT committed.

## 7. Outstanding / pending requests

1. **DONE (2026-10-10) — implemented the `questDesign` reference UI.**
   The real reference folder is `C:\Users\acer\Desktop\questDesign\`
   (the user's stated paths `conceptual/Quest design/` and
   `Desktop/Quest Nova/` do not exist). Work delivered:

   - `Theme` (palette/spacings + FlatLaf install), `Ui` (buttons, cards,
     tables, `Meter`, `pageHeader`), `AppShell` sidebar (Content/Papers
     groups, badges, Ctrl+1..5, status bar) — replaces `JTabbedPane`
   - All 7 screens restyled: master-detail subjects/question bank/papers,
     live filters (no Search button), 3-step import with collapsed
     destructive actions, blueprint Meter ("0 of 0 marks" + status hint),
     cards instead of `TitledBorder`, pinned action bars
   - Removed: `Validate Blueprint` button (+ dead `onValidate`),
     every `Derived Blueprint Preview` string, the `UiTheme` class
   - Done-when evidence: 18 suites 679/0 green; no forbidden strings
     left (`grep` clean); 19/19 contrast pairs pass 4.5:1; offscreen
     screenshots of all 5 screens at 1000x640 + 1400x900 show no
     clipping (dense tables scroll horizontally at 1000px by design)
   - Constraint honored: presentation only — no service/dao/model/schema
     changes, all facade methods + enablement rules intact
   - Note: the blueprint section body keeps an outer scroll pane (A–E
     cards + preview exceed any fixed viewport); the design's "remove
     the scroll pane" applied to its shorter 2-card layout. Header,
     meter, preview and actions stay pinned.

2. **RESOLVED (probably) — "the db is not connected, therefore the
   imported question bank is not showing up in the db."** At the time
   it was reported, `MainFrame.launch()` showed a one-time MySQL warning
   dialog. `ConnectionTest` now returns SUCCESS, so MySQL is up. If the
   user still reports missing imported questions, re-verify by
   re-importing and checking row counts, rather than assuming.

## 8. Housekeeping notes

- Stray untracked files at repo root, all safe to delete if the user
  agrees: `baseline_uiTheme.txt`, `build_errors.txt`, `diff_baseline.txt`,
  `errors.txt`, `stage11_output.txt`, `s10_export.png`, `srcfiles.txt`
- `build_errors.txt` is STALE: it references a sidebar-based MainFrame
  (`btnSubjects`, `javax.BoxLayout`) that does not exist in the current
  source. Ignore it.
- `%USERPROFILE%/` directory in the repo is an accident (a path-expansion
  bug); contains `Downloads/QuestNova_Verification/
  QuestNova_Acceptance_Questionnaire.md`. Untracked.
- `.gitignore` covers only `build/` and `*.class`. Consider extending it
  to `artifacts/` screenshots and the stray files above.
- Shell note: this machine runs PowerShell 5.1. `head`/`tail` are NOT
  available. Use `Select-Object -First/-Last`. Inside `powershell -Command
  "..."`, `$_` and `$var` get eaten by the outer shell — write a `.ps1`
  script file instead, or escape carefully.

## 9. Changelog

- 2026-10-10 (later) — Finished the questDesign implementation: restyled
  GeneratePaperPanel (Meter, pinned action bar, Validate button removed),
  ReviewPaperPanel, fixed table column collapse at 1000px (AUTO_RESIZE_OFF
  + min widths on QuestionPanel/GeneratedPapersPanel), deleted UiTheme,
  verified 18 suites 679/0, contrast 19/19 >= 4.5:1, and 10 offscreen
  screenshots (5 screens x 1000x640 + 1400x900) with no clipping.
  Everything uncommitted on `fix/blueprint-ui-regressions`.
- 2026-10-10 — Created this AGENTS.md. Surveyed full repo, verified
  green compile and live DB connection, recorded uncommitted gradient
  removal, located the real design reference folder
  (`Desktop\questDesign`), and parked the pending UI-implementation task.
