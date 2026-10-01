#!/usr/bin/env python3
"""Fail-closed leak and boundary guard for the public source tree."""

from __future__ import annotations

import argparse
import os
import re
import stat
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ROOT_FILES = {
    ".gitignore",
    "CHANGELOG.md",
    "CONTRIBUTING.md",
    "LICENSE",
    "NOTICE",
    "README.md",
    "SECURITY.md",
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
}
GITHUB_FILES = {
    ".github/ISSUE_TEMPLATE/bug.yml",
    ".github/ISSUE_TEMPLATE/compatibility.yml",
    ".github/ISSUE_TEMPLATE/config.yml",
    ".github/ISSUE_TEMPLATE/feature.yml",
    ".github/public_source_guard.py",
    ".github/workflows/public-source-guard.yml",
}
RESOURCE_SUFFIXES = {".json", ".png", ".mcmeta", ".toml", ".txt"}
PRIVATE_SOURCE_NAMES = {
    "AscensionDebugSuite.java",
    "QualificationMod.java",
    "RuntimeQualificationChecks.java",
    "RuntimeSelfTest.java",
    "ClientRuntimeSelfTest.java",
}
FORBIDDEN_PATH_PARTS = {
    "internal",
    "third_party",
    ".idea",
    ".vscode",
    ".gradle",
    "build",
}
FORBIDDEN_CONTENT = (
    b"C:" + b"\\Lab\\",
    b"C:" + b"/Lab/",
    b"Lab" + b"Agent",
    b"SOURCE_" + b"PROVENANCE.md",
    b"provider-" + b"provenance",
    b"tools/" + b"qualification",
    b"internal/" + b"qualification",
    b"github.com/Mister-Cheese/" + b"Apothic-Ascension.git",
    b"github.com/Mister-Cheese/" + b"Apothic-Ascension/",
    b"apothic_ascension." + b"runtimeSelfTest",
    b"apothic_ascension." + b"clientSelfTest",
    b"READY_FOR_" + b"RELOAD",
)
SECRET_PATTERNS = (
    (re.compile(rb"gh[pousr]_[A-Za-z0-9]{20,}"), "GitHub token"),
    (re.compile(rb"AKIA[0-9A-Z]{16}"), "AWS access key"),
    (re.compile(rb"xox[baprs]-[A-Za-z0-9-]{20,}"), "Slack token"),
    (re.compile(rb"sk-proj-[A-Za-z0-9_-]{20,}"), "OpenAI project key"),
    (re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"), "private key"),
)
ABSOLUTE_PATH_PATTERNS = (
    re.compile(rb"(?i)[A-Z]:\\(?:Users|Lab|ProgramData)\\[^\r\n\t]+"),
    re.compile(rb"/(?:home|Users)/[A-Za-z0-9._-]+/"),
)
PRIVATE_JAR_ENTRIES = (
    "AscensionDebugSuite",
    "QualificationMod",
    "RuntimeQualificationChecks",
    "RuntimeSelfTest",
    "ClientRuntimeSelfTest",
)


class GuardError(RuntimeError):
    pass


def git(*args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    ).stdout.strip()


def tracked_files() -> list[str]:
    output = subprocess.run(
        ["git", "-C", str(ROOT), "ls-files", "-z"],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    return [entry.decode("utf-8") for entry in output.split(b"\0") if entry]


def allowed_path(rel: str) -> bool:
    if rel in ROOT_FILES or rel in GITHUB_FILES:
        return True
    p = Path(rel)
    parts = p.parts
    if len(parts) < 4 or parts[:2] != ("src", "main"):
        return False
    section = parts[2]
    if section == "java":
        return p.suffix == ".java"
    if section == "resources":
        return p.suffix in RESOURCE_SUFFIXES
    if section == "templates":
        return p.suffix == ".toml"
    return False


def scan_file(rel: str, path: Path) -> None:
    if any(part in FORBIDDEN_PATH_PARTS for part in Path(rel).parts):
        raise GuardError(f"forbidden path component: {rel}")
    if not allowed_path(rel):
        raise GuardError(f"path is outside the public allowlist: {rel}")
    if path.is_symlink() or stat.S_ISLNK(path.lstat().st_mode):
        raise GuardError(f"symlink is forbidden: {rel}")
    if rel.startswith("src/main/java/") and path.name in PRIVATE_SOURCE_NAMES:
        raise GuardError(f"private qualification source present: {rel}")

    data = path.read_bytes()
    for marker in FORBIDDEN_CONTENT:
        if marker in data:
            raise GuardError(f"internal marker {marker!r} found in {rel}")
    for pattern, label in SECRET_PATTERNS:
        if pattern.search(data):
            raise GuardError(f"{label} pattern found in {rel}")
    for pattern in ABSOLUTE_PATH_PATTERNS:
        if pattern.search(data):
            raise GuardError(f"machine-specific absolute path found in {rel}")


def verify_history() -> None:
    if os.environ.get("AA_REQUIRE_ISOLATED_PREVIEW_HISTORY", "").lower() not in {"1", "true", "yes"}:
        return

    commits = [line for line in git("rev-list", "HEAD").splitlines() if line]
    roots = [line for line in git("rev-list", "--max-parents=0", "HEAD").splitlines() if line]
    if len(roots) != 1:
        raise GuardError(f"Public-Preview must have exactly one root commit; roots={len(roots)}")
    root_parts = git("rev-list", "--parents", "-n", "1", roots[0]).split()
    if len(root_parts) != 1:
        raise GuardError("Public-Preview root unexpectedly has a parent")

    if len(commits) == 1:
        return
    if len(commits) != 2:
        raise GuardError(
            "Public-Preview may contain only the sanitized root plus one workflow-only commit; "
            f"history_count={len(commits)}"
        )

    workflow = ".github/workflows/public-source-guard.yml"
    changed = [
        line for line in git("diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD^", "HEAD").splitlines()
        if line
    ]
    if changed != [workflow]:
        raise GuardError(
            "the only permitted second Public-Preview commit is the CI workflow; "
            f"changed={changed}"
        )


def verify_jar(path: Path) -> None:
    if not path.is_file():
        raise GuardError(f"runtime JAR not found: {path}")
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        leaked = sorted(
            name for name in names
            if any(token in name for token in PRIVATE_JAR_ENTRIES)
        )
        if leaked:
            raise GuardError("private qualification classes leaked into runtime JAR: " + ", ".join(leaked[:20]))
        for required in ("META-INF/source-code.txt", "META-INF/licenses/apothic_ascension.txt"):
            if required not in names:
                raise GuardError(f"runtime JAR missing MPL publication notice: {required}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()

    verify_history()
    files = tracked_files()
    if not files:
        raise GuardError("public tree contains no tracked files")

    for rel in files:
        path = ROOT / rel
        if not path.is_file():
            raise GuardError(f"tracked entry is not a regular file: {rel}")
        scan_file(rel, path)

    java_count = sum(1 for rel in files if rel.startswith("src/main/java/") and rel.endswith(".java"))
    if java_count == 0:
        raise GuardError("public tree contains no Java production source")

    if args.jar is not None:
        verify_jar(args.jar.resolve())

    print(f"PUBLIC_SOURCE_GUARD_PASS files={len(files)} java_sources={java_count}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (GuardError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f"PUBLIC_SOURCE_GUARD_FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
