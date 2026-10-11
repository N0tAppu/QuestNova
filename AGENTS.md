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

The app icon lives at `resources/Orbiting Quiz Sheet Icon.png` and is
loaded from the classpath (`/resources/...`) by `ui.Branding`; when
compiling, copy `resources/` into `build/resources/` so the classpath
lookup finds it. `Branding.source()` reports which loader path won.

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
    Branding         window/sidebar icon loader (classpath-first) + title
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

Branch: `main` @ `cacc2b4` (merge of the GitHub initial commit).
Remote: origin configured, nothing pushed from this session.

```
main                    cacc2b4  current HEAD (redesign merged)
ba69409                 ui: quest design restyle (AppShell, Theme/Ui, cards)
6051d75                 docs: final A-M regression repair report
```

Earlier pre-redesign history (`c2aa087`, `77f2b22`, `fix/blueprint-ui-regressions`)
is already merged; those branch names no longer need checking out.

### UNCOMMITTED changes (in the working tree right now)

The Quest Nova branding + palette pass PLUS the dark-mode + accent
system (section 7 items 2 and 4), presentation only:

- MODIFIED: `src/ui/Theme.java` (mutable appearance x accent tokens,
  prefs persistence, FlatLaf re-setup), `Ui.java` (qn.* registry,
  retheme walker, cell renderer with zebra + hover, viewport helper,
  btnStyle kinds), `AppShell.java` (sidebar footer: Light/Dark toggle
  + accent switcher, refreshTheme), `MainFrame.java` (theme listener
  wiring, current-screen tracking), `GeneratePaperPanel.java` (JSplitPane
  layout + collapsible Paper preview card), `GeneratedPapersPanel.java`,
  `QuestionPanel.java`, `ImportPanel.java`, `ReviewPaperPanel.java`,
  `SubjectPanel.java` (eager setForeground/setBackground -> Ui.fg/bg)
- NEW: `src/ui/Branding.java` (class-path icon loader with `source()`)
- NEW: `src/ThemeSystemTest.java` (358 checks: token completeness,
  6-combo contrast, prefs round-trip + invalid fallback, zebra/selection/
  hover precedence, form preservation, accent/appearance independence)
- NEW: `resources/Orbiting Quiz Sheet Icon.png` (shipped icon asset)

Full verification of this tree: `run-regress.ps1 -Tag darkmode2`
**1019 passed / 0 failed / REGRESSION_GREEN** (19 suites including the
new ThemeSystemTest); `javac` exit 0; 20 offscreen screenshots
inspected (dark-plum + light-green, 5 screens x 1000x640 + 1400x900);
live app launched. NOT committed — ask the user before committing.

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
   - Done-when evidence: 18 suites green (the "679" figure quoted here
     was wrong; the recorded baseline is 661 — see section 5); no
     forbidden strings
     left (`grep` clean); 19/19 contrast pairs pass 4.5:1; offscreen
     screenshots of all 5 screens at 1000x640 + 1400x900 show no
     clipping (dense tables scroll horizontally at 1000px by design)
   - Constraint honored: presentation only — no service/dao/model/schema
     changes, all facade methods + enablement rules intact
   - Note: the blueprint section body keeps an outer scroll pane (A–E
     cards + preview exceed any fixed viewport); the design's "remove
     the scroll pane" applied to its shorter 2-card layout. Header,
     meter, preview and actions stay pinned.

2. **DONE (2026-10-10 later) — Quest Nova branding + global palette
   refinement** (the `/nova` run; presentation only):

   - Rebranded: window title `Quest Nova — Question Paper Generator`,
     sidebar brand block (shipped icon + "Quest Nova"), dialog text.
     Icon ships in `resources/`, loads from the build classpath via
     `ui.Branding` (verified `SOURCE=classpath`, not the file fallback).
   - Palette: `Theme` remapped to the cool surface-hierarchy scheme
     (BG `#F1F5F8`, SIDE `#E8EEF3`, SURFACE `#FFFFFF`, HEAD_BG
     `#E1EAF0`, ACCENT `#0F766E`, WARN darkened to `#855C11`, new
     `GRID`/`SIDE`/`ACCENT_P` tokens). Contrast audit: 52/52 measured
     pairs pass, 7 documented exceptions (decorative separators, state
     tints paired with the accent bar, etc.).
   - Non-color selection indicator: 3px accent left-bar on the first
     cell of selected rows (`Ui.cell`).
   - Two real defects found during screenshot QA and fixed:
     (a) `GeneratedPapersPanel` detail rows overlapped because
     `GridBagConstraints.gridy` defaults to RELATIVE (-1) and the grid
     never pinned row 0 — now `g.gridy = 0` + a value-column gap;
     (b) card headers painted the title over the actions at ~1000px —
     `Ui.card` title moved WEST->CENTER and `Ui.bold` now returns an
     ellipsizing `TruncLabel`.
   - Verified: 661/0 REGRESSION_GREEN, 10 screenshots re-inspected,
     live launch shows title/icon/brand correctly.

3. **RESOLVED (probably) — "the db is not connected, therefore the
   imported question bank is not showing up in the db."** At the time
   it was reported, `MainFrame.launch()` showed a one-time MySQL warning
   dialog. `ConnectionTest` now returns SUCCESS, so MySQL is up. If the
   user still reports missing imported questions, re-verify by
   re-importing and checking row counts, rather than assuming.

4. **DONE (2026-10-10 latest) — Dark mode + accent schemes + sidebar
   toggle + blueprint layout fix** (the `/nova` run; presentation only):

   - Two independent axes in `Theme`: `Appearance{LIGHT,DARK}` ×
     `Accent{GREEN,GRAPHITE,PLUM}`; `Theme.set(a, c)` reassigns every
     token, re-runs FlatLaf setup, refreshes open windows and notifies
     an apply listener. Static initialiser seeds LIGHT/GREEN so early
     reads never see null colours (fixes a smoke-suite NPE regression).
   - Dark tokens per spec: BG `#1D232B`, SIDE `#0E141C` (darkened from
     the stated `#171F29` — it failed the sidebar-edge contrast check at
     1.05:1; now 1.17:1), SURFACE `#252D37`, INPUT `#1B222A`, INK
     `#F1F4F8`, MUTED `#B0BAC6`, LINE `#36414E`, plus OK/WARN/BAD
     `#58C99A`/`#F0BF69`/`#FF8585`. Light SIDE likewise darkened to
     `#DCE5EE` (1.16:1 vs BG).
   - Accents: Green `#42B883`/`#0F766E`, Graphite `#A3AFBF`/`#475467`,
     Plum `#B99AE8`/`#6F4E9E` (dark/light); ACCENT_FG near-black on
     dark fills for button-text contrast.
   - Persistence: `java.util.prefs` node `/quest-nova`, keys
     `appearance`/`accent`; invalid values fall back to LIGHT/GREEN;
     no DB involvement.
   - Sidebar footer (bottom-left, below nav): Light/Dark toggle +
     accent cycler ("green accent" -> "graphite" -> "plum"). Accessible
     names + tooltips; `AppShell.refreshTheme()` re-applies borders and
     footer labels after a switch.
   - `Ui.fg`/`Ui.bg`/`Ui.viewport`/`Ui.applyButtonStyle` register
     `qn.*` client properties; `Ui.retheme` walks the tree on theme
     change. All eager `setForeground(Theme.*)` sites across 6 panels
     converted. Renderers already read Theme at paint time.
   - Zebra: renderer precedence selection(TINT) -> hover(HOVER) ->
     ROW_A/ROW_B; hover tracked via `qn.hoverRow`.
   - Blueprint: replaced the mid/south BorderLayout with a vertical
     `JSplitPane` (resizeWeight 0.62, continuous layout) and a
     collapsible "Paper preview" card (Collapse/Expand button in the
     card header); divider initialised after first layout. Values in
     the A-E form are untouched by resizes or collapse.
   - New suite `ThemeSystemTest` (358 checks) registered in
     `run-regress.ps1`; final run: **1019 passed / 0 failed across 19
     suites**; 20 screenshots inspected (dark-plum + light-green,
     5 screens × 1000x640 + 1400x900); live app launched.
   - Deviations from spec: no "System Default" appearance option (the
     toggle is binary per the mandatory-placement wording); accent
     switcher is a button (cycle) rather than a picker menu; dark
     SIDE darkened for contrast as noted above.

5. **DONE (2026-10-11) — Light Mode colour correction** (Dark Mode
   frozen; presentation only):

   - Root cause of "components still show Dark colours after switch":
     FlatLaf UI delegates cache `background` at install time;
     `updateComponentTreeUI` does NOT re-read UIManager for
     already-constructed components (proved by `ThemeSwitchProbe`:
     UIManager values update correctly but component fields keep the
     old dark colour).
   - Fix: `Ui.rethemeOne()` fallback — for unregistered components of
     known types (JPanel, JComboBox, JSpinner, JTextField/TextArea/
     FormattedTextField, JCheckBox, JRadioButton, JList, JTableHeader,
     JToolTip) reset background/foreground from live UIManager values.
     Guards skip `qn.bg`/`qn.card`/`qn.btn`/`qn.table` owners. Not
     gated on `isOpaque()` (FlatLaf inputs report false but carry a
     stale field). Plain JScrollPane viewports (no `qn.viewport`) get
     `List.background`/`Panel.background` reset too.
   - Light tokens (reference palette, CTRL_LINE corrected for 3:1):
     BG `#F7F8FA`, SIDE `#E1E7F0` (darkened from reference `#E8EDF2`
     to clear the 1.15:1 sidebar-edge check; now 1.17:1), SURFACE/
     INPUT white, INK `#202833`, MUTED `#526071`, LINE `#D6DDE5`
     (decorative), CTRL_LINE `#8A94A6` (interactive borders, ~3.1:1
     on white), HEAD_BG `#E8EDF2`, ROW_A white, ROW_B `#F3F6F9`,
     GRID `#D6DDE5`, HOVER `#E8EDF3`, TRACK `#E8EDF2`, OK `#18794E`,
     WARN `#946200`, BAD `#B42318`, TINT_IDLE `#E8EDF2`, DISABLED_FG
     `#687586`, FOCUS `#2563EB`. Dark tokens untouched.
   - `applyDefaults()` expanded with verified FlatLaf 3.7.2 keys
     (checked against jar properties): ToolTip, PopupMenu, CheckBox,
     RadioButton, Spinner (+editor), FormattedTextField, ComboBox
     buttonBackground, ScrollBar thumb/track, Component/Button
     disabled backgrounds+foregrounds, Panel/TextField/ComboBox/
     Spinner foregrounds.
   - Screenshot QA found and fixed two additional stale-colour
     defects: (a) `Ui.pageHeader` title was a plain JLabel with eager
     `setForeground(Theme.INK)` and no `qn.fg` registration — now
     uses `fg(t, "INK")`; (b) blueprint syllabus `JList`s inside plain
     `JScrollPane`s kept dark backgrounds — fixed by the JList +
     viewport fallback above.
   - Verified: `ThemeSwitchProbe` stale_count=0 across repeated
     Dark→Light→Dark→Light transitions (disabled controls, dropdowns,
     tables, already-constructed components); `ThemeSystemTest` 358/0;
     full regression **1019 passed / 0 failed across 19 suites**;
     31 screenshots inspected (light-green + dark-green + live
     dark→light switch shots, 5 screens × 1000x640 + 1400x900);
     live app launched (PID 23456, stayed up, stopped cleanly).
   - Not manually verified in the live app (offscreen harness only):
     clicking the sidebar Light/Dark toggle by hand, prefs persistence
     across a real restart, and end-to-end Excel import / PDF export
     click-throughs (covered by the 19-suite regression instead).

6. **DONE (2026-10-11) — Auto-create missing modules on Excel import**
   (targeted enhancement; no schema/UI redesign):

   - `ExcelImportService.validate()` now discovers distinct module
     names in the file, classifies them as existing (reused) or new
     (to be created), and tracks per-module valid-question counts.
     Blank modules and names >100 chars remain row errors. Unknown
     module names are NO LONGER row errors — they become new modules.
   - `ImportReport` extended with `getDistinctModules()`,
     `getExistingModules()`, `getNewModules()`,
     `getModuleQuestionCounts()`, `getInvalidModuleValues()`,
     `getModulesCreated()`, `getModulesReusedCount()`.
     `summaryText()` now includes a module section with names + counts.
   - `importValidRows()` creates new modules via `ModuleService.addModule`
     (validation + duplicate safety), resolves moduleId on questions,
     inserts (existing partial-import policy preserved), then deletes
     any newly created module that ended up with zero imported questions
     (orphan cleanup). Concurrent-create race handled by lookup fallback.
   - `ImportPanel` preview shows module discovery summary
     ("Modules: N found (E existing, N new: names...)").
     Completion dialog shows modules created/reused counts.
   - Validation writes NOTHING to the DB; only `importValidRows` does.
   - ExcelImportTest extended with 12 module-creation scenarios
     (M1-M12): all-existing, all-new, mixed, repeated names,
     cross-subject scoping, whitespace, blank/long cells, cancel,
     orphan cleanup, partial import, re-import, subject switch.
     Existing T2-6/T9/T10 assertions updated (nonexistent-module
     rows are now valid with auto-created modules).
   - Verified: build exit 0, ExcelImportTest **74/0**, full regression
     **1056 passed / 0 failed across 19 suites**.

## 8. Housekeeping notes

- Stray untracked files at repo root, all safe to delete if the user
  agrees: `baseline_uiTheme.txt`, `build_errors.txt`, `diff_baseline.txt`,
  `errors.txt`, `stage11_output.txt`
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

- 2026-10-11 — Excel import auto-creates missing modules: `validate()`
  discovers/classifies module names (no DB writes), `ImportReport` gains
  module discovery getters + summary section, `importValidRows` creates
  new modules via ModuleService with orphan cleanup and concurrent-create
  safety, ImportPanel preview/result show module counts, 12 new test
  scenarios (M1-M12). Verified: build exit 0, ExcelImportTest 74/0,
  **1056/0 REGRESSION_GREEN (19 suites)**. Uncommitted on `main`.
- 2026-10-11 — Light Mode colour correction (Dark Mode frozen):
  root-caused stale FlatLaf delegate colours (UIManager updates but
  component fields don't), added `Ui.rethemeOne` UIManager fallback for
  unregistered component types + plain JScrollPane viewports, light
  tokens aligned to reference palette (CTRL_LINE `#8A94A6` for 3:1,
  SIDE `#E1E7F0` for sidebar-edge 1.17:1), `applyDefaults` expanded
  with verified FlatLaf 3.7.2 keys (tooltip/popup/check/radio/spinner/
  scrollbar/disabled), fixed stale `pageHeader` title and syllabus
  JLists via screenshot QA. Verified: `ThemeSwitchProbe` 4×transition
  stale_count=0, `ThemeSystemTest` 358/0, **1019/0 REGRESSION_GREEN
  (19 suites)**, 31 screenshots inspected (light-green, dark-green,
  live dark→light switch), live launch. Uncommitted on `main`.
- 2026-10-10 (latest) — Dark mode + accent schemes + sidebar toggle +
  blueprint split-pane: `Theme` appearance×accent token system with
  `java.util.prefs` persistence, `Ui` qn.* retheme registry + zebra/hover
  renderer, AppShell footer (Light/Dark + accent cycler), eager-colour
  retrofit across 6 panels, GeneratePaperPanel JSplitPane with
  collapsible preview, `ThemeSystemTest` (358 checks). Verified:
  build exit 0, 1019/0 REGRESSION_GREEN (19 suites), 20 screenshots
  inspected (dark-plum + light-green), live launch. Uncommitted on
  `main`.
- 2026-10-10 (nova run) — Quest Nova branding + global palette pass:
  shipped icon (`resources/`, classpath-first `ui.Branding`), window/
  sidebar/dialog retitle, `Theme` cool-surface remap + contrast audit
  (52/52 measured, 7 documented exceptions), non-color selection bar,
  section struts in GeneratePaperPanel. Screenshot QA found and fixed
  two real defects (gridy RELATIVE overlap in the papers detail pane;
  card-title/actions overlap at 1000px). Verified: build exit 0,
  661/0 REGRESSION_GREEN (corrected the stale "679" figure), 10
  screenshots re-inspected, live launch verified. All uncommitted on
  `main`.
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
