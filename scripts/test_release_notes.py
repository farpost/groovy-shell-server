"""Behavioral tests using real temporary Git histories; no network or third-party packages."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from release_notes import ReleaseNotesError, render_release_notes


SCRIPT = Path(__file__).with_name("release_notes.py").resolve()


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.repo = Path(self.directory.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "release-test@example.invalid")
        self.git("config", "user.name", "Release Test")
        self.git("commit", "-qm", "Initial", "--allow-empty")

    def git(self, *arguments):
        return subprocess.check_output(["git", *arguments], cwd=self.repo, stderr=subprocess.PIPE).decode().strip()

    def fragment(self, name, text):
        path = self.repo / "release-notes" / f"{name}.md"
        path.parent.mkdir(exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def commit(self, message="Change"):
        self.git("add", ".")
        self.git("commit", "-qm", message, "--allow-empty")
        return self.git("rev-parse", "HEAD")

    def render(self, version="2026.11.16.0306", commit=None):
        return render_release_notes(self.repo, version, commit or self.git("rev-parse", "HEAD"))

    def test_first_release_includes_all_curated_sections_and_identity(self):
        self.fragment("001-java", "## Breaking changes\n\nJava 25 is required.\n")
        self.fragment("002-loader", "## Improvements\n\nApplication loader is explicit.\n")
        commit = self.commit()
        notes = self.render(commit=commit)
        self.assertIn("## Breaking changes\n\nJava 25 is required.", notes)
        self.assertIn("## Improvements\n\nApplication loader is explicit.", notes)
        self.assertIn("com.farpost:groovy-shell:2026.11.16.0306", notes)
        self.assertIn(f"/commit/{commit}", notes)

    def test_next_release_excludes_consumed_fragments(self):
        self.fragment("001", "Old migration")
        self.commit()
        self.git("tag", "2026.11.15.1200")
        self.fragment("002", "New migration")
        self.commit()
        notes = self.render()
        self.assertIn("New migration", notes)
        self.assertNotIn("Old migration", notes)

    def test_exact_commit_ignores_dirty_tree_and_later_commits(self):
        path = self.fragment("001", "Committed migration")
        requested = self.commit()
        path.write_text("Later committed edit", encoding="utf-8")
        self.commit()
        path.write_text("Dirty edit", encoding="utf-8")
        self.fragment("002", "Untracked migration")
        notes = self.render(commit=requested)
        self.assertIn("Committed migration", notes)
        for excluded in ("Later committed edit", "Dirty edit", "Untracked migration"):
            self.assertNotIn(excluded, notes)

    def test_unrelated_newer_tag_does_not_become_baseline(self):
        self.fragment("001", "Consumed migration")
        self.commit()
        self.git("tag", "2026.11.14.1200")
        self.git("checkout", "-qb", "other")
        self.fragment("unrelated", "Other branch")
        self.commit()
        self.git("tag", "2026.11.15.1200")
        self.git("checkout", "-q", "main")
        self.fragment("002", "Main branch migration")
        self.commit()
        notes = self.render()
        self.assertIn("Main branch migration", notes)
        self.assertNotIn("Consumed migration", notes)
        self.assertNotIn("Other branch", notes)

    def test_annotated_prefixed_tag_and_retry_are_supported(self):
        self.fragment("001", "Consumed")
        self.commit()
        self.git("tag", "-a", "v2026.11.15.1200", "-m", "Release")
        self.fragment("002", "New migration")
        current = self.commit()
        original = self.render(commit=current)
        self.git("tag", "v2026.11.16.0306")
        self.assertEqual(original, self.render(commit=current))

    def test_modifying_or_deleting_released_fragment_fails(self):
        path = self.fragment("001", "Immutable")
        baseline = self.commit()
        self.git("tag", "2026.11.15.1200")
        for action in ("modify", "delete"):
            with self.subTest(action=action):
                self.git("reset", "--hard", baseline)
                if action == "modify":
                    path.write_text("Changed", encoding="utf-8")
                else:
                    path.unlink()
                self.commit()
                with self.assertRaisesRegex(ReleaseNotesError, "modified or deleted"):
                    self.render()

    def test_reverting_an_edit_does_not_hide_released_history_mutation(self):
        path = self.fragment("001", "Immutable")
        self.commit()
        self.git("tag", "2026.11.15.1200")
        path.write_text("Changed", encoding="utf-8")
        self.commit()
        path.write_text("Immutable", encoding="utf-8")
        self.commit()
        with self.assertRaisesRegex(ReleaseNotesError, "modified or deleted"):
            self.render()

    def test_reverted_edit_on_merged_branch_is_still_rejected(self):
        path = self.fragment("001", "Immutable")
        self.commit()
        self.git("tag", "2026.11.15.1200")
        self.git("checkout", "-qb", "feature")
        path.write_text("Changed on feature", encoding="utf-8")
        self.commit()
        path.write_text("Immutable", encoding="utf-8")
        self.commit()
        self.git("checkout", "-q", "main")
        self.commit("Main change")
        self.git("merge", "--no-ff", "-qm", "Merge feature", "feature")
        with self.assertRaisesRegex(ReleaseNotesError, "modified or deleted"):
            self.render()

    def test_no_new_fragments_does_not_claim_no_breaking_changes(self):
        self.fragment("001", "Consumed")
        self.commit()
        self.git("tag", "2026.11.15.1200")
        self.commit()
        notes = self.render()
        self.assertIn("No additional migration notes", notes)
        self.assertNotIn("No breaking", notes)
        self.assertNotIn("Consumed", notes)

    def test_shallow_clone_is_rejected_instead_of_repeating_old_fragments(self):
        self.fragment("001", "Previously released")
        self.commit()
        self.git("tag", "2026.11.15.1200")
        self.commit("Later change")
        clone_directory = tempfile.TemporaryDirectory()
        self.addCleanup(clone_directory.cleanup)
        clone = Path(clone_directory.name) / "clone"
        subprocess.check_call(
            ["git", "clone", "-q", "--depth", "1", self.repo.as_uri(), str(clone)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        with self.assertRaisesRegex(ReleaseNotesError, "complete Git history"):
            render_release_notes(clone, "2026.11.16.0306", self.git("rev-parse", "HEAD"))

    def test_invalid_version_and_commit_fail(self):
        for version in ("1.2.3", "2026.02.30.1200", "2026.11.16.2460", "2026.1.16.0306"):
            with self.subTest(version=version), self.assertRaises(ReleaseNotesError):
                self.render(version=version)
        for commit in ("HEAD", "--help", "abc123", "a" * 40):
            with self.subTest(commit=commit), self.assertRaises(ReleaseNotesError):
                self.render(commit=commit)

    def test_cli_writes_output_and_reports_validation_errors(self):
        self.fragment("001", "CLI migration")
        current = self.commit()
        output = self.repo / "notes.md"
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--version", "2026.11.16.0306", "--commit", current,
             "--output", str(output)], cwd=self.repo, capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.render(commit=current), output.read_text(encoding="utf-8"))
        failure = subprocess.run(
            [sys.executable, str(SCRIPT), "--version", "invalid", "--commit", current],
            cwd=self.repo, capture_output=True, text=True, check=False,
        )
        self.assertNotEqual(0, failure.returncode)
        self.assertIn("version must", failure.stderr)


if __name__ == "__main__":
    unittest.main()
