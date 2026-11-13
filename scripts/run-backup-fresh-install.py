#!/usr/bin/env python3
"""Exercise a real QA uninstall/reinstall between production backup export and restore.

Only com.lightreader.app.qa and its test package may be installed/uninstalled. This script
never changes device authorization/security settings and never touches production data.
Both APKs must already have been built from the source under test. It performs no Gradle work.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
import zipfile

QA = "com.lightreader.app.qa"
TEST = "com.lightreader.app.qa.test"
PRODUCTION = "com.lightreader.app"
TEST_CLASS = "com.lightreader.app.BackupFreshInstallInstrumentedTest"
PHASES = ("prepareArchiveForFreshInstall", "verifyRestoreInFreshInstall")


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256")
    return digest.hexdigest()


def utc() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def find_aapt(explicit: str | None) -> str:
    if explicit:
        candidate = Path(explicit)
        if candidate.is_file():
            return str(candidate.resolve())
        resolved = shutil.which(explicit)
        if resolved:
            return resolved
        raise RuntimeError(f"aapt not found: {explicit}")
    sdk_roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    if os.environ.get("LOCALAPPDATA"):
        sdk_roots.append(str(Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"))
    names = ("aapt.exe", "aapt")
    for sdk in filter(None, sdk_roots):
        build_tools = Path(sdk) / "build-tools"
        for version in sorted(build_tools.glob("*"), reverse=True):
            for name in names:
                candidate = version / name
                if candidate.is_file():
                    return str(candidate)
    resolved = shutil.which("aapt")
    if resolved:
        return resolved
    raise RuntimeError("aapt is required to verify both APK package identities before installation; pass --aapt or set ANDROID_HOME.")


class Run:
    def __init__(self, args: argparse.Namespace, root: Path):
        self.args = args
        self.root = root
        self.run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "_" + uuid.uuid4().hex[:10]
        self.report = args.report_root.resolve() / self.run_id
        self.report.mkdir(parents=True, exist_ok=False)
        self.commands = self.report / "commands.jsonl"
        self.result: dict = {"runId": self.run_id, "startedUtc": utc(), "serial": args.serial,
                             "qaPackage": QA, "testPackage": TEST, "phases": [], "tests": 0,
                             "failures": 0, "errors": 0, "skipped": 0, "succeeded": False}

    def command(self, argv: list[str], name: str, *, input_bytes: bytes | None = None,
                binary_path: Path | None = None, discard_stdout: bool = False,
                check: bool = True, timeout: int = 180) -> subprocess.CompletedProcess:
        # No host shell is involved. The remote commands use fixed package names and validated paths.
        started = utc()
        output = binary_path.open("wb") if binary_path else (subprocess.DEVNULL if discard_stdout else subprocess.PIPE)
        try:
            process = subprocess.run(argv, input=input_bytes, stdout=output, stderr=subprocess.PIPE, timeout=timeout, check=False)
        except subprocess.TimeoutExpired as error:
            partial_stdout = error.stdout or b""
            partial_stderr = error.stderr or b""
            (self.report / f"{name}.stdout.txt").write_bytes(partial_stdout)
            (self.report / f"{name}.stderr.txt").write_bytes(partial_stderr)
            with self.commands.open("a", encoding="utf-8") as log:
                log.write(json.dumps({"at": started, "argv": argv, "timedOut": True,
                                      "timeoutSeconds": timeout, "stdoutBytes": len(partial_stdout),
                                      "stderrBytes": len(partial_stderr)}, ensure_ascii=False) + "\n")
            if name.startswith("phase-"):
                self.result["phases"].append({"method": name.removeprefix("phase-"), "expectedTests": 1,
                                             "tests": 0, "failures": 0, "errors": 1, "passed": False, "timedOut": True})
                self.result["errors"] += 1
                # Killing the host adb client does not reliably stop Android instrumentation.
                # Stop only the fixed QA target before another host run can start a new phase.
                cleanup = {"package": QA, "timeoutSeconds": 10, "completed": False}
                try:
                    stopped = self.adb("shell", "am", "force-stop", QA,
                                       name=f"timeout-force-stop-{name}", check=False, timeout=10)
                    cleanup.update({"exitCode": stopped.returncode, "completed": stopped.returncode == 0})
                except Exception as cleanup_error:
                    cleanup["error"] = str(cleanup_error)
                self.result["timeoutCleanup"] = cleanup
            raise RuntimeError(f"{name} timed out after {timeout}s; partial output is retained") from error
        finally:
            if binary_path:
                output.close()
        stdout = b"" if binary_path or discard_stdout else process.stdout
        stderr = process.stderr or b""
        (self.report / f"{name}.stdout.txt").write_bytes(stdout)
        (self.report / f"{name}.stderr.txt").write_bytes(stderr)
        with self.commands.open("a", encoding="utf-8") as log:
            log.write(json.dumps({"at": started, "argv": argv, "exitCode": process.returncode,
                                  "stdoutBytes": binary_path.stat().st_size if binary_path else len(stdout),
                                  "stderrBytes": len(stderr)}, ensure_ascii=False) + "\n")
        if check and process.returncode:
            raise RuntimeError(f"{name} exited {process.returncode}; see retained stdout/stderr in {self.report}")
        return process

    def adb(self, *parts: str, name: str, **kwargs) -> subprocess.CompletedProcess:
        return self.command([self.args.adb, "-s", self.args.serial, *parts], name, **kwargs)

    def pull_private(self, filename: str) -> Path:
        if filename not in {"reader-backup.zip", "expected.json", "prepare-result.json", "verify-result.json"}:
            raise ValueError("Unexpected private evidence filename")
        target = self.report / filename
        self.adb("exec-out", "run-as", QA, "cat", f"files/backup-fresh-install/{self.run_id}/{filename}",
                 name=f"extract-{filename.replace('.', '-')}", binary_path=target)
        if not target.stat().st_size:
            raise RuntimeError(f"Empty evidence file: {filename}")
        return target

    def push_private(self, filename: str) -> None:
        if filename not in {"reader-backup.zip", "expected.json"}:
            raise ValueError("Unexpected restore input filename")
        target = self.report / filename
        remote = f"files/backup-fresh-install/{self.run_id}"
        remote_file = f"{remote}/{filename}"
        if not re.fullmatch(r"files/backup-fresh-install/[A-Za-z0-9_-]{8,80}/(?:reader-backup\.zip|expected\.json)", remote_file):
            raise ValueError("Unexpected QA restore input path")
        self.adb("shell", "run-as", QA, "mkdir", "-p", remote, name=f"mkdir-{filename.replace('.', '-')}")
        # Windows adb may treat stdin as text and stop at a raw 0x1a. Send ASCII only,
        # then decode inside the fixed QA sandbox. The quoted shell path is allowlisted above.
        encoded = base64.b64encode(target.read_bytes()) + b"\n"
        self.adb("shell", "-T", "run-as", QA, "sh", "-c", f"'base64 -d > {remote_file}'",
                 name=f"return-{filename.replace('.', '-')}", input_bytes=encoded, discard_stdout=True)
        check_file = self.report / f"returned-{filename}"
        self.adb("exec-out", "run-as", QA, "cat", f"{remote}/{filename}",
                 name=f"check-return-{filename.replace('.', '-')}", binary_path=check_file)
        if sha256(check_file) != sha256(target):
            raise RuntimeError(f"Device roundtrip changed {filename}")

    def install(self, apk: Path, package: str, phase: str) -> None:
        if package not in (QA, TEST):
            raise ValueError("Only the two fixed QA packages can be installed")
        output = self.adb("install", "-r", "-t", str(apk), name=f"{phase}-install-{package}", timeout=180)
        if "Success" not in output.stdout.decode("utf-8", errors="replace"):
            raise RuntimeError(f"Installation of {package} did not report success")

    def uninstall_qa(self) -> None:
        # Keep this literal allowlist adjacent to the destructive operation.
        for package in (TEST, QA):
            if package not in ("com.lightreader.app.qa", "com.lightreader.app.qa.test"):
                raise ValueError("Refusing to uninstall a non-QA package")
            output = self.adb("uninstall", package, name=f"fresh-uninstall-{package}")
            if "Success" not in output.stdout.decode("utf-8", errors="replace"):
                raise RuntimeError(f"QA uninstall did not succeed: {package}")
            absent = self.adb("shell", "pm", "path", package, name=f"absence-{package}", check=False)
            if "package:" in absent.stdout.decode("utf-8", errors="replace"):
                raise RuntimeError(f"Package is still installed after uninstall: {package}")

    def phase(self, method: str) -> None:
        output = self.adb("shell", "am", "instrument", "-w", "-e", "clearPackageData", "false",
                          "-e", "freshInstallRunId", self.run_id, "-e", "runId", self.run_id,
                          "-e", "class", f"{TEST_CLASS}#{method}", f"{TEST}/androidx.test.runner.AndroidJUnitRunner",
                          name=f"phase-{method}", check=False, timeout=self.args.phase_timeout)
        combined = (output.stdout + b"\n" + output.stderr).decode("utf-8", errors="replace")
        ok = output.returncode == 0 and bool(re.search(r"\bOK\s*\(1 test\)", combined))
        ok = ok and not re.search(r"FAILURES|INSTRUMENTATION_FAILED|Process crashed|shortMsg=", combined)
        failure_counts = re.search(r"Tests run:\s*(\d+),\s*Failures:\s*(\d+)", combined)
        executed = 1 if ok else int(failure_counts.group(1)) if failure_counts else 0
        failures = 0 if ok else int(failure_counts.group(2)) if failure_counts else 0
        errors = int(not ok and not failure_counts)
        phase = {"method": method, "expectedTests": 1, "tests": executed, "failures": failures,
                 "errors": errors, "passed": ok, "stdoutSha256": hashlib.sha256(output.stdout).hexdigest()}
        self.result["phases"].append(phase)
        self.result["tests"] += executed
        self.result["failures"] += failures
        self.result["errors"] += errors
        if not ok:
            raise RuntimeError(f"{method} did not report exactly OK (1 test); see retained phase output")

    def execute(self) -> None:
        qa_apk, test_apk = self.args.qa_apk.resolve(), self.args.test_apk.resolve()
        for apk in (qa_apk, test_apk):
            if not apk.is_file():
                raise RuntimeError(f"Build the current QA and QA AndroidTest APKs first: {apk}")
        aapt = find_aapt(self.args.aapt)
        self.result["artifacts"] = []
        for apk, expected in ((qa_apk, QA), (test_apk, TEST)):
            badging = self.command([aapt, "dump", "badging", str(apk)], f"identity-{expected}").stdout.decode("utf-8", errors="replace")
            match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
            if not match or match.group(1) != expected:
                raise RuntimeError(f"Refusing APK whose package is not exactly {expected}: {apk}")
            self.result["artifacts"].append({"path": str(apk), "package": expected, "sha256": sha256(apk), "bytes": apk.stat().st_size})
        xml = self.command([aapt, "dump", "xmltree", str(test_apk), "AndroidManifest.xml"], "test-target-manifest").stdout.decode("utf-8", errors="replace")
        if not re.search(r'android:targetPackage[^\n]*="' + re.escape(QA) + r'"', xml):
            raise RuntimeError("The instrumentation APK does not target the isolated QA package")
        state = self.adb("get-state", name="device-state").stdout.decode().strip()
        if state != "device":
            raise RuntimeError("The selected device is not already online and authorized")
        device = {}
        for prop in ("ro.product.model", "ro.build.fingerprint", "ro.build.version.sdk", "ro.kernel.qemu"):
            device[prop] = self.adb("shell", "getprop", prop, name="device-" + prop).stdout.decode().strip()
        self.result["device"] = device
        self.result["startedSource"] = self.source_identity()
        production_before = self.adb("shell", "pm", "path", PRODUCTION, name="production-path-before", check=False).stdout
        self.result["productionPathBefore"] = production_before.decode().strip()
        self.install(qa_apk, QA, "prepare")
        self.install(test_apk, TEST, "prepare")
        self.phase(PHASES[0])
        archive = self.pull_private("reader-backup.zip")
        expected = self.pull_private("expected.json")
        prepare = json.loads(self.pull_private("prepare-result.json").read_text(encoding="utf-8"))
        if prepare.get("runId") != self.run_id or prepare.get("books") != 3 or prepare.get("passed") is not True:
            raise RuntimeError("Prepare evidence does not describe this successful three-book fixture")
        archive_sha = sha256(archive)
        if prepare.get("archiveSha256") != archive_sha or json.loads(expected.read_text(encoding="utf-8")).get("archiveSha256") != archive_sha:
            raise RuntimeError("Export archive checksum differs from the prepare evidence")
        with zipfile.ZipFile(archive) as zip_file:
            if zip_file.testzip() is not None:
                raise RuntimeError("Exported ZIP integrity check failed")
            manifest = json.loads(zip_file.read("manifest.json"))
            if manifest.get("archiveVersion") != 1 or {book["format"] for book in manifest["books"]} != {"TXT", "EPUB", "WEB"}:
                raise RuntimeError("Unexpected backup version or format coverage")
            if len(manifest["books"]) != 3:
                raise RuntimeError("Expected exactly three synthetic books")
        self.result["backup"] = {"path": str(archive), "sha256": archive_sha, "bytes": archive.stat().st_size,
                                 "archiveVersion": 1, "books": 3, "formats": ["TXT", "EPUB", "WEB"]}
        self.adb("shell", "am", "force-stop", QA, name="prepare-force-stop")
        self.uninstall_qa()
        self.result["freshUninstallVerified"] = True
        self.install(qa_apk, QA, "fresh")
        self.install(test_apk, TEST, "fresh")
        self.push_private("reader-backup.zip")
        self.push_private("expected.json")
        self.phase(PHASES[1])
        verification = json.loads(self.pull_private("verify-result.json").read_text(encoding="utf-8"))
        if (verification.get("runId") != self.run_id or verification.get("passed") is not True
                or verification.get("books") != 3 or verification.get("readerBooksOpened") != 3
                or verification.get("duplicateRestoreCount") != 0 or verification.get("aiConfigured") is not False
                or verification.get("archiveSha256") != archive_sha):
            raise RuntimeError("Restore evidence is incomplete or belongs to a different run")
        production_after = self.adb("shell", "pm", "path", PRODUCTION, name="production-path-after", check=False).stdout
        self.result["productionPathAfter"] = production_after.decode().strip()
        if production_before != production_after:
            raise RuntimeError("Production installation path changed during this isolated QA run")
        for artifact in self.result["artifacts"]:
            if sha256(Path(artifact["path"])) != artifact["sha256"]:
                raise RuntimeError("An APK changed during the two-phase test")
        self.result["completedSource"] = self.source_identity()
        if self.result["startedSource"] != self.result["completedSource"]:
            raise RuntimeError("Source changed during the two-phase test; rebuild and rerun a coherent source snapshot")
        self.result["succeeded"] = self.result["tests"] == 2 and len(self.result["phases"]) == 2 and all(p["passed"] for p in self.result["phases"])

    def source_identity(self) -> dict:
        head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=self.root, capture_output=True, check=True).stdout.decode().strip()
        paths = subprocess.run(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
                               cwd=self.root, capture_output=True, check=True).stdout.decode("utf-8").split("\0")
        entries = []
        suffixes = {".kt", ".kts", ".xml", ".toml", ".py", ".ps1", ".sh", ".yml", ".yaml"}
        for relative in sorted(set(filter(None, paths))):
            path = self.root / relative
            if path.is_file() and (path.suffix in suffixes or path.name == "gradle.properties"):
                entries.append(f"{relative} {sha256(path)}")
        digest = hashlib.sha256("\n".join(entries).encode()).hexdigest()
        (self.report / "source-files.sha256").write_text("\n".join(entries) + "\n", encoding="utf-8")
        return {"head": head, "sourceTreeSha256": digest}


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="An already-authorized isolated device or emulator serial")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--aapt", help="Path to Android SDK aapt for mandatory APK identity validation")
    parser.add_argument("--qa-apk", type=Path, default=root / "app/build/outputs/apk/qa/app-qa.apk")
    parser.add_argument("--test-apk", type=Path, default=root / "app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk")
    parser.add_argument("--report-root", type=Path, default=root / "build/reports/backup-fresh-install")
    parser.add_argument("--phase-timeout", type=int, default=180)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.:-]+", args.serial):
        parser.error("Invalid device serial")
    if not 30 <= args.phase_timeout <= 600:
        parser.error("--phase-timeout must be between 30 and 600 seconds")
    run = Run(args, root)
    try:
        run.execute()
    except Exception as error:
        run.result["failure"] = str(error)
        if not run.result["failures"] and not run.result["errors"]:
            run.result["errors"] += 1
        (run.report / "failure.txt").write_text(f"{type(error).__name__}: {error}\n", encoding="utf-8")
    finally:
        run.result["completedUtc"] = utc()
        (run.report / "result.json").write_text(json.dumps(run.result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"succeeded": run.result["succeeded"], "tests": run.result["tests"],
                      "failures": run.result["failures"], "errors": run.result["errors"], "report": str(run.report)}, ensure_ascii=False))
    return 0 if run.result["succeeded"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
