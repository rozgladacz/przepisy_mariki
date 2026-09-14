"""GitHub release orchestration. No third-party Python dependencies."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_CERT = "51b3d764ea5e63e0b94d410adef716ba803526a4f629a32f04ce0d41f387893c"


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def version_for_run(properties, run_number):
    values = dict(line.split("=", 1) for line in properties.splitlines() if "=" in line)
    major, minor, patch = map(int, values["versionName"].split("."))
    if run_number < 1:
        raise ValueError("Run number must be positive")
    code = int(values["versionCode"]) + run_number - 1
    if not 10 < code < 2_100_000_000:
        raise ValueError("Invalid versionCode")
    return f"{major}.{minor}.{patch + run_number - 1}", code


def context():
    return os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_SHA"]


def releases(repo):
    pages = json.loads(run("gh", "api", "--paginate", "--slurp", f"repos/{repo}/releases?per_page=100"))
    return [release for page in pages for release in page]


def is_head(repo, sha):
    return run("gh", "api", f"repos/{repo}/git/ref/heads/main", "--jq", ".object.sha") == sha


def plan():
    repo, sha = context()
    name, code = version_for_run((ROOT / "version.properties").read_text(), int(os.environ["GITHUB_RUN_NUMBER"]))
    tag = f"v{name}"
    existing = next((r for r in releases(repo) if r["tag_name"] == tag), None)
    if existing and existing["target_commitish"] != sha:
        raise RuntimeError("Version already belongs to another commit")
    skip = not is_head(repo, sha) or bool(existing and not existing["draft"])
    with open(os.environ["GITHUB_OUTPUT"], "a") as out:
        out.write(f"version_name={name}\nversion_code={code}\ntag={tag}\nskip={str(skip).lower()}\n")


def prepare_assets():
    name, code = os.environ["VERSION_NAME"], int(os.environ["VERSION_CODE"])
    repo, sha = context()
    apk_files = list((ROOT / "app/build/outputs/apk/release").glob("*.apk"))
    if len(apk_files) != 1:
        raise RuntimeError("Expected exactly one release APK")
    apk = apk_files[0]
    if not 0 < apk.stat().st_size <= 25 * 1024 * 1024:
        raise RuntimeError("APK size exceeds limit")
    build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    cert = run(str(build_tools / "apksigner"), "verify", "--print-certs", str(apk))
    if f"Signer #1 certificate SHA-256 digest: {EXPECTED_CERT}" not in cert:
        raise RuntimeError("APK signing certificate does not match installed versions")
    badging = run(str(build_tools / "aapt"), "dump", "badging", str(apk))
    expected = f"package: name='pl.local.przepisy' versionCode='{code}' versionName='{name}'"
    if not badging.startswith(expected) or "sdkVersion:'33'" not in badging:
        raise RuntimeError("APK identity/version/minSdk mismatch")
    output = ROOT / "release-output"
    output.mkdir(exist_ok=True)
    shutil.copyfile(apk, output / "Przepisy.apk")
    notes = (ROOT / "release-notes.md").read_text(encoding="utf-8").strip()
    if len(notes) > 8000:
        raise RuntimeError("Release notes too long")
    metadata = dict(
        schemaVersion=1, applicationId="pl.local.przepisy", versionCode=code,
        versionName=name, minSdk=33,
        apkUrl=f"https://github.com/{repo}/releases/download/v{name}/Przepisy.apk",
        sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), notes=notes,
    )
    (output / "update.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS").write_text(f"{metadata['sha256']}  Przepisy.apk\n")
    return output, metadata


def publish():
    repo, sha = context()
    output, metadata = prepare_assets()
    tag = f"v{metadata['versionName']}"
    # Recheck after the build: an older job must never replace a newer main.
    if not is_head(repo, sha):
        print("Main has advanced; skipping publication.")
        return
    known = releases(repo)
    existing = next((r for r in known if r["tag_name"] == tag), None)
    if existing:
        if existing["target_commitish"] != sha:
            raise RuntimeError("Tag belongs to another commit")
        if not existing["draft"]:
            print("This release is already published.")
            return
    # Enforce monotonic Android versions even if the workflow is recreated.
    for release in known:
        if release["draft"] or release["prerelease"]:
            continue
        asset = next((a for a in release["assets"] if a["name"] == "update.json"), None)
        if asset:
            old = json.loads(run("gh", "api", "-H", "Accept: application/octet-stream", asset["url"]))
            if int(old["versionCode"]) >= metadata["versionCode"]:
                raise RuntimeError("A published release already has this or a higher versionCode")
    if not existing:
        run("gh", "release", "create", tag, "--repo", repo, "--target", sha,
            "--draft", "--title", f"Przepisy {metadata['versionName']}",
            "--notes-file", str(ROOT / "release-notes.md"))
    run("gh", "release", "upload", tag, "--repo", repo, "--clobber",
        str(output / "Przepisy.apk"), str(output / "update.json"), str(output / "SHA256SUMS"))
    if not is_head(repo, sha):
        print("Main has advanced; leaving this release as a draft.")
        return
    run("gh", "release", "edit", tag, "--repo", repo, "--draft=false", "--latest")
    print(f"Published https://github.com/{repo}/releases/tag/{tag}")


if __name__ == "__main__":
    {"plan": plan, "publish": publish}[sys.argv[1]]()
