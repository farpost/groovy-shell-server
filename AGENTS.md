# Repository instructions

## Release notes are part of the change

- Keep CalVer (`YYYY.MM.DD.HHMM`, UTC). Communicate compatibility through release notes; do not change the versioning scheme unless requested.
- For changes to runtime requirements, dependencies, public APIs, configuration, shell behavior or the release process, generate a new Markdown fragment under `release-notes/` in the same PR. Use a unique descriptive name, for example `2026-11-16-shell-modernization.md`.
- Derive the notes from the actual diff and verified behavior. Explain what changes for a consumer. Dependency upgrades must name the old and new major versions when they affect compatibility.
- Put incompatible changes under `### Breaking changes`, followed by concrete steps under `### Migration`. Cover minimum Java, Groovy/Spring/logging compatibility, public API changes, default scripts, shell commands/output and configuration semantics when affected. List compatible improvements separately under `### Improvements`.
- Do not infer compatibility from a successful build. State the relevant verification boundary, and keep the README migration guidance consistent with the notes. Do not claim unperformed runtime or packaged-application tests.
- Published fragments are immutable: add a new fragment for a correction or follow-up instead of editing/deleting an already released fragment. The generator selects only fragments introduced since the previous reachable CalVer release tag.
- Do not include credentials, private conversations or internal infrastructure addresses in public release notes.

## Automation and verification

- `CLAUDE.md` delegates to this file; keep the rules in one place.
- The publishing workflow generates curated notes from the exact source commit before deploying. After Maven Central confirms publication, it creates the matching GitHub tag and Release, adding GitHub's generated change list to the curated migration notes.
- Keep the Maven version, GitHub Release version and target commit aligned. A failed Maven publication must not create a successful GitHub Release.
- For release-automation changes, run `python3 -m unittest discover -s scripts -p 'test_*.py'` and check the workflow/Makefile integration. For Java/runtime changes, also run `./mvnw verify` on the supported JDK.

Existing code comments must be preserved. Keep changes scoped, and do not rewrite Git history.
