# Release and version policy

Jobstein is currently a pre-release administrative foundation. The Gradle project version is `0.1.0-SNAPSHOT`; this version is visible in distribution filenames and `jobstein --version`. No release artifact or compatibility promise is implied by the snapshot.

## Versioning

- Set the distribution version in `build.gradle.kts` and keep the CLI version in `Jobstein.java` in sync. Verify the unpacked launcher with `jobstein --version`.
- Use semantic-version-style `MAJOR.MINOR.PATCH` values for published releases. Increment the major version for incompatible CLI, persisted-state, or supported-environment changes; increment minor for backward-compatible capabilities; increment patch for compatible fixes.
- Remove `-SNAPSHOT` only in a reviewed release commit. The release commit must pass the full Java 21 test suite, `installDist`, the credential-free smoke test, and the reproducible distribution/checksum verifier.
- Publish only from an exact CI-green commit on the protected main branch. Record the commit SHA and SHA-256 sidecars with the release notes.
- Do not package local configuration, credentials, resumes, or generated sandbox state in a release archive. Persisted state remains outside the installation directory.

## Licensing and release authority

No open-source license has been selected in this repository. Do not add a license file, SPDX identifier, copyright statement, or public release until the project owner chooses the license and approves the release terms. Until then, the repository and distributions remain unreleased development material.

Schema changes require a compatibility review and an explicit migration plan. Existing state must not be rewritten implicitly during a version upgrade; unsupported or unsafe state should fail closed with repair guidance.
