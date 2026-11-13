"""Failure-path checks for the opt-in unpublished-release recovery operation."""

import importlib.util
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location("recovery", Path(__file__).with_name("recover-release-tag.py"))
recovery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recovery)
OLD, SOURCE, NEW = "a" * 40, "b" * 40, "c" * 40
REF = "refs/tags/v0.3.6"
ENV = dict(GITHUB_REPOSITORY="owner/reader", EXPECTED_VERSION="0.3.6",
           RECOVER_TAG_OBJECT=OLD, GITHUB_SHA=SOURCE, GH_TOKEN="test-token",
           GITHUB_EVENT_NAME="workflow_dispatch")


class RecoveryTest(unittest.TestCase):
    def test_only_a_confirmed_404_allows_recovery(self):
        for status in (401, 403, 404, 429, 500):
            with self.subTest(status=status), patch.object(recovery, "urlopen", side_effect=HTTPError("url", status, "error", {}, None)):
                if status == 404:
                    recovery.require_unpublished("owner/reader", "v0.3.6", "test-token")
                else:
                    with self.assertRaises(HTTPError):
                        recovery.require_unpublished("owner/reader", "v0.3.6", "test-token")

    def test_existing_release_is_rejected(self):
        with patch.object(recovery, "urlopen"), patch.object(recovery.json, "load", return_value={"draft": True}):
            with self.assertRaises(ValueError):
                recovery.require_unpublished("owner/reader", "v0.3.6", "test-token")

    def test_invalid_inputs_never_touch_git(self):
        for key, value in (("GITHUB_EVENT_NAME", "push"), ("RECOVER_TAG_OBJECT", "main"),
                           ("GITHUB_SHA", "-invalid"), ("EXPECTED_VERSION", "0.3.6/other"),
                           ("GITHUB_REPOSITORY", "../other/repo")):
            with self.subTest(key=key), patch.object(recovery, "git") as git:
                with self.assertRaises(ValueError):
                    recovery.recover(ENV | {key: value})
                git.assert_not_called()

    def test_source_mismatch_is_rejected(self):
        with patch.object(recovery, "git", return_value=OLD) as git:
            with self.assertRaises(ValueError):
                recovery.recover(ENV)
            self.assertEqual(1, git.call_count)

    def test_moved_tag_is_rejected_before_fetch(self):
        with patch.object(recovery, "require_unpublished"), patch.object(recovery, "git", side_effect=[SOURCE, f"{NEW}\t{REF}"]) as git:
            with self.assertRaises(ValueError):
                recovery.recover(ENV)
            self.assertEqual(2, git.call_count)

    def test_fetch_race_and_unrelated_history_never_push(self):
        for suffix in (("", NEW), ("", OLD, subprocess.CalledProcessError(1, "git merge-base"))):
            with self.subTest(suffix=suffix), patch.object(recovery, "require_unpublished"), patch.object(recovery, "git", side_effect=[SOURCE, f"{OLD}\t{REF}", *suffix]) as git:
                with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                    recovery.recover(ENV)
                self.assertFalse(any(call.args[0] == "push" for call in git.call_args_list))

    def test_success_uses_exact_lease_and_rechecks_remote(self):
        with patch.object(recovery, "require_unpublished") as unpublished, patch.object(recovery, "git", side_effect=[SOURCE, f"{OLD}\t{REF}", "", OLD, "", "", NEW, "", f"{NEW}\t{REF}"]) as git:
            recovery.recover(ENV)
            self.assertEqual(2, unpublished.call_count)
            git.assert_any_call("push", f"--force-with-lease={REF}:{OLD}", "origin", f"{REF}:{REF}")
            self.assertEqual(("ls-remote", "--refs", "origin", REF), git.call_args.args)


if __name__ == "__main__":
    unittest.main()
