import unittest
from release import version_for_run


class VersionTests(unittest.TestCase):
    def test_first_release_updates_existing_installation(self):
        self.assertEqual(("1.3.3", 11), version_for_run("versionName=1.3.3\nversionCode=11", 1))

    def test_rerun_has_identical_version_and_later_runs_increase(self):
        baseline = "versionName=1.3.3\nversionCode=11"
        self.assertEqual(version_for_run(baseline, 4), version_for_run(baseline, 4))
        self.assertEqual(("1.3.7", 15), version_for_run(baseline, 5))

    def test_invalid_run_is_rejected(self):
        with self.assertRaises(ValueError):
            version_for_run("versionName=1.3.3\nversionCode=11", 0)



class PublicationTests(unittest.TestCase):
    def setUp(self):
        from unittest.mock import patch
        from pathlib import Path
        self.patch = patch
        self.output = Path("release-output")
        self.metadata = {"versionName": "1.3.3", "versionCode": 11}
        self.context = patch("release.context", return_value=("owner/repo", "abc123"))
        self.assets = patch("release.prepare_assets", return_value=(self.output, self.metadata))
        self.context.start()
        self.assets.start()
        self.addCleanup(self.context.stop)
        self.addCleanup(self.assets.stop)

    def test_stale_commit_never_publishes(self):
        from release import publish
        with self.patch("release.is_head", return_value=False), self.patch("release.run") as command:
            publish()
            command.assert_not_called()

    def test_failed_upload_never_promotes_latest(self):
        from release import publish
        import subprocess
        with self.patch("release.is_head", return_value=True), self.patch("release.releases", return_value=[]):
            with self.patch("release.run", side_effect=["", subprocess.CalledProcessError(1, "upload")]) as command:
                with self.assertRaises(subprocess.CalledProcessError):
                    publish()
                self.assertFalse(any("edit" in call.args for call in command.call_args_list))

    def test_success_publishes_only_after_assets_uploaded(self):
        from release import publish
        with self.patch("release.is_head", return_value=True), self.patch("release.releases", return_value=[]):
            with self.patch("release.run", return_value="") as command:
                publish()
                self.assertEqual(["create", "upload", "edit"], [call.args[2] for call in command.call_args_list])
                self.assertIn("--latest", command.call_args.args)

    def test_published_rerun_does_not_modify_release(self):
        from release import publish
        existing = [{"tag_name": "v1.3.3", "target_commitish": "abc123", "draft": False}]
        with self.patch("release.is_head", return_value=True), self.patch("release.releases", return_value=existing):
            with self.patch("release.run") as command:
                publish()
                command.assert_not_called()

if __name__ == "__main__":
    unittest.main()
