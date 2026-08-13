# Code Reviews

This directory contains dated, repository-wide and scoped code-review reports.

## Reviews

- [2026-08-12 comprehensive code review](2026-08-12-comprehensive-code-review.md). This covers the full maintained implementation at revision `98ee7b4`, including the engine, modern and classic loaders, bridge adapters, safety, tests, and UX.
- [2026-08-12 review response](2026-08-12-review-response.md). Tracks what was fixed, fixed in a measured form, mitigated, or deferred against the findings above.
- [2026-08-13 follow-up comprehensive code review](2026-08-13-follow-up-code-review.md). Re-reviews the full implementation at revision `ef4b522`, verifies Claude's fixes and response, records remaining safety and correctness gaps, and includes a focused UX follow-up. E-stim and schema migration are treated as non-blocking beta decisions.
- [2026-08-13 follow-up review response](2026-08-13-follow-up-review-response.md). Tracks what was fixed and tested against the follow-up findings, and the one item left as a labelled beta limitation.
- [2026-08-13 second follow-up comprehensive code review](2026-08-13-second-follow-up-code-review.md). Re-reviews the full implementation at revision `2620bc9`, checks Claude's second fix pass and response, and records the remaining safety, architecture, adapter, parser, and UX work. E-stim and beta schema migration remain non-blocking, while final-state architecture is treated as required before release.

## Convention

- Use `YYYY-MM-DD-<scope>-code-review.md` for new reports.
- Update an existing report only while refining the same review against the same implementation.
- Create a new dated report after substantial code changes or a fresh review pass.
- Keep findings prioritized and record the reviewed revision, scope, verification performed, and testing limitations.
- Preserve older reports as historical snapshots; track resolved findings in the newer report instead of rewriting history.
