#!/usr/bin/env python3
"""Render release notes from committed, append-only Markdown fragments."""

from __future__ import annotations

import argparse
from datetime import datetime
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


VERSION_PATTERN = re.compile(r"[0-9]{4}\.[0-9]{2}\.[0-9]{2}\.[0-9]{4}\Z")
COMMIT_PATTERN = re.compile(r"(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})\Z")
REPOSITORY_URL = "https://github.com/farpost/groovy-shell-server"
MAVEN_URL = "https://central.sonatype.com/artifact/com.farpost/groovy-shell"


class ReleaseNotesError(ValueError):
    """The requested release or its fragment history is inconsistent."""


def validate_version(version: str) -> str:
    if not VERSION_PATTERN.fullmatch(version):
        raise ReleaseNotesError("version must have the form YYYY.MM.DD.HHMM")
    try:
        datetime.strptime(version, "%Y.%m.%d.%H%M")
    except ValueError as error:
        raise ReleaseNotesError(f"invalid release date: {version}") from error
    return version


def git(repo: Path, *arguments: str) -> bytes:
    result = subprocess.run(
        ["git", *arguments], cwd=repo, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=False,
    )
    if result.returncode:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise ReleaseNotesError(f"git {' '.join(arguments)} failed: {message}")
    return result.stdout


def resolve_commit(repo: Path, commit: str) -> str:
    if not COMMIT_PATTERN.fullmatch(commit):
        raise ReleaseNotesError("commit must be a full hexadecimal commit SHA")
    resolved = git(repo, "rev-parse", "--verify", f"{commit}^{{commit}}").decode().strip()
    if resolved != commit.lower():
        raise ReleaseNotesError("commit must identify a commit object directly")
    return resolved


def is_ancestor(repo: Path, ancestor: str, commit: str) -> bool:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, commit], cwd=repo,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if result.returncode not in (0, 1):
        raise ReleaseNotesError(result.stderr.decode("utf-8", errors="replace").strip())
    return result.returncode == 0


def release_baseline(repo: Path, version: str, commit: str) -> str | None:
    candidates = []
    tags = git(repo, "for-each-ref", "--format=%(refname:strip=2)", "refs/tags").decode().splitlines()
    for tag in tags:
        tag_version = tag.removeprefix("v")
        if not VERSION_PATTERN.fullmatch(tag_version):
            continue
        validate_version(tag_version)
        tagged_commit = git(repo, "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}").decode().strip()
        if tag_version == version:
            if tagged_commit != commit:
                raise ReleaseNotesError(f"release tag {tag} already identifies a different commit")
            # Re-running publication for this exact release must reproduce its original notes.
            continue
        if tag_version < version and is_ancestor(repo, tagged_commit, commit):
            candidates.append((tag_version, tagged_commit))
    if not candidates:
        return None
    return max(candidates)[1]


def fragments(repo: Path, commit: str) -> dict[str, str]:
    result = {}
    entries = git(repo, "ls-tree", "-r", "-z", "--full-tree", commit, "--", "release-notes")
    for entry in entries.split(b"\0"):
        if not entry:
            continue
        metadata, path_bytes = entry.split(b"\t", 1)
        path = path_bytes.decode("utf-8")
        relative = PurePosixPath(path)
        if len(relative.parts) != 2 or relative.suffix != ".md":
            continue
        mode, kind, blob = metadata.decode().split()
        if kind != "blob" or mode not in ("100644", "100755"):
            raise ReleaseNotesError(f"fragment must be a regular Markdown file: {path}")
        result[path] = blob
    return result


def check_released_fragments(repo: Path, baseline: str, commit: str,
                             released: dict[str, str], current: dict[str, str]) -> None:
    def check(snapshot: dict[str, str], revision: str) -> None:
        for path, blob in released.items():
            if snapshot.get(path) != blob:
                raise ReleaseNotesError(
                    f"released fragment {path} was modified or deleted at {revision}; "
                    "add a new fragment instead of rewriting release history"
                )

    check(current, commit)
    if not released:
        return
    # Also reject an edit that was later reverted. Only descendants of the released
    # baseline count: an unrelated branch merged later can predate the release.
    revisions = git(repo, "rev-list", "--ancestry-path", f"{baseline}..{commit}",
                    "--", *sorted(released)).decode().splitlines()
    for revision in revisions:
        check(fragments(repo, revision), revision)


def render_release_notes(repo: Path, version: str, commit: str) -> str:
    validate_version(version)
    if git(repo, "rev-parse", "--is-shallow-repository").strip() == b"true":
        raise ReleaseNotesError("complete Git history and release tags are required; fetch with depth 0 and tags")
    commit = resolve_commit(repo, commit)
    baseline = release_baseline(repo, version, commit)
    current = fragments(repo, commit)
    released = fragments(repo, baseline) if baseline else {}
    if baseline:
        check_released_fragments(repo, baseline, commit, released, current)
    sections = [
        f"# Release {version}",
        f"Maven: [`com.farpost:groovy-shell:{version}`]({MAVEN_URL}/{version}).",
        f"Commit: [`{commit}`]({REPOSITORY_URL}/commit/{commit}).",
    ]
    additions = sorted(current.keys() - released.keys())
    for path in additions:
        fragment = git(repo, "show", f"{commit}:{path}").decode("utf-8").strip()
        if not fragment:
            raise ReleaseNotesError(f"release note fragment is empty: {path}")
        sections.append(fragment)
    if not additions:
        sections.append("No additional migration notes; see the generated change list below.")
    return "\n\n".join(sections) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        notes = render_release_notes(Path.cwd(), args.version, args.commit)
        if args.output:
            args.output.write_text(notes, encoding="utf-8")
        else:
            sys.stdout.write(notes)
    except (ReleaseNotesError, OSError, UnicodeError) as error:
        parser.exit(1, f"error: {error}\n")


if __name__ == "__main__":
    main()
