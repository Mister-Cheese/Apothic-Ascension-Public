# Apothic Ascension

This repository is the **public, release-aligned source export** for Apothic Ascension.

Current exported version: **1.14.1-Beta**
Minecraft: **1.21.1**
NeoForge: **21.1.249**

## What is published here

This repository contains the production Java source, runtime resources, mod metadata,
and a minimal public build definition sufficient to inspect, modify, and build the mod.

It intentionally does **not** mirror the private development repository or its history.
Internal CI/CD, qualification harnesses, release engineering, research notes, audit
evidence, local tooling, and development-only documentation are maintained separately.

## Build

Requirements:

- Java 21
- Gradle 8.14.3

From the repository root:

```text
gradle clean build
```

The resulting JAR is written under `build/libs/`.

## Issues

Use this repository's issue tracker for bugs, compatibility reports, and feature requests.
Please include the exact Apothic Ascension version and relevant mod versions.

For security vulnerabilities, use GitHub's private vulnerability reporting instead of a
public issue.

## License

Source in this repository is licensed under the **Mozilla Public License 2.0**.
See [LICENSE](LICENSE).

Earlier copies of Apothic Ascension that were distributed under different license terms
retain those grants; publishing this source under MPL-2.0 does not revoke them.

## Repository boundary

Public commits are generated source snapshots and are not a sanitized copy of private Git
history. This separation is intentional: only release-facing source is exported across the
repository boundary.
