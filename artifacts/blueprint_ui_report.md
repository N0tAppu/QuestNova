# Blueprint UI Regression Repair — Final Report (Sections A–M)

Task: protect git → acceptance questionnaire → diagnose/repair reported Blueprint regressions →
richer colorful palette → real question-paper generation through the app → recheck workflow →
full regression → this evidence-based report.
Date: 2026-10-09/10. Branch: `fix/blueprint-ui-regressions` @ `6bebd16`.
Registry note: no Java/Swing-specific skill exists in the registry; standard systematic-debugging /
TDD / color / contrast / accessibility skill guidance was applied.

## A. Git safety and final git state (no push performed)

- Never pushed, no force-push, no history rewrite, no branch deletion, no destructive reset.
  `git remote -v` = origin defined, no remote refs present; nothing left the machine.
- Final state (working tree clean, branch `fix/blueprint-ui-regressions`):

  | Branch | Commit | Meaning |
  |---|---|---|
  | `main` | `c2aa087` | pre-redesign baseline — untouched |
  | `backup/pre-ui-redesign` | `c2aa087` | original safety copy — intact |
  | `ui/redesign` | `77f2b22` | redesign head |
  | `backup/pre-blueprint-fix` | `77f2b22` | pre-repair recovery point — intact |
  | `fix/blueprint-ui-regressions` | `6bebd16` | work branch (final) |

- Work chain on top of `77f2b22`: `0f33c17` (repair) → `2ea9c76` (docs) → `ba5790d` (palette) →
  `6bebd16` (docs recheck). Commits only at verified milestones; every commit preceded by a green
  test run.

## B. Acceptance questionnaire

- `artifacts/blueprint_ui_acceptance.md`: **56/56 items PASS, 0 PENDING** after final recheck;
  rule "no PASS without verification" honored — items were flipped only as their evidence landed
  (repro → E2E → DB-down drill → regression).
- Evidence ids: **E-REPRO14** (40/40, full workflow via real `MainFrame` events),
  **E-S12E2E** (81/81, 19-step mandatory checklist incl. shortage + failure path),
  **E-S13DBDOWN** (10/10, DB-down drill), final 18-suite regression (661/0).
- Recorded in commits `2ea9c76` and `6bebd16`.

## C. Diagnosed root causes (reproduced, not guessed)

1. **Pattern-row controls silently disabled (F-DEFECT, repaired `0f33c17`)**:
   `rebuildPatternSection()` recreated the table model after every pattern-row dialog operation,
   wiping row selection → Edit Row / Remove Row disabled. Reproduced with instrumented evidence
   `DIAG pre-S26: editEnabled=false` after a successful edit; post-fix `editEnabled=true`.
   Pre-existing on `main`, not introduced by the redesign.
2. **Disabled buttons looked enabled (F7, repaired `ba5790d`)**: disabled primary/danger buttons
   kept saturated indigo/red fill with only a gray label, so "Generate Paper"/"Clear Blueprint"
   appeared available when disabled — matching the reported "controls unavailable" perception.
   Pre-fix gray label on saturated fill failed the 4.5:1 text requirement; disabled pairs now
   measure 5.09:1 and 6.34:1.
3. **Not reproducible as functional defects**: module selection, syllabus controls and RNG
   generation behaved correctly through real component events end-to-end (E-REPRO14, E-S12E2E);
   the residual symptom traced to the disabled-state styling (item 2).
4. Test-infrastructure finding (not a product defect): per-check Swing dialog-capture timers could
   go stale and intercept later dialogs; fixed in the harness with a single scoped janitor timer.

## D. Repair loop (fix-first / test-first, smallest responsible fixes)

- `0f33c17`: `rebuildPatternSection(boolean keepSelection)` restores selection across same-module
  refreshes only — does not force-enable buttons; disabled states still driven by real enablement
  logic (verified: B9 disable path still observed when no row selected).
- `ba5790d` (`UiTheme.applyActionColors`): disabled bg/fg from an `enabled` property listener +
  dedicated disabled tints — presentation-only, no behavior change; enabled/disabled gating logic
  untouched (E2E asserts Generate disabled when invalid/shortage).
- Pre/post DIAG + suite runs for each fix; no fix shipped without a green run.

## E. Richer palette with tasteful gradients (Section 5)

- Palette: indigo primary `#4F46E5`/`#4338CA`, violet accent `#7C3AED`, teal secondary `#0F766E`,
  emerald success `#047857`, coral/red danger `#DC2626`, amber warning `#B45309`, tinted surfaces
  (`#ECF0FB` bg, `#F6F8FE` stripe, `#E4E9FA` header, `#DCE1FD` selection), disabled tints
  `#DCE1FD`/`#FCE8E8`.
- Exactly three intentional Java2D gradient roles (`UiTheme.brandGradient`): application header
  strip, active tab indicator (MainFrame paint), section band borders — none behind long text.
- `palette-candidate.ps1` **31/31 contrast pairs PASS** (incl. both disabled pairs);
  `gradient-check.ps1` **GRADIENT_TEXT_PASS**.

## F. Semantic colour usage (Section 6)

- Colour never changes rules: VALID (green) / NOT VALID (red) always paired with the literal word
  plus bullet reasons; FEASIBLE / NOT FEASIBLE include full text detail; status text color alone
  never carries meaning.
- Red reserved for destructive/error (Delete Question Paper, NOT VALID); indigo for primary
  actions; teal for secondary/neutral bands; disabled = muted tint + gray label, still readable.
- All text pairs ≥4.5:1 (min 5.09:1), control borders ≥3:1 — verified by script, not by eye.

## G. Visual quality check (Section 7) — real renders, failing pairs fixed

- Rendered and inspected real screenshots from live runs: `s11_import`, `s11_blueprint`,
  `s12_*` set, `btn_disabled_zoom`, `s13_dbdown_*`.
- Fixes made from evidence: F1 horizontal-bar overflow → `ScrollBody` wrapper (in `ba5790d`);
  F7 disabled state → muted fill (verified in `btn_disabled_zoom.png` + `s12_f6_small.png`).
- Post-fix re-render confirmed both; no regressions introduced (fresh Stage11Show GREEN).

## H. Real question paper generated through the app (Section 8, mandatory)

- **Stage12E2E: 81 passed / 0 failed** driving the real `MainFrame` end-to-end:
  subject → modules → syllabus → cell allocations (incl. live invalid-input rejection) →
  Set Rule → Apply Pattern Policy → exam settings → tab round-trip → Validate → Validate
  Availability → **real Generate click** → Review dialog → **Finalize confirm + success
  (Paper ID 202)** → **real PDF export buttons through a real JFileChooser** (question paper +
  answer key) → history → reopen (FINALIZED, exports enabled) → delete with default=Cancel safety
  → real delete (CASCADE removes only paper rows; bank untouched) → subject-change confirm
  (No keeps / Yes clears) → **shortage failure path** (real Remove/Add Row 2×10 over a 6-question
  pool → NOT FEASIBLE with shortage detail → Generate fails cleanly, no partial row, pattern
  unchanged, UI still usable) → Clear Blueprint → resize renders.
- Deep [DB] checks passed: slot count == paper questions (4), sequence continuous, marks total 12,
  modules/difficulty per slot policy, no duplicate questions, snapshot isolation (source edit
  after finalize does not change stored paper).
- PDFs rendered from the actual exported files: `s12_pdf_paper.png`, `s12_pdf_key.png`
  (595×841, correct content/marks) — no fabricated screenshots; raw harness logs kept.
- Stage11Show re-run also green (paper 212, both PDFs, `edit enabled=true`, `generate
  enabled=true`).

## I. Safe test-data cleanup (Section 9)

- Only harness-created fixtures were deleted (fixture subjects 485/486/507, their modules and
  questions, harness papers); original data never truncated: subjects 123 (OOP) and 124
  (Data Structures), 10 original modules, 500 original questions.
- Before/after counts identical every run: **2 / 10 / 500 / 0 / 0 / 0 / 0** (subjects, modules,
  questions, papers, paper_questions, paper_blueprints, paper_slots) — asserted by the harness
  (BASELINE == FINAL) and re-verified by direct SQL after the final audit.
- Others' papers: none existed; deletion checks additionally proved CASCADE touches only the
  target paper's rows (G6k/G6l).

## J. Full regression after everything (Section 11)

- `run-regress.ps1`: **18 suites, TOTAL passed=661 failed=0, REGRESSION_GREEN** — equals
  `BASELINE.txt` (660 + ConnectionTest = 661). Suites include CRUD, blueprint, RNG/difficulty,
  generation, review/finalization, deletion, PDF export, Stage11 UI suites, smokes.
- Questionnaire recheck performed after the regression (Section B).
- Contrast + gradient scripts re-run green on the final tree.

## K. Final visual/interaction audit (Section 12, normal + resized)

- Fresh Stage11Show on the final build: GREEN — renders at default 1000×650
  (`s11_subjects/questions/import/blueprint/papers`), review window, PDFs; interaction asserts
  (generate enabled, pattern edit enabled, finalize/export enabled) all true.
- Resized renders inspected: `s12_f6_large.png` (1500×950) and `s12_f6_small.png` (1200×680) —
  no clipped controls, action bar reachable, disabled states visibly muted at both sizes.
- DB-down interaction drill (`Stage13DbDown`, 10/10): one friendly startup warning
  (`s13_dbdown_warning.png` 441×209), frame opens (`s13_dbdown_frame.png` 1000×650), all five
  tabs activate with per-panel error dialogs, zero uncaught EDT exceptions.
  (Note: image attachments render out of order in this session; file dimensions + capture gating
  + harness assertions are the authoritative evidence.)

## L. App preservation and rollback paths (Sections 13–14)

- Diff vs redesign head `77f2b22` = **8 files**: 7 `src/ui/*.java` + questionnaire doc.
  No schema, DAO, service, generation algorithm, or test-facade changes; no new dependencies;
  FlatLaf redesign preserved; no JavaFX/web.
- Facade API untouched — proven by the full 18-suite regression compiling and passing (H5).
- Rollback paths preserved: `backup/pre-ui-redesign` (`c2aa087`), `backup/pre-blueprint-fix`
  (`77f2b22`), pristine `main`; forward rollback via `git revert` of the 4 work commits.
- Nothing pushed; final git state reported in Section A.

## M. Changed files and evidence inventory

- Committed: `0f33c17` (GeneratePaperPanel + docs), `2ea9c76` (docs), `ba5790d` (UiTheme,
  MainFrame, GeneratePaperPanel, SubjectPanel, QuestionPanel, ImportPanel, GeneratedPapersPanel),
  `6bebd16` (docs). Diff vs `77f2b22`: 372 insertions / 39 deletions across 8 files.
- Questionnaire: `artifacts/blueprint_ui_acceptance.md` (56/56 PASS).
- Verification harnesses (temp, not committed): `ReproBlueprintFlow` (E-REPRO14 40/40),
  `Stage11Show` (visual suite), `Stage12E2E` (81/81), `Stage13DbDown` (10/10),
  `palette-candidate.ps1` (31/31), `gradient-check.ps1`, `run-regress.ps1` (661/0).

## Closure — stop conditions met

Reported regressions repaired with root-cause evidence ✓ · workflow usable end-to-end ✓ ·
real question paper generated through the UI incl. failure path ✓ · richer colorful UI with
intentional gradients ✓ · questionnaire rechecked 56/56 ✓ · full suite passes at baseline ✓ ·
DB clean at exact baseline ✓ · rollback paths preserved ✓ · no push performed ✓.
