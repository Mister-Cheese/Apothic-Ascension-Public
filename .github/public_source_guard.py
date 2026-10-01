#!/usr/bin/env python3
"""Validate the public source boundary using positive structure and generic leak checks."""

from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import os
import re
import stat
import struct
import subprocess
import sys
import tomllib
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ".github/public-source-manifest.json"
MAX_FILE_SIZE = 16 * 1024 * 1024
MAX_ARCHIVE_ENTRY_SIZE = 64 * 1024 * 1024
MAX_ARCHIVE_TOTAL_SIZE = 128 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 4096
MAX_PNG_RASTER_SIZE = 64 * 1024 * 1024
EXPECTED_CLASS_MAJOR = 65  # Java 21, matching the public build toolchain.
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
LEGACY_HISTORY_BASELINE = "a34ca785e007fc66f978a7d498b66a6099c8cec9"

# Boundary note:
# This guard is public, so every explicit rule reveals a small amount about the boundary it checks.
# That mapping risk is acknowledged, minor, and intentional. Prefer positive structure and generic
# integrity checks over named deny rules. Older public revisions exposed a few internal names; those
# disclosures cannot be undone, so new checks avoid repeating or expanding that map unless needed.
ROOT_FILES = {
    ".gitattributes",
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
    ".github/FUNDING.yml",
    ".github/ISSUE_TEMPLATE/bug.yml",
    ".github/ISSUE_TEMPLATE/compatibility.yml",
    ".github/ISSUE_TEMPLATE/config.yml",
    ".github/ISSUE_TEMPLATE/feature.yml",
    ".github/public_source_guard.py",
    MANIFEST_PATH,
    ".github/workflows/public-source-guard.yml",
}
RESOURCE_SUFFIXES = {".json", ".png", ".mcmeta", ".toml", ".txt"}
TEXT_SUFFIXES = {
    ".gradle", ".java", ".json", ".mcmeta", ".md", ".properties",
    ".py", ".toml", ".txt", ".yml", ".yaml",
}
PROSE_PATHS = {
    "CHANGELOG.md", "CONTRIBUTING.md", "NOTICE", "README.md", "SECURITY.md",
}
SUSPICIOUS_HISTORY_NAMES = {
    ".env", ".env.local", ".env.production", "credentials", "credentials.json",
    "id_ed25519", "id_rsa",
}
SUSPICIOUS_HISTORY_SUFFIXES = {
    ".7z", ".bak", ".class", ".db", ".dmp", ".dump", ".jar", ".jks", ".key",
    ".keystore", ".old", ".orig", ".p12", ".pem", ".pfx", ".rar", ".sqlite", ".swp",
    ".zip",
}
WINDOWS_RESERVED_STEMS = {
    "CON", "PRN", "AUX", "NUL",
    *(f"COM{i}" for i in range(1, 10)),
    *(f"LPT{i}" for i in range(1, 10)),
}
PNG_REJECTED_METADATA = {b"tEXt", b"zTXt", b"iTXt", b"eXIf"}
PNG_ALLOWED_CHUNKS = {
    b"IHDR", b"PLTE", b"IDAT", b"IEND", b"tRNS", b"cHRM", b"gAMA",
    b"sRGB", b"pHYs", b"bKGD", b"tIME",
}

BANNED_PROSE_CHARS = {
    "\u00a0": "non-breaking space",
    "\u200b": "zero-width space",
    "\u2010": "Unicode hyphen",
    "\u2011": "non-breaking hyphen",
    "\u2012": "figure dash",
    "\u2013": "en dash",
    "\u2014": "em dash",
    "\u2018": "curly single quote",
    "\u2019": "curly single quote",
    "\u201c": "curly double quote",
    "\u201d": "curly double quote",
    "\u2022": "Unicode bullet",
    "\u2026": "single-character ellipsis",
}
MOJIBAKE_MARKERS = ("\ufffd", "\u00e2\u20ac", "\u00c3", "\u00c2")
META_PROSE_PATTERNS = (
    re.compile(r"\bas (?:you|the user) (?:mentioned|said|asked|requested)\b", re.I),
    re.compile(r"\blike (?:you|the user) (?:mentioned|said|asked)\b", re.I),
    re.compile(r"\bper (?:your|the user's) request\b", re.I),
    re.compile(r"\bas discussed (?:earlier|above|previously)\b", re.I),
    re.compile(r"\bin (?:this|our) (?:conversation|chat)\b", re.I),
    re.compile(r"\bthe user (?:asked|requested|mentioned|said)\b", re.I),
)
VAGUE_RELEASE_PHRASES = (
    re.compile(r"\bseamless(?:ly)?\b", re.I),
    re.compile(r"\bcomprehensive(?:ly)?\b", re.I),
    re.compile(r"\brobust(?:ly)?\b", re.I),
    re.compile(r"\bpowerful\b", re.I),
    re.compile(r"\benterprise[- ]grade\b", re.I),
    re.compile(r"\bnext[- ]generation\b", re.I),
    re.compile(r"\bstate[- ]of[- ]the[- ]art\b", re.I),
    re.compile(r"\brevolutionary\b", re.I),
    re.compile(r"\bcutting[- ]edge\b", re.I),
    re.compile(r"\b(?:greatly|significantly|dramatically) improved\b", re.I),
    re.compile(r"\bfull support\b", re.I),
    re.compile(r"\bfully compatible\b", re.I),
    re.compile(r"\bthis release marks\b", re.I),
    re.compile(r"\ba new era\b", re.I),
    re.compile(r"\bwe(?:'re| are) excited\b", re.I),
    re.compile(r"\bdesigned to\b", re.I),
    re.compile(r"\baims to\b", re.I),
    re.compile(r"\bhelps ensure\b", re.I),
    re.compile(r"\bleverages?\b", re.I),
    re.compile(r"\bmarks? a significant\b", re.I),
    re.compile(r"\bprovides? (?:a|an) (?:clean|robust|comprehensive|powerful)\b", re.I),
)

HIGH_CONFIDENCE_SECRET_PATTERNS = (
    (re.compile(rb"gh[pousr]_[A-Za-z0-9]{20,}"), "GitHub classic token"),
    (re.compile(rb"github_pat_[A-Za-z0-9_]{20,}"), "GitHub fine-grained token"),
    (re.compile(rb"AKIA[0-9A-Z]{16}"), "AWS access key"),
    (re.compile(rb"AIza[0-9A-Za-z_-]{35}"), "Google API key"),
    (re.compile(rb"xox[baprs]-[A-Za-z0-9-]{20,}"), "Slack token"),
    (re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{20,}"), "OpenAI-style key"),
    (re.compile(rb"mrp_[A-Za-z0-9_-]{20,}"), "Modrinth token"),
    (
        re.compile(
            rb"https://(?:discord(?:app)?\.com|discord\.com)/api/webhooks/"
            rb"[0-9]{6,}/[A-Za-z0-9._-]{20,}"
        ),
        "Discord webhook",
    ),
    (
        re.compile(rb"(?i)https?://[^/\s:@]+:[^@\s/]{6,}@[^\s/]+"),
        "credential-bearing URL",
    ),
    (
        re.compile(rb"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}"),
        "JWT",
    ),
    (
        re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
        "private key",
    ),
)
SECRET_ASSIGNMENT = re.compile(
    rb"(?im)^\s*['\"]?([A-Z][A-Z0-9_.-]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY|APIKEY|PRIVATE_KEY))"
    rb"['\"]?\s*[:=]\s*['\"]?([^\s'\"#,}]{8,})"
)
BEARER_CREDENTIAL_PATTERN = re.compile(
    rb"(?i)(?:authorization\s*[:=]\s*|bearer\s+)(?:bearer\s+)?[A-Za-z0-9._~+/=-]{20,}"
)
SERVICE_KEY_ASSIGNMENT = re.compile(
    rb"(?im)^\s*((?:CF|CLOUDFLARE|CURSEFORGE|MODRINTH|GITHUB|GOOGLE|DISCORD|OPENAI|AWS)"
    rb"[A-Z0-9_]*(?:KEY|TOKEN|SECRET))\s*[:=]\s*['\"]?([^\s'\"#]{8,})"
)
AUTHORIZATION_VALUE = re.compile(
    rb"(?im)^\s*Authorization\s*:\s*(?:Bearer|Basic)\s+([A-Za-z0-9._~+/-]{12,}={0,2})"
)
MACHINE_PATH_PATTERNS = (
    re.compile(rb"(?i)[A-Z]:\\(?:Users|ProgramData|Temp|Windows|Lab)\\[^\r\n\t]*"),
    re.compile(rb"/(?:home|Users)/[A-Za-z0-9._-]+/"),
)
TEXT_BINARY_MAGIC = (
    (b"\xca\xfe\xba\xbe", "Java class"),
    (b"PK\x03\x04", "ZIP archive"),
    (b"\x7fELF", "ELF executable"),
    (b"MZ", "PE executable"),
)


class GuardError(RuntimeError):
    pass


def git_text(*args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="strict",
    ).stdout.strip()


def git_bytes(*args: str) -> bytes:
    return subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_manifest_bytes(rel: str, data: bytes) -> bytes:
    kind = classify_path(rel)
    if kind == "png_resource":
        return data
    normalized = data.replace(b"\r\n", b"\n")
    return normalized.rstrip(b"\n") + b"\n"


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise GuardError(f"malformed properties line in {path.name}: {raw!r}")
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def classify_path(rel: str) -> str:
    if rel in ROOT_FILES:
        return "public_prose" if rel in PROSE_PATHS else "public_build_metadata"
    if rel in GITHUB_FILES:
        if rel.startswith(".github/ISSUE_TEMPLATE/"):
            return "public_issue_template"
        if rel.startswith(".github/workflows/"):
            return "public_workflow"
        if rel.endswith(".py"):
            return "public_guard"
        if rel == MANIFEST_PATH:
            return "public_manifest"
        return "public_metadata"

    p = Path(rel)
    parts = p.parts
    if len(parts) < 4 or parts[:2] != ("src", "main"):
        raise GuardError(f"path is outside the public allowlist: {rel}")
    section = parts[2]
    suffix = p.suffix.lower()
    if section == "java" and suffix == ".java":
        return "java_source"
    if section == "resources" and suffix in RESOURCE_SUFFIXES:
        return "png_resource" if suffix == ".png" else "text_resource"
    if section == "templates" and suffix == ".toml":
        return "text_template"
    raise GuardError(f"unexpected public source path or file type: {rel}")


def validate_public_path(rel: str, seen_casefold: dict[str, str]) -> None:
    if not rel or rel.startswith("/") or rel.endswith("/") or "\\" in rel:
        raise GuardError(f"unsafe public path: {rel!r}")
    if not rel.isascii():
        raise GuardError(f"non-ASCII public path is not allowed: {rel!r}")
    if len(rel) > 240:
        raise GuardError(f"public path exceeds cross-platform length limit: {rel}")
    if any(ord(char) < 32 or ord(char) == 127 for char in rel):
        raise GuardError(f"control character found in public path: {rel!r}")

    parts = rel.split("/")
    for part in parts:
        if part in {"", ".", ".."}:
            raise GuardError(f"unsafe public path component in {rel!r}")
        if len(part) > 120:
            raise GuardError(f"public path component is too long: {rel!r}")
        if part.endswith((" ", ".")):
            raise GuardError(f"public path component ends with dot/space: {rel!r}")
        stem = part.split(".", 1)[0].upper()
        if stem in WINDOWS_RESERVED_STEMS:
            raise GuardError(f"Windows-reserved public path component: {rel!r}")
        if part.casefold() == ".git":
            raise GuardError(f"Git metadata path is forbidden: {rel!r}")

    folded = rel.casefold()
    previous = seen_casefold.get(folded)
    if previous is not None and previous != rel:
        raise GuardError(
            f"case-insensitive public path collision: {previous!r} vs {rel!r}"
        )
    seen_casefold[folded] = rel


def tracked_modes() -> dict[str, str]:
    result: dict[str, str] = {}
    seen_casefold: dict[str, str] = {}
    for raw in git_text("ls-files", "-s").splitlines():
        meta, rel = raw.split("\t", 1)
        validate_public_path(rel, seen_casefold)
        mode, _sha, stage = meta.split()
        if stage != "0":
            raise GuardError(f"non-zero Git index stage for {rel}")
        if mode != "100644":
            raise GuardError(f"unexpected Git mode {mode} for {rel}")
        if rel in result:
            raise GuardError(f"duplicate Git index path: {rel}")
        result[rel] = mode
    return result


def load_manifest() -> dict[str, object]:
    path = ROOT / MANIFEST_PATH
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GuardError(f"invalid public source manifest: {exc}") from exc
    if manifest.get("schema") != 1:
        raise GuardError("unsupported public source manifest schema")
    if not isinstance(manifest.get("release"), str) or not manifest["release"]:
        raise GuardError("manifest release is missing")
    files = manifest.get("files")
    if not isinstance(files, list) or not files:
        raise GuardError("manifest file inventory is missing")
    return manifest


def verify_manifest() -> tuple[dict[str, str], dict[str, object]]:
    modes = tracked_modes()
    manifest = load_manifest()
    entries: dict[str, dict[str, object]] = {}
    for item in manifest["files"]:
        if not isinstance(item, dict):
            raise GuardError("manifest contains a non-object file entry")
        rel = item.get("path")
        if not isinstance(rel, str) or not rel:
            raise GuardError("manifest contains an invalid path")
        if rel == MANIFEST_PATH:
            raise GuardError("manifest must not inventory itself")
        if rel in entries:
            raise GuardError(f"duplicate manifest path: {rel}")
        entries[rel] = item

    control_paths = {MANIFEST_PATH}
    workflow = ".github/workflows/public-source-guard.yml"
    if os.environ.get("AA_ALLOW_DEFERRED_WORKFLOW", "").lower() not in {"1", "true", "yes"}:
        control_paths.add(workflow)
    expected_paths = set(entries) | control_paths
    actual_paths = set(modes)
    if actual_paths != expected_paths:
        raise GuardError(
            "tracked tree differs from public manifest: "
            f"missing={sorted(expected_paths - actual_paths)[:20]} "
            f"extra={sorted(actual_paths - expected_paths)[:20]}"
        )

    props = parse_properties(ROOT / "gradle.properties")
    if manifest["release"] != props.get("mod_version"):
        raise GuardError(
            f"manifest release {manifest['release']!r} does not match gradle.properties"
        )

    for rel, item in entries.items():
        path = ROOT / rel
        if not path.is_file() or path.is_symlink() or stat.S_ISLNK(path.lstat().st_mode):
            raise GuardError(f"manifest path is not a regular file: {rel}")
        data = path.read_bytes()
        kind = classify_path(rel)
        if item.get("kind") != kind:
            raise GuardError(f"manifest file class mismatch for {rel}")
        if item.get("mode") != modes[rel]:
            raise GuardError(f"manifest Git mode mismatch for {rel}")
        canonical = canonical_manifest_bytes(rel, data)
        if item.get("size") != len(canonical):
            raise GuardError(f"manifest size mismatch for {rel}")
        if item.get("sha256") != sha256(canonical):
            raise GuardError(f"manifest SHA-256 mismatch for {rel}")

    return modes, manifest


def _placeholder_value(value: bytes) -> bool:
    lowered = value.lower().strip()
    return (
        lowered in {b"changeme", b"example", b"placeholder", b"redacted", b"undefined", b"none"}
        or value.startswith((b"$" + b"{", b"{" + b"{", b"<"))
    )


def scan_secret_material(
    data: bytes,
    context: str,
    *,
    include_machine_paths: bool = True,
) -> None:
    for pattern, label in HIGH_CONFIDENCE_SECRET_PATTERNS:
        if pattern.search(data):
            raise GuardError(f"{label} pattern found in {context}")
    if BEARER_CREDENTIAL_PATTERN.search(data):
        raise GuardError(f"bearer/access token pattern found in {context}")
    for pattern in (SECRET_ASSIGNMENT, SERVICE_KEY_ASSIGNMENT):
        for match in pattern.finditer(data):
            value = match.group(2)
            if _placeholder_value(value):
                continue
            raise GuardError(
                f"credential-like assignment found in {context}: "
                f"{match.group(1).decode('ascii', 'replace')}"
            )
    for match in AUTHORIZATION_VALUE.finditer(data):
        if not _placeholder_value(match.group(1)):
            raise GuardError(f"authorization credential found in {context}")
    if include_machine_paths:
        for pattern in MACHINE_PATH_PATTERNS:
            if pattern.search(data):
                raise GuardError(f"machine-specific absolute path found in {context}")


def validate_text(rel: str, data: bytes) -> str:
    if b"\x00" in data:
        raise GuardError(f"NUL byte found in text file: {rel}")
    for magic, label in TEXT_BINARY_MAGIC:
        if data.startswith(magic):
            raise GuardError(f"{label} payload disguised as text: {rel}")
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise GuardError(f"text file is not valid UTF-8: {rel}: {exc}") from exc


def validate_java(rel: str, text: str) -> None:
    if "SPDX-License-Identifier: MPL-2.0" not in "\n".join(text.splitlines()[:8]):
        raise GuardError(f"Java source lacks MPL SPDX header: {rel}")
    match = re.search(r"(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;", text)
    if not match:
        raise GuardError(f"Java source has no package declaration: {rel}")
    expected = "/".join(match.group(1).split("."))
    if not Path(rel).parent.as_posix().endswith("/" + expected):
        raise GuardError(f"Java package/path mismatch: {rel} declares {match.group(1)}")


def no_duplicate_json_pairs(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise GuardError(f"duplicate JSON object key: {key}")
        result[key] = value
    return result


def validate_json(rel: str, text: str) -> None:
    try:
        json.loads(text, object_pairs_hook=no_duplicate_json_pairs)
    except (json.JSONDecodeError, GuardError) as exc:
        raise GuardError(f"invalid JSON resource {rel}: {exc}") from exc


def validate_toml(rel: str, text: str) -> None:
    try:
        tomllib.loads(text)
    except tomllib.TOMLDecodeError as exc:
        raise GuardError(f"invalid TOML resource {rel}: {exc}") from exc


def validate_png(rel: str, data: bytes) -> None:
    if not data.startswith(PNG_SIGNATURE):
        raise GuardError(f"invalid PNG signature: {rel}")

    legal_bit_depths = {
        0: {1, 2, 4, 8, 16},
        2: {8, 16},
        3: {1, 2, 4, 8},
        4: {8, 16},
        6: {8, 16},
    }
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}

    pos = len(PNG_SIGNATURE)
    chunks: list[bytes] = []
    idat_payloads: list[bytes] = []
    width = height = bit_depth = color_type = None
    palette_entries: int | None = None
    saw_idat = False
    idat_closed = False
    saw_iend = False
    singleton_chunks = {
        b"PLTE", b"tRNS", b"cHRM", b"gAMA", b"sRGB", b"pHYs", b"bKGD", b"tIME",
    }
    seen_singletons: set[bytes] = set()

    while pos < len(data):
        if pos + 12 > len(data):
            raise GuardError(f"truncated PNG chunk header: {rel}")
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        chunk_type = data[pos + 4 : pos + 8]
        end = pos + 12 + length
        if length > MAX_FILE_SIZE or end > len(data):
            raise GuardError(f"invalid PNG chunk length in {rel}")
        payload = data[pos + 8 : pos + 8 + length]
        expected_crc = struct.unpack(">I", data[pos + 8 + length : end])[0]
        actual_crc = binascii.crc32(chunk_type + payload) & 0xFFFFFFFF
        if expected_crc != actual_crc:
            raise GuardError(f"PNG CRC mismatch in {rel}")
        if not re.fullmatch(rb"[A-Za-z]{4}", chunk_type):
            raise GuardError(f"invalid PNG chunk type in {rel}")
        if chunk_type in PNG_REJECTED_METADATA:
            raise GuardError(
                f"PNG contains removable text/EXIF metadata {chunk_type.decode()}: {rel}"
            )
        if chunk_type not in PNG_ALLOWED_CHUNKS:
            raise GuardError(f"unclassified PNG chunk {chunk_type!r}: {rel}")
        if chunk_type in singleton_chunks:
            if chunk_type in seen_singletons:
                raise GuardError(f"duplicate singleton PNG chunk {chunk_type!r}: {rel}")
            seen_singletons.add(chunk_type)

        if not chunks and chunk_type != b"IHDR":
            raise GuardError(f"PNG does not begin with IHDR: {rel}")
        if chunk_type == b"IHDR":
            if chunks:
                raise GuardError(f"duplicate or misplaced IHDR chunk: {rel}")
            if len(payload) != 13:
                raise GuardError(f"invalid IHDR length: {rel}")
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            if width == 0 or height == 0 or width > 16384 or height > 16384:
                raise GuardError(f"invalid PNG dimensions in {rel}: {width}x{height}")
            if color_type not in legal_bit_depths or bit_depth not in legal_bit_depths[color_type]:
                raise GuardError(
                    f"invalid PNG bit-depth/color-type pair in {rel}: "
                    f"bit_depth={bit_depth} color_type={color_type}"
                )
            if compression != 0 or filter_method != 0:
                raise GuardError(f"unsupported PNG compression/filter method in {rel}")
            if interlace != 0:
                raise GuardError(f"interlaced PNG is not allowed in public textures: {rel}")

        elif chunk_type == b"PLTE":
            if saw_idat:
                raise GuardError(f"PLTE appears after IDAT in {rel}")
            if palette_entries is not None:
                raise GuardError(f"duplicate PLTE chunk in {rel}")
            if length == 0 or length % 3 != 0 or length > 768:
                raise GuardError(f"invalid PLTE length in {rel}")
            palette_entries = length // 3
            if color_type == 3 and palette_entries > (1 << int(bit_depth)):
                raise GuardError(f"PLTE contains too many entries for indexed PNG: {rel}")

        elif chunk_type == b"IDAT":
            if idat_closed:
                raise GuardError(f"non-consecutive IDAT chunks in {rel}")
            if color_type == 3 and palette_entries is None:
                raise GuardError(f"indexed PNG is missing PLTE before IDAT: {rel}")
            saw_idat = True
            idat_payloads.append(payload)

        elif chunk_type == b"IEND":
            if not saw_idat:
                raise GuardError(f"PNG reaches IEND before IDAT: {rel}")
            if length != 0 or end != len(data):
                raise GuardError(f"invalid IEND/trailing data in {rel}")
            saw_iend = True

        else:
            if saw_idat:
                idat_closed = True
            if chunk_type == b"tRNS":
                if saw_idat:
                    raise GuardError(f"tRNS appears after IDAT in {rel}")
                if color_type in {4, 6}:
                    raise GuardError(f"tRNS is invalid for alpha PNG color type in {rel}")
                if color_type == 3 and (
                    palette_entries is None or length > palette_entries
                ):
                    raise GuardError(f"invalid indexed PNG tRNS length in {rel}")
                if color_type == 0 and length != 2:
                    raise GuardError(f"invalid grayscale PNG tRNS length in {rel}")
                if color_type == 2 and length != 6:
                    raise GuardError(f"invalid truecolor PNG tRNS length in {rel}")

        chunks.append(chunk_type)
        pos = end
        if saw_iend:
            break

    if not chunks or chunks[0] != b"IHDR" or not saw_idat or not saw_iend:
        raise GuardError(f"incomplete PNG structure: {rel}")
    if chunks.count(b"IEND") != 1:
        raise GuardError(f"invalid IEND count in {rel}")
    if color_type == 3 and palette_entries is None:
        raise GuardError(f"indexed PNG is missing PLTE: {rel}")

    assert width is not None
    assert height is not None
    assert bit_depth is not None
    assert color_type is not None

    bits_per_pixel = channels[color_type] * bit_depth
    row_bytes = (width * bits_per_pixel + 7) // 8
    expected_raster = height * (1 + row_bytes)
    if expected_raster > MAX_PNG_RASTER_SIZE:
        raise GuardError(
            f"PNG decompressed raster exceeds public limit: {rel} size={expected_raster}"
        )

    compressed = b"".join(idat_payloads)
    try:
        decompressor = zlib.decompressobj()
        raster = decompressor.decompress(compressed, expected_raster + 1)
        if len(raster) > expected_raster or decompressor.unconsumed_tail:
            raise GuardError(f"PNG compressed stream expands beyond expected raster: {rel}")
        raster += decompressor.flush(max(1, expected_raster + 1 - len(raster)))
    except zlib.error as exc:
        raise GuardError(f"invalid PNG zlib stream in {rel}: {exc}") from exc
    if len(raster) > expected_raster:
        raise GuardError(f"PNG compressed stream expands beyond expected raster: {rel}")
    if not decompressor.eof or decompressor.unused_data or decompressor.unconsumed_tail:
        raise GuardError(f"PNG contains incomplete, trailing, or overlong compressed data: {rel}")
    if len(raster) != expected_raster:
        raise GuardError(
            f"PNG raster length mismatch in {rel}: "
            f"expected={expected_raster} actual={len(raster)}"
        )

    stride = 1 + row_bytes
    for row in range(height):
        filter_type = raster[row * stride]
        if filter_type > 4:
            raise GuardError(f"invalid PNG scanline filter {filter_type} in {rel}")


def validate_class(rel: str, data: bytes) -> None:
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise GuardError(f"invalid Java class header: {rel}")
    scan_secret_material(data, rel)

    minor, major, constant_pool_count = struct.unpack(">HHH", data[4:10])
    if minor != 0 or major != EXPECTED_CLASS_MAJOR:
        raise GuardError(
            f"unexpected Java class version in {rel}: minor={minor} major={major}"
        )
    if constant_pool_count < 2:
        raise GuardError(f"invalid Java constant pool count: {rel}")

    pos = 10
    tags: list[int | None] = [None] * constant_pool_count
    utf8: dict[int, bytes] = {}
    refs: list[tuple[int, int, tuple[int, ...], str]] = []

    def require(size: int, label: str) -> None:
        if size < 0 or pos + size > len(data):
            raise GuardError(f"truncated Java {label}: {rel}")

    def cp_ref(index: int, allowed: tuple[int, ...], label: str) -> None:
        if index <= 0 or index >= constant_pool_count:
            raise GuardError(f"invalid Java constant-pool index for {label}: {rel}")
        if tags[index] not in allowed:
            raise GuardError(
                f"invalid Java constant-pool tag for {label} in {rel}: "
                f"index={index} tag={tags[index]} expected={allowed}"
            )

    index = 1
    while index < constant_pool_count:
        require(1, "constant pool tag")
        tag = data[pos]
        pos += 1
        tags[index] = tag

        if tag == 1:
            require(2, "UTF-8 constant length")
            length = struct.unpack(">H", data[pos : pos + 2])[0]
            pos += 2
            require(length, "UTF-8 constant")
            raw = data[pos : pos + length]
            scan_secret_material(raw, f"{rel} constant pool")
            utf8[index] = raw
            pos += length
        elif tag in {3, 4}:
            require(4, "numeric constant")
            pos += 4
        elif tag in {5, 6}:
            require(8, "wide numeric constant")
            pos += 8
            index += 1
            if index >= constant_pool_count:
                raise GuardError(f"wide Java constant overruns constant pool: {rel}")
            tags[index] = 0
        elif tag in {7, 8, 16, 19, 20}:
            require(2, "single-reference constant")
            target = struct.unpack(">H", data[pos : pos + 2])[0]
            refs.append((target, index, (1,), f"constant tag {tag}"))
            pos += 2
        elif tag in {9, 10, 11}:
            require(4, "member-reference constant")
            class_index, name_type_index = struct.unpack(">HH", data[pos : pos + 4])
            refs.append((class_index, index, (7,), f"member class tag {tag}"))
            refs.append((name_type_index, index, (12,), f"member name/type tag {tag}"))
            pos += 4
        elif tag == 12:
            require(4, "name-and-type constant")
            name_index, descriptor_index = struct.unpack(">HH", data[pos : pos + 4])
            refs.append((name_index, index, (1,), "name-and-type name"))
            refs.append((descriptor_index, index, (1,), "name-and-type descriptor"))
            pos += 4
        elif tag in {17, 18}:
            require(4, "dynamic constant")
            _bootstrap_index, name_type_index = struct.unpack(">HH", data[pos : pos + 4])
            refs.append((name_type_index, index, (12,), f"dynamic name/type tag {tag}"))
            pos += 4
        elif tag == 15:
            require(3, "method-handle constant")
            reference_kind = data[pos]
            reference_index = struct.unpack(">H", data[pos + 1 : pos + 3])[0]
            if not 1 <= reference_kind <= 9:
                raise GuardError(f"invalid Java method-handle kind in {rel}: {reference_kind}")
            if reference_kind <= 4:
                allowed = (9,)
            elif reference_kind == 9:
                allowed = (11,)
            elif reference_kind == 8:
                allowed = (10,)
            else:
                allowed = (10, 11)
            refs.append((reference_index, index, allowed, "method-handle reference"))
            pos += 3
        else:
            raise GuardError(f"unknown Java constant pool tag {tag} in {rel}")
        index += 1

    for target, _owner, allowed, label in refs:
        cp_ref(target, allowed, label)

    def read_u2(label: str) -> int:
        nonlocal pos
        require(2, label)
        value = struct.unpack(">H", data[pos : pos + 2])[0]
        pos += 2
        return value

    def read_u4(label: str) -> int:
        nonlocal pos
        require(4, label)
        value = struct.unpack(">I", data[pos : pos + 4])[0]
        pos += 4
        return value

    def skip_attributes(count: int, owner: str) -> None:
        nonlocal pos
        for _ in range(count):
            name_index = read_u2(f"{owner} attribute name")
            cp_ref(name_index, (1,), f"{owner} attribute name")
            length = read_u4(f"{owner} attribute length")
            if length > MAX_ARCHIVE_ENTRY_SIZE:
                raise GuardError(f"oversized Java attribute in {rel}: {owner} size={length}")
            require(length, f"{owner} attribute payload")
            pos += length

    def skip_members(count: int, owner: str) -> None:
        for _ in range(count):
            _access = read_u2(f"{owner} access flags")
            name_index = read_u2(f"{owner} name")
            descriptor_index = read_u2(f"{owner} descriptor")
            cp_ref(name_index, (1,), f"{owner} name")
            cp_ref(descriptor_index, (1,), f"{owner} descriptor")
            skip_attributes(read_u2(f"{owner} attribute count"), owner)

    _access_flags = read_u2("class access flags")
    this_class = read_u2("this_class")
    super_class = read_u2("super_class")
    cp_ref(this_class, (7,), "this_class")
    if super_class != 0:
        cp_ref(super_class, (7,), "super_class")

    for _ in range(read_u2("interfaces count")):
        cp_ref(read_u2("interface"), (7,), "interface")
    skip_members(read_u2("fields count"), "field")
    skip_members(read_u2("methods count"), "method")
    skip_attributes(read_u2("class attribute count"), "class")

    if pos != len(data):
        raise GuardError(f"trailing data after Java class structure: {rel}")

    class_name_index = None
    for target, owner, allowed, _label in refs:
        if owner == this_class and allowed == (1,):
            class_name_index = target
            break
    if class_name_index is not None:
        try:
            internal_name = utf8[class_name_index].decode("ascii")
        except (KeyError, UnicodeDecodeError) as exc:
            raise GuardError(f"non-ASCII or missing Java class name in {rel}") from exc
        if "/" in rel and not rel.endswith(internal_name + ".class"):
            raise GuardError(
                f"Java class path/name mismatch in {rel}: declares {internal_name}"
            )


def validate_prose(rel: str, text: str) -> None:
    non_ascii = sorted({char for char in text if ord(char) > 127})
    if non_ascii:
        sample = ", ".join(f"U+{ord(char):04X}" for char in non_ascii[:12])
        raise GuardError(f"non-ASCII character found in public prose {rel}: {sample}")
    for char, label in BANNED_PROSE_CHARS.items():
        if char in text:
            raise GuardError(f"{label} is not allowed in public prose: {rel}")
    for marker in MOJIBAKE_MARKERS:
        if marker in text:
            raise GuardError(f"mojibake marker {marker!r} found in public prose: {rel}")
    for pattern in META_PROSE_PATTERNS:
        if pattern.search(text):
            raise GuardError(f"drafting-process/meta reference found in public prose: {rel}")


def validate_release_prose(rel: str, text: str) -> None:
    validate_prose(rel, text)
    for pattern in VAGUE_RELEASE_PHRASES:
        if pattern.search(text):
            raise GuardError(f"vague promotional phrasing found in release prose: {rel}")

    if Path(rel).name == "CHANGELOG.md":
        lines = [line.rstrip() for line in text.splitlines()]
        nonempty = [line for line in lines if line.strip()]
        if not nonempty or nonempty[0] != "# Changelog":
            raise GuardError("CHANGELOG.md must begin with exactly '# Changelog'")
        release_headings = [line for line in lines if line.startswith("## ")]
        if not release_headings:
            raise GuardError("CHANGELOG.md contains no release headings")
        for heading in release_headings:
            if not re.fullmatch(r"## [0-9]+\.[0-9]+\.[0-9]+-Beta", heading):
                raise GuardError(
                    "changelog release headings must contain only the version: "
                    f"{heading!r}"
                )
        properties_path = ROOT / "gradle.properties"
        if properties_path.is_file():
            current = parse_properties(properties_path).get("mod_version")
            if release_headings[0] != f"## {current}":
                raise GuardError(
                    f"first changelog release heading must match current version {current!r}"
                )


def validate_current_tree() -> dict[str, object]:
    modes, manifest = verify_manifest()
    for rel in sorted(modes):
        path = ROOT / rel
        data = path.read_bytes()
        if len(data) > MAX_FILE_SIZE:
            raise GuardError(f"public file exceeds size limit: {rel}")
        scan_secret_material(data, rel)

        kind = classify_path(rel)
        if kind == "png_resource":
            validate_png(rel, data)
            continue

        suffix = Path(rel).suffix.lower()
        if suffix in TEXT_SUFFIXES or rel in ROOT_FILES or rel in GITHUB_FILES:
            text = validate_text(rel, data)
            if rel.endswith((".json", ".mcmeta")):
                validate_json(rel, text)
            if rel.endswith(".toml"):
                validate_toml(rel, text)
            if kind == "java_source":
                validate_java(rel, text)
            if kind in {"public_prose", "public_issue_template"}:
                if Path(rel).name == "CHANGELOG.md":
                    validate_release_prose(rel, text)
                else:
                    validate_prose(rel, text)
    return manifest


def verify_history_shape() -> None:
    if os.environ.get("AA_REQUIRE_ISOLATED_PREVIEW_HISTORY", "").lower() not in {
        "1", "true", "yes"
    }:
        return
    commits = [line for line in git_text("rev-list", "HEAD").splitlines() if line]
    roots = [line for line in git_text("rev-list", "--max-parents=0", "HEAD").splitlines() if line]
    if len(roots) != 1:
        raise GuardError(f"Public-Preview must have exactly one root commit; roots={len(roots)}")
    if len(git_text("rev-list", "--parents", "-n", "1", roots[0]).split()) != 1:
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
        line for line in git_text(
            "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD^", "HEAD"
        ).splitlines()
        if line
    ]
    if changed != [workflow]:
        raise GuardError(
            "the only permitted second Public-Preview commit is the CI workflow; "
            f"changed={changed}"
        )


def scan_reachable_history() -> None:
    names = set()
    for line in git_text("log", "HEAD", "--format=", "--name-only").splitlines():
        rel = line.strip()
        if rel:
            names.add(rel)
    for rel in sorted(names):
        p = Path(rel)
        if p.name in SUSPICIOUS_HISTORY_NAMES or p.suffix.lower() in SUSPICIOUS_HISTORY_SUFFIXES:
            raise GuardError(f"suspicious filename exists in reachable Git history: {rel}")

    # Scan every reachable Git blob instead of relying on patch rendering. This catches content
    # that Git may classify as binary and content that was later removed from the current tree.
    shas = list(
        dict.fromkeys(
            line.split(" ", 1)[0]
            for line in git_text("rev-list", "--objects", "HEAD").splitlines()
            if line
        )
    )
    if not shas:
        return

    check = subprocess.run(
        [
            "git", "-C", str(ROOT), "cat-file",
            "--batch-check=%(objectname) %(objecttype) %(objectsize)",
        ],
        input="\n".join(shas) + "\n",
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
    ).stdout.splitlines()

    blobs: list[tuple[str, int]] = []
    for line in check:
        sha, obj_type, raw_size = line.split()
        if obj_type != "blob":
            continue
        size = int(raw_size)
        if size > MAX_FILE_SIZE:
            raise GuardError(
                f"oversized blob exists in reachable Git history: {sha} size={size}"
            )
        blobs.append((sha, size))

    batch = subprocess.run(
        ["git", "-C", str(ROOT), "cat-file", "--batch"],
        input="\n".join(sha for sha, _size in blobs).encode("ascii") + b"\n",
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout

    pos = 0
    for expected_sha, expected_size in blobs:
        line_end = batch.find(b"\n", pos)
        if line_end < 0:
            raise GuardError("truncated git cat-file history stream")
        header = batch[pos:line_end].decode("ascii")
        sha, obj_type, raw_size = header.split()
        size = int(raw_size)
        if sha != expected_sha or obj_type != "blob" or size != expected_size:
            raise GuardError("git cat-file history stream lost object alignment")
        start = line_end + 1
        end = start + size
        if end >= len(batch) or batch[end:end + 1] != b"\n":
            raise GuardError("truncated git blob in history stream")
        data = batch[start:end]
        if data.startswith(PNG_SIGNATURE):
            validate_png(f"reachable Git blob {sha}", data)
        scan_secret_material(
            data,
            f"reachable Git blob {sha}",
            include_machine_paths=False,
        )
        pos = end + 1

    baseline = subprocess.run(
        [
            "git", "-C", str(ROOT), "merge-base", "--is-ancestor",
            LEGACY_HISTORY_BASELINE, "HEAD",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode == 0
    if baseline:
        recent = git_bytes("diff", "--binary", LEGACY_HISTORY_BASELINE, "HEAD")
    else:
        recent = git_bytes(
            "log", "--full-history", "--no-ext-diff", "--text",
            "--format=commit:%H", "-p", "HEAD"
        )
    scan_secret_material(recent, "post-baseline reachable history")


def verify_jar(path: Path) -> None:
    if not path.is_file():
        raise GuardError(f"runtime JAR not found: {path}")
    props = parse_properties(ROOT / "gradle.properties")
    group_path = props["mod_group_id"].replace(".", "/") + "/"
    missing_sources: set[str] = set()
    foreign_classes: set[str] = set()

    with zipfile.ZipFile(path) as jar:
        if jar.comment:
            raise GuardError("runtime JAR contains an archive comment")
        infos = [info for info in jar.infolist() if not info.is_dir()]
        if len(infos) > MAX_ARCHIVE_ENTRIES:
            raise GuardError(f"runtime JAR contains too many entries: {len(infos)}")
        total_uncompressed = sum(info.file_size for info in infos)
        if total_uncompressed > MAX_ARCHIVE_TOTAL_SIZE:
            raise GuardError(
                f"runtime JAR expands beyond public limit: {total_uncompressed} bytes"
            )
        names = [info.filename for info in infos]
        if len(names) != len(set(names)):
            raise GuardError("runtime JAR contains duplicate entry names")
        name_set = set(names)
        for required in (
            "META-INF/source-code.txt",
            "META-INF/licenses/apothic_ascension.txt",
        ):
            if required not in name_set:
                raise GuardError(f"runtime JAR missing publication notice: {required}")

        for info in infos:
            name = info.filename
            if (
                not name
                or name.startswith("/")
                or "\\" in name
                or "\x00" in name
                or any(part in {"", ".", ".."} for part in name.split("/"))
            ):
                raise GuardError(f"unsafe runtime JAR entry path: {name!r}")
            unix_mode = (info.external_attr >> 16) & 0xFFFF
            if unix_mode and stat.S_ISLNK(unix_mode):
                raise GuardError(f"symlink entry is forbidden in runtime JAR: {name}")
            if info.flag_bits & 0x1:
                raise GuardError(f"encrypted runtime JAR entry is forbidden: {name}")
            if info.compress_type not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}:
                raise GuardError(f"unexpected ZIP compression method for {name}")
            if info.file_size > MAX_ARCHIVE_ENTRY_SIZE:
                raise GuardError(f"oversized runtime JAR entry: {name}")
            if info.comment:
                raise GuardError(f"runtime JAR entry comment is forbidden: {name}")
            if info.extra:
                scan_secret_material(info.extra, f"JAR extra field {name}")

            data = jar.read(info)
            suffix = Path(name).suffix.lower()
            if name.endswith(".class"):
                validate_class(name, data)
                if name == "module-info.class":
                    continue
                if not name.startswith(group_path):
                    foreign_classes.add(name)
                    continue
                outer = name[:-6].split("$", 1)[0]
                source = ROOT / "src" / "main" / "java" / f"{outer}.java"
                if not source.is_file():
                    missing_sources.add(source.relative_to(ROOT).as_posix())
                continue

            if suffix == ".png":
                validate_png(f"JAR:{name}", data)
                continue

            if suffix in TEXT_SUFFIXES or name.upper() == "META-INF/MANIFEST.MF":
                scan_secret_material(data, f"JAR:{name}")
                text = validate_text(f"JAR:{name}", data)
                if suffix in {".json", ".mcmeta"}:
                    validate_json(f"JAR:{name}", text)
                elif suffix == ".toml":
                    validate_toml(f"JAR:{name}", text)

    if foreign_classes:
        raise GuardError(
            "runtime JAR unexpectedly bundles classes outside the first-party package: "
            + ", ".join(sorted(foreign_classes)[:20])
        )
    if missing_sources:
        raise GuardError(
            "runtime JAR contains first-party classes without public source: "
            + ", ".join(sorted(missing_sources)[:20])
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--skip-history", action="store_true")
    args = parser.parse_args()

    manifest = validate_current_tree()
    verify_history_shape()
    if not args.skip_history:
        scan_reachable_history()
    if args.jar is not None:
        verify_jar(args.jar.resolve())

    file_count = len(manifest["files"]) + 1
    java_count = sum(
        1 for item in manifest["files"] if item.get("kind") == "java_source"
    )
    print(
        "PUBLIC_SOURCE_GUARD_PASS "
        f"files={file_count} java_sources={java_count} "
        f"history={'skipped' if args.skip_history else 'scanned'}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (GuardError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print(f"PUBLIC_SOURCE_GUARD_FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
