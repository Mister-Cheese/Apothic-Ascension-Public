# Apothic Ascension — Public Source

This repository is the public, release-aligned source export and issue tracker for **Apothic Ascension**.

It is intentionally **not** the development repository. Public history is constructed from release source snapshots and does not inherit private development history. Internal research, release engineering, CI/CD, qualification infrastructure, operator notes, and other non-distributed tooling are maintained separately and are not part of this repository.

## Source availability

Source snapshots are published here for releases that declare **Mozilla Public License 2.0 (MPL-2.0)**. Each public snapshot is produced from an explicit source allowlist and checked against the corresponding distributed JAR so that shipped first-party classes are not silently omitted from the source export.

Releases published before the MPL transition retain the license declared by those releases; the presence of an MPL license in this repository does not retroactively rewrite their release metadata.

## Issues

Use the issue tracker for reproducible bugs and compatibility problems. Include the exact Apothic Ascension, Minecraft, NeoForge, Apotheosis, and relevant third-party mod versions when applicable.

Do not post credentials, private server information, access tokens, or other secrets in an issue.

## Contributions

This repository is an export target rather than the authoritative development workspace. Issues are the preferred public contribution path. Source changes accepted for a future release are incorporated into the authoritative development tree and re-exported with that release.

## License

Unless a file or bundled third-party notice states otherwise, first-party source published for MPL releases is made available under the **Mozilla Public License 2.0**. Third-party license notices remain controlling for their respective material.
