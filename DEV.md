# Developer Guide

How to build, run, test, and release **GlowStick MHL** - an IntelliJ IDEA plugin that paints a
per-project colored frame around the IDE window so multiple open projects are visually distinct.
This guide assumes no prior familiarity with the project or with IntelliJ plugin development.

---

## 1. What you need

- **JDK 17** on your machine (a 17-compatible JDK; the build's toolchain targets 17 because the
  plugin's baseline IDE, 2024.1, runs on JBR 17). Gradle 9 itself runs on JDK 17–21.
- **Git**.
- That's it - **do not install Gradle**. The project ships the Gradle wrapper (`./gradlew`, pinned
  to Gradle 9.4.1). Always invoke Gradle through the wrapper.

> The Gradle wrapper (`gradle/wrapper/gradle-wrapper.jar` + `.properties`, `gradlew`,
> `gradlew.bat`) is required to build. If it's missing you'll see
> `Error: Unable to access jarfile .../gradle-wrapper.jar` - restore it with
> `gradle wrapper --gradle-version 9.4.1` from a system Gradle, or check out a branch that
> has it.

## 2. Quick start

```bash
git clone <repo-url>
cd idea-glowstick-mhl
./gradlew runIde        # launches a sandbox IDE with the plugin installed
```

`runIde` downloads the target IDE (~1 GB) on first run and caches it. A second IDE window opens with
the plugin active - this is your main manual-test loop.

## 3. Build & run commands

| Command                   | What it does                                                                  |
|---------------------------|-------------------------------------------------------------------------------|
| `./gradlew runIde`        | Launch a sandbox IDE with the plugin installed. **Primary manual-test loop.** |
| `./gradlew buildPlugin`   | Produce the installable ZIP under `build/distributions/`.                     |
| `./gradlew test`          | Run the fast JUnit 5 unit tests (no IDE needed).                              |
| `./gradlew verifyPlugin`  | Run the JetBrains Plugin Verifier (API-compatibility check). See §6.          |
| `./gradlew publishPlugin` | Publish to JetBrains Marketplace (task exists but not wired yet). See §8.     |

Notes:

- The **configuration cache** and **build cache** are on (`gradle.properties`), so repeat builds are
  fast.
- `buildSearchableOptions`, `prepareJarSearchableOptions`, and `jarSearchableOptions` are
  deliberately **disabled** (that headless-IDE step is slow/noisy). Leave all three disabled
  together - disabling only one breaks `clean buildPlugin`.

## 4. Testing

### Unit tests (`./gradlew test`)

Only the **pure-logic** classes are unit-tested - they don't touch the IDE runtime, so the tests are
fast and run in CI:

- `FrameOpacityModelTest` - the opacity-curve math (row counts, easing, clamping, master scaling,
  segment chaining).
- `ValueProviderTest` - enum parsing / fallback.
- `ProjectColorSettingsTest` - defaults, value clamping, state round-trip.

Add tests here for any new pure-logic code.

### Manual testing (the Swing UI)

The painter, the settings page, and the interactive preview manipulate **live Swing frames**
and **cannot** be unit-tested - verify them by hand:

```bash
./gradlew runIde
```

Then, when you change painting/positioning/UI logic, check:

1. Open a project; assign it a color (project widget in the top bar, or the welcome-screen project
   icon → color).
2. The frame appears in that color. Toggle it via **Tools → Draw GlowStick window frame**.
3. In **Settings → Appearance & Behavior → GlowStick Frame**: change edges, widths, opacities,
   interpolation; the live preview updates.
4. Drag the three preview handles - vertical changes opacity, dragging the middle (join)
   handle horizontally changes the outer-segment width.
5. **Resize** the IDE window - the frame stays aligned on all four edges.
6. **IDE panes still drag** (split dividers, tool-window edges) while the frame is on - the frame
   must not swallow mouse events.

You can also install the built ZIP into a real IDE (§7) instead of using the sandbox.

## 5. Plugin Verifier - checking API compatibility

`./gradlew verifyPlugin` runs
the [intellij-plugin-verifier](https://github.com/JetBrains/intellij-plugin-verifier)
against a set of IDEs, catching **deprecated / removed / incompatible API** usage. It is configured
(in `build.gradle.kts`, `pluginVerification { ides { … } }`) to check an explicit list of IntelliJ
IDEA Community releases from the baseline (2024.1) up to the latest.

- **`verifyPlugin` is set to FAIL** on scheduled-for-removal, deprecated, and hard
  compatibility problems (via `failureLevel`) — so those surface locally the same way the
  Marketplace flags them. `INTERNAL_API_USAGES` is intentionally *not* in the fail set: the one
  remaining internal call (`getProjectColorToCustomize`, the per-project color source) has no
  public equivalent and is the plugin's feature — it's still reported (and the Marketplace warns
  on it, non-blocking), just not fatal. Full per-IDE report: `build/reports/pluginVerifier/<ide>/report.md`.
- **First run downloads each IDE** in the list (several GB total), then caches them.
- The list is pinned explicitly (not `recommended()`/`select{}`) **on purpose**: the JetBrains
  release feed sometimes lists a version whose Community binary isn't published to the download repo
  (e.g. `idea:ideaIC:2025.3` → 404), which breaks feed-driven selection. If a newly added version
  404s, just remove it from the `listOf(...)` in `build.gradle.kts`.
- **Offline alternative:** verify against an IDE you already have installed - uncomment the
  `local(file("…"))` line and point it at an install directory. No downloads.

You can also run the verifier standalone: download `verifier-cli-<ver>-all.jar` from the verifier's
releases and run
`java -jar verifier-cli.jar check-plugin build/distributions/glowstick-*.zip /path/to/an/IDE`.

## 6. Installing a locally-built ZIP into a real IDE

```bash
./gradlew buildPlugin
# then in your IDE:
# Settings → Plugins → gear icon → "Install Plugin from Disk…" →
#   build/distributions/glowstick-mhl-<version>-….zip → Restart
```

Useful for testing against your actual IDE version rather than the sandbox.

## 7. Metadata, versioning & release

**`gradle.properties` is the single source of truth** for plugin metadata:

- `pluginVersion`, `pluginName`, `pluginGroup`, `pluginSinceBuild`, `platformVersion`.
- `build.gradle.kts` reads these and `patchPluginXml` injects `<name>`/`<version>`/
  `<description>`/`<change-notes>`/`<idea-version since-build>` into the packaged
  `plugin.xml` at build time. The **source** `plugin.xml` intentionally omits those tags - don't add
  them back there.
- **To bump the version, edit `pluginVersion` in `gradle.properties` only.**

Marketplace text is generated from Markdown at build time:

- **Description** ← `README.plugin.md` (rendered to HTML).
- **Change notes** ← the newest `## …` section of `CHANGES.md` only. Add a new
  `## vX.Y.Z (YYYY-MM-DD)` section at the top for each release.

Publishing: the `publishPlugin` task exists (from the IntelliJ Platform Gradle Plugin), but
**publishing is not wired up yet** - `build.gradle.kts` has no `publishing { token = … }` (nor a
signing) block. To enable it, add a publishing block that reads a Marketplace token from an
environment variable, optionally add plugin signing, then run `./gradlew publishPlugin`. Until then,
distribute the ZIP from `buildPlugin` (install-from-disk, §7).

If the Marketplace shows the plugin as "not compatible" with a newer IDE, it's usually a stale
published/installed copy - the current build declares `since-build=241` with no upper bound and is
compatible with everything from 2024.1 up. Rebuild/reinstall or re-publish.

## 8. Build ZIP file name

`buildPlugin` names the archive with build coordinates appended (the plugin `<version>`
itself stays clean for the Marketplace):

```text
glowstick-mhl-<version>-<commitUTCstamp>Z-<shortSha>[+local-<buildUTCstamp>Z].zip
```

- Clean tree → `…-<version>-<commitStamp>Z-<sha>.zip`.
- Dirty tree (uncommitted changes) → adds `+local-<buildStamp>Z`, so a local build never claims to
  be exactly a commit. All timestamps are UTC.

The same full string is baked into a bundled resource and shown (click-to-copy) in the settings
header, so a user can report exactly which build they're running.

## 9. Gotchas worth knowing before you touch the code

- **Frame painting must run on the EDT.** Reading the project color asserts Event-Dispatch-
  Thread access, and the applier is called from background triggers - so it wraps all frame work in
  `SwingUtilities.invokeLater {}`. Never read the color or touch the frame off-EDT.
- **The glass-pane frame must be mouse-transparent.** A full-window glass-pane child with default
  hit-testing would swallow every mouse event and break IDE pane dragging. The border component
  reports `contains() == false` (it still paints).
- **The per-project color comes from an internal platform API**
  (`ProjectWindowCustomizerService`) - not stable public API. The Plugin Verifier flags it, and a
  future IDE could move/rename it.
- **Kotlin builds with `-Xjvm-default=all`** (in `build.gradle.kts`). This makes a Kotlin class that
  implements a Java interface use real JVM default methods instead of generating a delegating
  override for every default - which had dragged in a deprecated API
  (`DynamicPluginListener.checkUnloadPlugin`). It's also JetBrains' recommended mode.
- **Some platform helpers are binary-unstable across IDE releases** (e.g. `IconUtil.colorize`
  gained/lost overloads), which can throw `NoSuchMethodError` at runtime even though it compiles.
  When a settings page hangs on an endless "please wait" spinner, grep the real IDE's log
  (`~/.cache/JetBrains/<IDE><year>.<v>/log/idea.log`) for the plugin package.

## 10. CI

`.github/workflows/ci.yml` runs `test` + `buildPlugin` on pull requests and pushes to
`master`/`dev`, and uploads the built ZIP as an artifact.
