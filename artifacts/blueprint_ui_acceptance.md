# Blueprint UI Acceptance Questionnaire

Verification questionnaire for the QuestNova Examination Blueprint workflow.
**Rule: no item may be marked PASS before it has actually been verified; unverified items stay PENDING.**

- Baseline: `c2aa087` (pre-redesign), redesign head `77f2b22`, repair branch `fix/blueprint-ui-regressions`.
- Source audit references: `src/ui/GeneratePaperPanel.java`, `src/ui/MainFrame.java`, `src/ui/UiTheme.java`, `service/BlueprintService.java`, `service/PatternPolicy.java`, `service/PaperGenerator.java`.
- Verification levels: **[STATE]** = facade/automated state test, **[LIVE]** = real component event on a constructed MainFrame, **[UI]** = dialog/visual inspection, **[DB]** = database query.

Result legend: `PENDING` → `PASS` / `FAIL` / `UNVERIFIED` (with evidence id).

**Final recheck (2026-10-09, after all repairs + palette work): 56/56 items PASS, 0 PENDING.**
Full 18-suite regression green (`TOTAL passed=661 failed=0`, = baseline `BASELINE.txt`), DB at exact
baseline 2/10/500/0/0/0/0, contrast suite 31/31 PASS, gradients GRADIENT_TEXT_PASS.

Primary evidence artifact: **E-REPRO14** = `ReproBlueprintFlow` full-workflow drive through the real `MainFrame`
(subject → modules → syllabus → cell allocations → Set Rule → Apply Policy → exam settings → tab round-trip →
Validate → Availability → real Generate click → Review → Finalize confirm+success → history count),
run 2026-10-09 against clean build of `0f33c17`: **40 checks passed, 0 failed**, DB restored 2/10/500/0.

Secondary evidence artifact: **E-S12E2E** = `Stage12E2E` full 19-step checklist drive through the real `MainFrame`
(feasible build with invalid-input rejection → Apply Policy → Validate/Availability → real Generate → Review →
Finalize confirm+success → real PDF exports via file chooser → deep DB checks → snapshot-isolation check →
reopen + safe-delete (default=Cancel) → real delete with CASCADE → subject-change confirm No/Yes →
shortage phase with real Add Row/Remove/Clear → resize renders), run 2026-10-09 against build of `ba5790d`:
**81 checks passed, 0 failed**, DB restored 2/10/500/0/0/0/0.

## Findings so far (root-caused, repair committed)

1. **F-DEFECT (repaired, commit `0f33c17`)**: after any refresh that follows a pattern-row dialog operation,
   `rebuildPatternSection()` cleared the pattern table model, which silently reset the row selection and
   disabled **Edit Row / Remove Row** — controls looked/behaved unavailable until the user re-clicked a row.
   Matches reported symptom "mark-pattern operations unavailable or not functioning".
   Evidence: pre-fix run showed `DIAG pre-S26: editEnabled=false` after a successful edit; post-fix
   `editEnabled=true` and the whole 40-check flow passes. Present on `main` too (pre-existing, not
   introduced by the redesign); repaired by restoring the selection across same-module refreshes only.
2. Reported symptoms for module selection, syllabus controls and RNG generation could **not** be
   reproduced as functional defects through real component events (E-REPRO14 green end-to-end).
   A related visual defect **was** found and repaired (finding 3).
3. **F-DEFECT (repaired, commit `ba5790d`)**: disabled primary/danger buttons kept their saturated
   fill (indigo/red) with only a gray label, so "Generate Paper", "Clear Blueprint" etc. looked enabled
   when disabled — matching the reported "controls appear unavailable / not functioning" perception.
   Fixed in `UiTheme.applyActionColors` with dedicated disabled tints + an `enabled` property listener;
   contrast of disabled pairs (5.09:1 and 6.34:1) verified by `palette-candidate.ps1` (**31/31 PASS**).
   Evidence: [UI] `s12_f6_small.png` (disabled Generate/Clear clearly muted), `btn_disabled_zoom.png`.

---

## A. Module and syllabus workflow

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| A1 | Subject can be selected | `subjectBox` action → `applySubjectChange` | Selecting a subject stores `exam.subjectId` and loads its modules | [LIVE] real combo event + [STATE] `getSelectedSubjectId()` | PASS | E-REPRO14 S4–S6 |
| A2 | Modules of the subject load | `loadAllModules()` → `availableList` | Available list shows exactly the subject's unselected modules | [LIVE] list model after subject select | PASS | E-REPRO14 S7–S8 (2 fixture modules listed) |
| A3 | Modules can be selected and added | "Add to Syllabus" button → `onAddToSyllabus` → `addModulesToSyllabus` | Selected modules move to syllabus; alloc table gains rows | [LIVE] real button `doClick()` | PASS | E-REPRO14 S9–S11 |
| A4 | Allocation controls appear at the right time | `rebuildAllocTable` / `rebuildPatternSection` | With syllabus non-empty, alloc rows exist; pattern footer shows module name (not "Select a module…") | [LIVE] | PASS | E-REPRO14 S12–S13 |
| A5 | Module selection persists when expected | `currentModuleId` + `syncSelectionToCurrent` | Selected module stays current across refreshes until changed/cleared | [STATE] + [LIVE] | PASS | E-REPRO14 S13 + S24 + S32 (current module and enablement correct after many refreshes and tab round-trip) |
| A6 | Changing subject clears/refreshes dependent state | `applySubjectChange(interactive=true)` | Confirm shown when blueprint has data; on confirm, syllabus/alloc/pattern/preview cleared, modules reloaded for new subject | [LIVE] incl. dialog confirm | PASS | E-S12E2E A6a/H3a (confirm shown), H3b (No keeps blueprint), A6b (Yes clears dependent state) |
| A7 | Workflow proceeds when prerequisites satisfied | `refreshAll` enablement | After subject+syllabus+alloc+pattern valid: Generate enabled, Clear enabled | [STATE] `isGenerateEnabled`, [LIVE] button state | PASS | E-REPRO14 S22, S36 + E-S12E2E P1-20/P4-6 (Generate) and F3b (real Clear click opens confirm ⇒ Clear enabled) |
| A8 | Invalid configurations rejected with feedback | `validateBlueprint` + `rebuildSummary` | Summary shows NOT VALID with precise bullet reasons; Generate disabled | [STATE] `getValidationErrors`, [UI] summary text | PASS | E-REPRO14 S33 + live invalid case message "Repro Module 1 pattern total is 1 marks, but its allocated marks are 4." (observed pre-fix run); S22/S36 disabled-when-invalid |
| A9 | Allocation cell editing writes to the model | `allocModel.setValueAt` col 1 → `exam.setAllocation` | Typing marks in "Allocated Marks" updates `exam`, derived %/totals refresh, invalid input rejected with message | [LIVE] `editCellAt` + `stopCellEditing` | PASS | E-REPRO14 S14 (write path) + E-S12E2E P1-8/P1-9 (live "-3" rejected with "Allocated marks must be a non-negative whole number.", model untouched) + P1-10 (valid edits stored) |

## B. Module-wise pattern editing

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| B1 | Select a module whose pattern to edit | `selectedList`/`allocTable` selection → `setCurrentModule` | Selecting in B or C sets current module; Section D shows its name | [LIVE] | PASS | E-REPRO14 S13, S24 |
| B2 | Existing pattern rows appear | `rebuildPatternSection` | Rows for current module listed; subtotal shown | [LIVE] | PASS | E-REPRO14 S19–S20 (policy-composed rows listed) |
| B3 | Add pattern row works (real dialog) | "Add Row" → `showPatternDialog(false)` | Modal dialog collects marks+count; row appears; totals update | [LIVE] real click + auto-dismissing dialog driver | PASS | E-REPRO14 S27 |
| B4 | Edit pattern row works | "Edit Row" enabled only with row selected → dialog prefilled | Edited values reflected | [LIVE] | PASS | E-REPRO14 S25–S26 (edit 1×1 then back to 2×2; requires F-DEFECT fix) |
| B5 | Remove pattern row works | "Remove Row" → `onRemovePatternRow` | Row removed; totals update | [LIVE] | PASS | E-REPRO14 S28 |
| B6 | Validation updates correctly | `refreshAll` after each change | Summary/generate state track pattern edits immediately | [STATE] | PASS | E-REPRO14 S21, S22, S29, S32 |
| B7 | Pattern totals match allocation | `patternTotal` vs `getAllocation` in footer/label | Difference = 0 when consistent; precise message when not | [STATE] `getValidationErrors` | PASS | E-REPRO14 S21/S29 (consistent) + invalid-case message in A8 |
| B8 | Preview reflects real changes | `rebuildPreview` | Preview rows = derived slots after every pattern edit | [STATE] `getPreviewRowCount` | PASS | E-S12E2E B8 (preview rows == 4 derived slots after policy) |
| B9 | Controls enable/disable for correct reasons | `setPatternButtonsEnabled` / `updatePatternSelectionButtons` | Add only with module; Edit/Remove only with module+row selected | [STATE] `isPatternEditEnabled`/`isPatternRemoveEnabled` + [LIVE] | PASS | E-REPRO14 S24 (enabled with module+row) + pre-fix `DIAG: editEnabled=false` with no row selected (disable path) |

## C. Global mark distribution / pattern policy

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| C1 | Rule can be defined | "Set Rule" → `onSetPatternRule` → `PatternPolicy.setRule` | Valid text stored; invalid text rejected with message | [LIVE] + [STATE] `hasPatternRule` | PASS | E-REPRO14 S16–S17 (valid stored) + live rejection message "The rule allocation must be a positive whole number, for example 12." (observed in instrumented run) |
| C2 | Rule can be applied | "Apply Pattern Policy" → `applyPatternPolicy` | Patterns composed for every allocated module | [LIVE] real click | PASS | E-REPRO14 S18–S20 |
| C3 | Pattern model actually changes | `BlueprintService.applyPatternPolicy` | Pattern rows created per policy semantics | [STATE] | PASS | E-REPRO14 S19–S20 (4→2×2 and 8→4×2 rows) |
| C4 | Preview consistent with model | `refreshPolicyPreview` / `rebuildPreview` | Policy preview + preview table match stored rows | [STATE] `getPolicyPreviewText` | PASS | E-S12E2E C4 (policy preview text consistent with stored pattern rows) |
| C5 | Rule reused only for applicable allocations | `PatternPolicy.hasRule(allocation)` semantics | Rule for 12 applies to 12-mark allocations only; never scaled to other totals | [STATE] existing Stage 12 suite + [LIVE] | PASS | `PatternCompositionTest` (50/50 green): "12-mark allocation reuses the configured rule", "6-mark module gets its own composition, never a scaled rule", "Rule applied to all five 12-mark modules" |
| C6 | Question shortage does not mutate pattern | `applyPatternPolicy` + availability path | Shortage reported at feasibility/generation only; stored pattern unchanged | [STATE] shortage case | PASS | E-S12E2E C6/E7c (2×10 pattern identical after NOT-FEASIBLE report and failed generation) |

## D. Overall examination difficulty

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| D1 | Faculty can select overall difficulty | `examDifficultyBox` → `setExamDifficulty` | One overall value stored (`ExamBlueprint.EXAM_DIFFICULTIES`) | [LIVE] + [STATE] | PASS | E-REPRO14 S30–S31 (Medium + Semester + 12 stored) |
| D2 | Difficulty persists through workflow | field on `exam` | Survives syllabus/pattern edits and tab switches | [LIVE] | PASS | E-REPRO14 S32 (value asserted after tab round-trip and later edits) |
| D3 | Generation respects difficulty policy | `ExamDifficultyPolicy.assign` inside `PaperGenerator` | Slots get difficulty per policy; overall value governs | [STATE] Stage 12 suite + [DB] generated paper | PASS | E-S12E2E D3 ([DB] assigned difficulties follow slot policy) + `Stage12DifficultyRngTest` 49/49 + `PaperGenerationTest` 48/48 in final regression |
| D4 | No manual per-question difficulty UI | source audit | No control exists to set difficulty per question | [SOURCE] confirmed in audit | PASS (source audit 2026-10-09: no per-question difficulty control in `GeneratePaperPanel`/`ReviewPaperPanel`) |

## E. RNG-based generation

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| E1 | Generate reachable when valid | `generateButton` enablement in `refreshAll` | Enabled iff `validateBlueprint` empty | [LIVE] | PASS | E-REPRO14 S22, S36 |
| E2 | Generate calls real generation path | `onGeneratePaper` → `generatePaper` → `PaperGenerator.generatePaper` | Real service called with current blueprint | [LIVE] real button click → [DB] paper row | PASS | E-REPRO14 S37–S38 (real Generate click → review → finalize → [DB] count +1) |
| E3 | Feasibility gate correct | `checkAvailability` / `AvailabilityValidator` | Infeasible config reported before any selection | [STATE] shortage case | PASS | E-S12E2E E3 ("NOT FEASIBLE … Shortage: 4" with requirement patterns, before generation); feasible path E-REPRO14 S34 |
| E4 | Candidate pools by (module, marks) | `PaperGenerator` internals | Questions drawn only from matching pools | [STATE] PaperGenerationTest + [DB] | PASS | E-S12E2E E4/E6 ([DB] module marks sequence correct) + `PaperGenerationTest` 48/48 in final regression |
| E5 | No duplicate questions in a paper | `PaperGenerator` | Same question id never appears twice | [DB] generated paper_questions | PASS | E-S12E2E E5 ([DB] 4 distinct question ids in paper 202) |
| E6 | Marks/module/count/difficulty/sequence correct | review model vs blueprint | Every slot filled; totals match; order by sequence | [DB] + [UI] review inspection | PASS | E-S12E2E E4/E6/E6b ([DB] sequence, marks total == 12, snapshot rows == slots; [UI] PDF render shows 2/2/4/4 = 12 in order) |
| E7 | Shortage fails cleanly (no partial paper) | `GenerationResult` failure path | Failure message; no paper row written; blueprint intact; UI usable after | [STATE] + [DB] counts | PASS | E-S12E2E E7a ("Generation failed. … Shortage: 4"), E7b (no paper row), E7d (summary still VALID), E7e (Validate responds after failure) |
| E8 | Success opens review workflow | `onGeneratePaper` → `reviewService.openReview` → `ReviewPaperDialog.show` | Review dialog opens with generated paper | [LIVE] dialog observed | PASS | E-REPRO14 S37 (window "Review Generated Paper" observed showing) |

## F. Visual usability

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| F1 | Blueprint controls visible/reachable at normal size (1000×650) | live frame | Sections reachable by scroll; action bar + preview pinned and visible | [UI] screenshot at default size | PASS | `s11_blueprint.png` + E-S12E2E (whole flow driven at default size; action bar reachable, no H-bar overflow after `ScrollBody` fix) |
| F2 | Lower sections use sensible scrolling | `JScrollPane(body)` (line 201) | Body scrolls; no impractically tall window required | [UI] screenshot + resize | PASS | `s11_blueprint.png` / `s12_shortage_blueprint.png` (body scrollbar reaches Section D/summary at default height); E-S12E2E full flow at default size |
| F3 | Buttons genuinely functional (not just enabled) | all action buttons | Every visible enabled button performs its real action | [LIVE] A/B/C/E steps | PASS | E-S12E2E: real Clear Blueprint click (F3b/F3c), real Remove (F3a), real Add Row (P4-4), subject-change confirm (A6/H3), delete confirm (G6e–G6i), plus core buttons in E-REPRO14 |
| F4 | Colour system consistent and readable | `UiTheme` palette | All text pairs ≥4.5:1, control borders ≥3:1; roles used semantically | [STATE] contrast script + [UI] | PASS | `palette-candidate.ps1` **31/31 PASS** (incl. disabled pairs 5.09/6.34); semantic roles verified in renders (green VALID, red NOT VALID/DELETE, indigo primary) |
| F5 | Gradients used intentionally | new theme work | Small number of purposeful gradients (header/status/section accents), none behind long text | [UI] inspection | PASS | `gradient-check.ps1` GRADIENT_TEXT_PASS; renders show exactly 3 gradient roles (header strip, active tab indicator, section bands) with white bold text ≥24px only |
| F6 | Layout usable after resizing | frame resize to larger/smaller | No clipped controls; tables/lists resize sanely | [UI] | PASS | E-S12E2E F6 (resize completed without exceptions) + `s12_f6_large.png` / `s12_f6_small.png` inspected — no clipped controls at either size |
| F7 | Enabled vs disabled states distinguishable | FlatLaf + `UiTheme` button helpers | Clear visual difference; disabled actions remain disabled | [UI] + [STATE] | PASS | defect found+fixed `ba5790d` (finding 3); `btn_disabled_zoom.png` + `s12_f6_small.png` (disabled Generate/Clear muted lavender + gray label); disabled pair contrast 5.09:1 |

## G. End-to-end question-paper verification

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| G1 | Real paper generated through real application workflow | MainFrame → Blueprint tab → real controls → Generate | Paper row created from live UI interaction (not a unit-test shortcut) | [LIVE] + [DB] | PASS | E-REPRO14 S37–S38 |
| G2 | Content matches configured blueprint | generated slots vs blueprint | Question count, marks, modules, difficulty distribution match | [DB] + [UI] review | PASS | E-S12E2E G2a/G2b (slots == paper_questions == 4), E4/E5/E6/D3 deep [DB] checks on paper 202 |
| G3 | Review and finalize work | `ReviewPaperDialog` → `ReviewPaperPanel.finalizeCurrentPaper` | Finalization transaction succeeds; status FINALIZED | [LIVE] + [DB] | PASS | E-REPRO14 S37–S38 + E-S12E2E P1-24–P1-27 (confirm + success + exactly one paper row) |
| G4 | PDF exports work | `PdfExportTest` + actual export path | Question paper + answer key PDFs produced with valid content | [STATE] suite + [UI] generated files inspected | PASS | E-S12E2E G4a/G4b/G4c (real export buttons + chooser → files exist, success messages) + [UI] `s12_pdf_paper.png` / `s12_pdf_key.png` rendered (1 page each, correct content) |
| G5 | Snapshot/history consistency | `question_papers` row + paper_questions | Finalized paper keeps its snapshot after source-question changes | [DB] + existing suite | PASS | E-S12E2E G5a/G5b/G5c (source question edited post-finalize → paper snapshot unchanged, source really changed) |
| G6 | History reopen + deletion | `GeneratedPapersPanel` delete semantics | Paper deletable; CASCADE removes its rows only; original data intact | [LIVE] + [DB] | PASS | E-S12E2E G6a–G6l (reopen FINALIZED + exports enabled; delete default=Cancel; cancel keeps; real delete CASCADE only; bank untouched) |
| G7 | Temporary test artifacts cleaned | before/after counts | Only test-created rows removed; subjects/modules/questions/papers baseline preserved | [DB] before/after | PASS | E-S12E2E CLEANUP (BASELINE == FINAL 2/10/500/0/0/0/0); E-REPRO14 same; final sweep at end of task |

## H. Additional checks discovered in source audit

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| H1 | Tab navigation refreshes Blueprint on enter | `MainFrame.refreshScreen(3)` | State retained (subject/syllabus kept); deleted modules pruned | [LIVE] tab switch | PASS | E-REPRO14 S32 |
| H2 | Re-click active tab refreshes | `mousePressed` handler | Second click on active tab calls `refreshPanel` without wiping state | [LIVE] | PASS | E-S12E2E H2 (re-click keeps subject/syllabus/pattern state) |
| H3 | Subject-change confirmation default does not destroy data | `applySubjectChange` YES_NO dialog | Default option semantics unchanged from baseline; user can cancel safely | [UI] dialog inspection | PASS | E-S12E2E H3a (dialog shown, janitor recorded default=Yes — same as baseline) + H3b (No keeps blueprint intact); A6b (Yes clears) |
| H4 | DB-down leaves UI usable | `loadSubjects`/`loadAllModules` catch blocks | Error message (or stderr at startup), no crash, controls consistent | [STATE] existing Stage 11 suite + [LIVE] shadow drill | PASS | E-S13DBDOWN **10/10** (classpath-shadow `DatabaseConnection`, source untouched): one friendly startup warning, frame opens, all 5 tabs swept with per-panel "Could not load … Please check that MySQL is running." dialogs, blueprint facade answers, **0 uncaught EDT exceptions**, DB counts unchanged 2/10/500/0; renders `s13_dbdown_warning.png` (441x209) + `s13_dbdown_frame.png` (1000x650) + prior `s11_dbdown_dialog.png`/`s11_dbdown_frame.png` |
| H5 | Facade API surface unchanged for test suites | `git diff` on public methods | No public/package-visible method removed or renamed | [STATE] full 18-suite regression | PASS | full regression re-run after all fixes: **TOTAL passed=661 failed=0 REGRESSION_GREEN** (all 18 suites compile + run against `ba5790d`; public facades untouched by diff) |
| H6 | Allocation editing cannot corrupt indices | `setValueAt` row→`syllabusIds().get(row)` | Row index valid at edit time; table rebuilt after every change | [LIVE] | PASS | E-REPRO14 S14 (rows 0/1 with 2-row table, model correct after rebuilds) |
