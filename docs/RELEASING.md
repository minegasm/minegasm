# Releasing Minegasm on Codeberg

Minegasm uses Forgejo Actions. The workflow in `.forgejo/workflows/build.yml` builds and tests every
NeoForge, Fabric, and Forge variant (`chiseledBuild`) on pushes, pull requests, and manual runs. A
matching beta tag additionally creates a Codeberg prerelease containing all the built jars and their
SHA-256 checksums. Forge builds on both Minecraft lines (unblocked per
`docs/adr/ADR-011-add-forge-loader.md`).

## One-time Codeberg setup

1. Request access to Codeberg's hosted Actions runners by following the current instructions at
   <https://codeberg.org/actions/meta>. Hosted access is limited and requires approval.
2. In the repository, enable **Actions** and **Releases** under **Settings → Units → Overview**.
3. In **Settings → Actions → General**, allow workflows to write repository contents so the automatic
   `${{ forge.token }}` can create releases. Pull-request tokens remain read-only.

The workflow uses Codeberg's `codeberg-medium-lazy` hosted runner. The `-lazy` queue is intentionally
lower priority and may start later when the service is busy. CI does not need access to Intiface or
physical devices; it runs deterministic automated tests only.

## Preparing a beta

Set `mod_version` in `gradle.properties`; this is the single source of truth used by Gradle, mod
metadata, jar names, and the release workflow. Use a SemVer prerelease value such as
`1.0.0-beta.1`, then update `CHANGELOG.md` and run a clean local release-candidate build:

```powershell
.\gradlew.bat clean chiseledBuild --rerun-tasks --warning-mode all
```

Test the exact jars from every `versions/*/build/libs/` directory in their corresponding Minecraft
versions and loaders, walking the **acceptance matrix** in `docs/TESTING.md` and filling its cells as
you go (a green unit test or Intiface-simulator run does not count there). Commit the filled matrix
as part of the release preparation: the release tag then snapshots it, so a copy per release is not
needed; `git show v<version>:docs/TESTING.md` recovers any past release's results. Push the release
preparation, then confirm the normal push workflow succeeds on Codeberg before creating a release tag.

## Publishing

Create and push a tag that exactly matches the project version with a leading `v`. Use an
annotated tag with a `build(release): <version>` message, matching the existing tag history:

```powershell
git tag -a v1.0.0-beta.1 -m "build(release): 1.0.0-beta.1"
git push origin main
git push origin v1.0.0-beta.1
```

The workflow rejects a tag whose version differs from the built jar. Tags containing `-beta.` create
a Codeberg prerelease and upload every version-labelled jar plus `SHA256SUMS`. Stable releases are
intentionally not automatic until the beta pipeline has been proven.

## After a release: reset the acceptance matrix

The tag has snapshotted the filled acceptance matrix, so reset its cells back to `⬜` (and clear the
"Issues found" list of anything now fixed) in a small `chore: reset acceptance matrix for <next-version>`
commit. The matrix on `main` then tracks the current cycle in progress, while each release tag holds
what was verified at that release. Keep the row set current as features change; do not archive stale
copies of the checklist.
