# Blueprint UI Acceptance Questionnaire

Verification questionnaire for the QuestNova Examination Blueprint workflow.
**Rule: no item may be marked PASS before it has actually been verified; unverified items stay PENDING.**

- Baseline: `c2aa087` (pre-redesign), redesign head `77f2b22`, repair branch `fix/blueprint-ui-regressions`.
- Source audit references: `src/ui/GeneratePaperPanel.java` (1355 lines), `src/ui/MainFrame.java`, `src/ui/UiTheme.java`, `service/BlueprintService.java`, `service/PatternPolicy.java`, `service/PaperGenerator.java`.
- Verification levels: **[STATE]** = facade/automated state test, **[LIVE]** = real component event on a constructed MainFrame, **[UI]** = dialog/visual inspection, **[DB]** = database query.

Result legend: `PENDING` → `PASS` / `FAIL` / `UNVERIFIED` (with evidence id).

---

## A. Module and syllabus workflow

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| A1 | Subject can be selected | `subjectBox` action → `applySubjectChange` | Selecting a subject stores `exam.subjectId` and loads its modules | [LIVE] real combo event + [STATE] `getSelectedSubjectId()` | PENDING | |
| A2 | Modules of the subject load | `loadAllModules()` → `availableList` | Available list shows exactly the subject's unselected modules | [LIVE] list model after subject select | PENDING | |
| A3 | Modules can be selected and added | "Add to Syllabus" button → `onAddToSyllabus` → `addModulesToSyllabus` | Selected modules move to syllabus; alloc table gains rows | [LIVE] real button `doClick()` | PENDING | |
| A4 | Allocation controls appear at the right time | `rebuildAllocTable` / `rebuildPatternSection` | With syllabus non-empty, alloc rows exist; pattern footer shows module name (not "Select a module…") | [LIVE] | PENDING | |
| A5 | Module selection persists when expected | `currentModuleId` + `syncSelectionToCurrent` | Selected module stays current across refreshes until changed/cleared | [STATE] + [LIVE] | PENDING | |
| A6 | Changing subject clears/refreshes dependent state | `applySubjectChange(interactive=true)` | Confirm shown when blueprint has data; on confirm, syllabus/alloc/pattern/preview cleared, modules reloaded for new subject | [LIVE] incl. dialog confirm | PENDING | |
| A7 | Workflow proceeds when prerequisites satisfied | `refreshAll` enablement | After subject+syllabus+alloc+pattern valid: Generate enabled, Clear enabled | [STATE] `isGenerateEnabled`, [LIVE] button state | PENDING | |
| A8 | Invalid configurations rejected with feedback | `validateBlueprint` + `rebuildSummary` | Summary shows NOT VALID with precise bullet reasons; Generate disabled | [STATE] `getValidationErrors`, [UI] summary text | PENDING | |
| A9 | Allocation cell editing writes to the model | `allocModel.setValueAt` col 1 → `exam.setAllocation` | Typing marks in "Allocated Marks" updates `exam`, derived %/totals refresh, invalid input rejected with message | [LIVE] `editCellAt` + `stopCellEditing` | PENDING | |

## B. Module-wise pattern editing

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| B1 | Select a module whose pattern to edit | `selectedList`/`allocTable` selection → `setCurrentModule` | Selecting in B or C sets current module; Section D shows its name | [LIVE] | PENDING | |
| B2 | Existing pattern rows appear | `rebuildPatternSection` | Rows for current module listed; subtotal shown | [LIVE] | PENDING | |
| B3 | Add pattern row works (real dialog) | "Add Row" → `showPatternDialog(false)` | Modal dialog collects marks+count; row appears; totals update | [LIVE] real click + auto-dismissing dialog driver | PENDING | |
| B4 | Edit pattern row works | "Edit Row" enabled only with row selected → dialog prefilled | Edited values reflected | [LIVE] | PENDING | |
| B5 | Remove pattern row works | "Remove Row" → `onRemovePatternRow` | Row removed; totals update | [LIVE] | PENDING | |
| B6 | Validation updates correctly | `refreshAll` after each change | Summary/generate state track pattern edits immediately | [STATE] | PENDING | |
| B7 | Pattern totals match allocation | `patternTotal` vs `getAllocation` in footer/label | Difference = 0 when consistent; precise message when not | [STATE] `getValidationErrors` | PENDING | |
| B8 | Preview reflects real changes | `rebuildPreview` | Preview rows = derived slots after every pattern edit | [STATE] `getPreviewRowCount` | PENDING | |
| B9 | Controls enable/disable for correct reasons | `setPatternButtonsEnabled` / `updatePatternSelectionButtons` | Add only with module; Edit/Remove only with module+row selected | [STATE] `isPatternEditEnabled`/`isPatternRemoveEnabled` + [LIVE] | PENDING | |

## C. Global mark distribution / pattern policy

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| C1 | Rule can be defined | "Set Rule" → `onSetPatternRule` → `PatternPolicy.setRule` | Valid text stored; invalid text rejected with message | [LIVE] + [STATE] `hasPatternRule` | PENDING | |
| C2 | Rule can be applied | "Apply Pattern Policy" → `applyPatternPolicy` | Patterns composed for every allocated module | [LIVE] real click | PENDING | |
| C3 | Pattern model actually changes | `BlueprintService.applyPatternPolicy` | Pattern rows created per policy semantics | [STATE] | PENDING | |
| C4 | Preview consistent with model | `refreshPolicyPreview` / `rebuildPreview` | Policy preview + preview table match stored rows | [STATE] `getPolicyPreviewText` | PENDING | |
| C5 | Rule reused only for applicable allocations | `PatternPolicy.hasRule(allocation)` semantics | Rule for 12 applies to 12-mark allocations only; never scaled to other totals | [STATE] existing Stage 12 suite + [LIVE] | PENDING | |
| C6 | Question shortage does not mutate pattern | `applyPatternPolicy` + availability path | Shortage reported at feasibility/generation only; stored pattern unchanged | [STATE] shortage case | PENDING | |

## D. Overall examination difficulty

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| D1 | Faculty can select overall difficulty | `examDifficultyBox` → `setExamDifficulty` | One overall value stored (`ExamBlueprint.EXAM_DIFFICULTIES`) | [LIVE] + [STATE] | PENDING | |
| D2 | Difficulty persists through workflow | field on `exam` | Survives syllabus/pattern edits and tab switches | [LIVE] | PENDING | |
| D3 | Generation respects difficulty policy | `ExamDifficultyPolicy.assign` inside `PaperGenerator` | Slots get difficulty per policy; overall value governs | [STATE] Stage 12 suite + [DB] generated paper | PENDING | |
| D4 | No manual per-question difficulty UI | source audit | No control exists to set difficulty per question | [SOURCE] confirmed in audit | PASS (source audit 2026-10-09: no per-question difficulty control in `GeneratePaperPanel`/`ReviewPaperPanel`) |

## E. RNG-based generation

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| E1 | Generate reachable when valid | `generateButton` enablement in `refreshAll` | Enabled iff `validateBlueprint` empty | [LIVE] | PENDING | |
| E2 | Generate calls real generation path | `onGeneratePaper` → `generatePaper` → `PaperGenerator.generatePaper` | Real service called with current blueprint | [LIVE] real button click → [DB] paper row | PENDING | |
| E3 | Feasibility gate correct | `checkAvailability` / `AvailabilityValidator` | Infeasible config reported before any selection | [STATE] shortage case | PENDING | |
| E4 | Candidate pools by (module, marks) | `PaperGenerator` internals | Questions drawn only from matching pools | [STATE] PaperGenerationTest + [DB] | PENDING | |
| E5 | No duplicate questions in a paper | `PaperGenerator` | Same question id never appears twice | [DB] generated paper_questions | PENDING | |
| E6 | Marks/module/count/difficulty/sequence correct | review model vs blueprint | Every slot filled; totals match; order by sequence | [DB] + [UI] review inspection | PENDING | |
| E7 | Shortage fails cleanly (no partial paper) | `GenerationResult` failure path | Failure message; no paper row written; blueprint intact; UI usable after | [STATE] + [DB] counts | PENDING | |
| E8 | Success opens review workflow | `onGeneratePaper` → `reviewService.openReview` → `ReviewPaperDialog.show` | Review dialog opens with generated paper | [LIVE] dialog observed | PENDING | |

## F. Visual usability

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| F1 | Blueprint controls visible/reachable at normal size (1000×650) | live frame | Sections reachable by scroll; action bar + preview pinned and visible | [UI] screenshot at default size | PENDING | |
| F2 | Lower sections use sensible scrolling | `JScrollPane(body)` (line 201) | Body scrolls; no impractically tall window required | [UI] screenshot + resize | PENDING | |
| F3 | Buttons genuinely functional (not just enabled) | all action buttons | Every visible enabled button performs its real action | [LIVE] A/B/C/E steps | PENDING | |
| F4 | Colour system consistent and readable | `UiTheme` palette | All text pairs ≥4.5:1, control borders ≥3:1; roles used semantically | [STATE] contrast script + [UI] | PENDING | |
| F5 | Gradients used intentionally | new theme work | Small number of purposeful gradients (header/status/section accents), none behind long text | [UI] inspection | PENDING | |
| F6 | Layout usable after resizing | frame resize to larger/smaller | No clipped controls; tables/lists resize sanely | [UI] | PENDING | |
| F7 | Enabled vs disabled states distinguishable | FlatLaf + `UiTheme` button helpers | Clear visual difference; disabled actions remain disabled | [UI] + [STATE] | PENDING | |

## G. End-to-end question-paper verification

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| G1 | Real paper generated through real application workflow | MainFrame → Blueprint tab → real controls → Generate | Paper row created from live UI interaction (not a unit-test shortcut) | [LIVE] + [DB] | PENDING | |
| G2 | Content matches configured blueprint | generated slots vs blueprint | Question count, marks, modules, difficulty distribution match | [DB] + [UI] review | PENDING | |
| G3 | Review and finalize work | `ReviewPaperDialog` → `ReviewPaperPanel.finalizeCurrentPaper` | Finalization transaction succeeds; status FINALIZED | [LIVE] + [DB] | PENDING | |
| G4 | PDF exports work | `PdfExportTest` + actual export path | Question paper + answer key PDFs produced with valid content | [STATE] suite + [UI] generated files inspected | PENDING | |
| G5 | Snapshot/history consistency | `question_papers` row + paper_questions | Finalized paper keeps its snapshot after source-question changes | [DB] + existing suite | PENDING | |
| G6 | History reopen + deletion | `GeneratedPapersPanel` delete semantics | Paper deletable; CASCADE removes its rows only; original data intact | [LIVE] + [DB] | PENDING | |
| G7 | Temporary test artifacts cleaned | before/after counts | Only test-created rows removed; subjects/modules/questions/papers baseline preserved | [DB] before/after | PENDING | |

## H. Additional checks discovered in source audit

| # | Check | Control / method | Expected | Verification | Result | Evidence |
|---|-------|------------------|----------|--------------|--------|----------|
| H1 | Tab navigation refreshes Blueprint on enter | `MainFrame.refreshScreen(3)` | State retained (subject/syllabus kept); deleted modules pruned | [LIVE] tab switch | PENDING | |
| H2 | Re-click active tab refreshes | `mousePressed` handler | Second click on active tab calls `refreshPanel` without wiping state | [LIVE] | PENDING | |
| H3 | Subject-change confirmation default does not destroy data | `applySubjectChange` YES_NO dialog | Default option semantics unchanged from baseline; user can cancel safely | [UI] dialog inspection | PENDING | |
| H4 | DB-down leaves UI usable | `loadSubjects`/`loadAllModules` catch blocks | Error message (or stderr at startup), no crash, controls consistent | [STATE] existing Stage 11 suite | PENDING | |
| H5 | Facade API surface unchanged for test suites | `git diff` on public methods | No public/package-visible method removed or renamed | [STATE] full 18-suite regression | PENDING | |
| H6 | Allocation editing cannot corrupt indices | `setValueAt` row→`syllabusIds().get(row)` | Row index valid at edit time; table rebuilt after every change | [LIVE] | PENDING | |
