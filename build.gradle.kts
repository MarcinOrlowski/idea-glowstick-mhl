import org.gradle.process.ExecOperations
import org.jetbrains.changelog.markdownToHTML
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.3.0"
    id("org.jetbrains.changelog") version "2.2.1"   // provides markdownToHTML
}

// gradle.properties is the single source of truth for plugin metadata.
// build.gradle.kts feeds these into `patchPluginXml`,; the source plugin.xml
// carries only structural tags (id, vendor, url, listeners, actions, etc).
fun prop(name: String) = providers.gradleProperty(name)

group = prop("pluginGroup").get()
version = prop("pluginVersion").get()

// Suffix appended to the plugin ZIP file name only (NOT the plugin <version>, which
// stays clean for the Marketplace). Built from git HEAD:
//   clean tree -> "<version>-<commitYYYYMMDD-HHMMSSZ>-<shortsha>"
//   dirty tree -> "...-<shortsha>+local-<buildYYYYMMDD-HHMMSSZ>"
// The trailing Z marks the timestamps as UTC (ISO-8601 Zulu).
// The commit date is deterministic; the extra build time on dirty builds keeps each
// local rebuild uniquely named and signals the artifact is NOT exactly that commit.
// Both timestamps are UTC (git via TZ=UTC + --date=format-local:, build time via
// ZoneOffset.UTC) so file names are identical regardless of the builder's timezone.
//
// This is a ValueSource (not providers.exec / a plain provider) on purpose: Gradle
// re-runs value sources every build, so clean->dirty is detected and dirty rebuilds get
// a fresh build time. A plain exec provider is frozen by the configuration cache and
// would report stale git state. On a clean tree the returned string is stable, so the
// configuration cache is still reused; only dirty builds reconfigure (fresh timestamp).
abstract class ArchiveVersionSource : ValueSource<String, ArchiveVersionSource.Params> {
    interface Params : ValueSourceParameters {
        val baseVersion: Property<String>
    }

    @get:Inject
    abstract val exec: ExecOperations

    private fun git(vararg args: String): String {
        val out = ByteArrayOutputStream()
        return try {
            exec.exec {
                commandLine(listOf("git") + args)
                standardOutput = out
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
                environment("TZ", "UTC")   // so git's --date=format-local: renders UTC
            }
            out.toString().trim()
        } catch (e: Exception) {
            ""   // git missing / not a repo -> no suffix parts
        }
    }

    override fun obtain(): String {
        val version = parameters.baseVersion.get()
        val sha = git("rev-parse", "--short", "HEAD")
        val stamp = git(
            "show",
            "-s",
            "--format=%cd",
            "--date=format-local:%Y%m%d-%H%M%SZ",
            "HEAD"
        )
        val dirty = git("status", "--porcelain").isNotEmpty()
        val core = listOf(version, stamp, sha).filter { it.isNotEmpty() }.joinToString("-")
        if (!dirty) return core

        val buildTime = LocalDateTime
            .now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'"))
        return "$core+local-$buildTime"
    }
}

val archiveVersionSuffix = providers.of(ArchiveVersionSource::class) {
    parameters.baseVersion = prop("pluginVersion")
}

// Bake the full build string into a bundled resource so the plugin can show it in its
// settings header at runtime (the plugin <version> itself stays clean at "0.1.0").
// Regenerated whenever the value changes (dirty builds); stable + up-to-date on a clean tree.
val buildInfoDir = layout.buildDirectory.dir("generated/buildInfo")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outFile = buildInfoDir.map { it.file("glowstick-build.properties") }
    val cleanVersion = prop("pluginVersion")
    val fullBuild = archiveVersionSuffix
    inputs.property("version", cleanVersion)
    inputs.property("build", fullBuild)
    outputs.file(outFile)
    doLast {
        outFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=${cleanVersion.get()}\nbuild=${fullBuild.get()}\n")
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against the oldest supported platform
        // Forward-compatible through 2026.x (build 261)
        intellijIdeaCommunity(prop("platformVersion").get())
        pluginVerifier()
    }
    // Plain JUnit 5 unit tests for the pure-logic classes (no live IDE needed).
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = prop("pluginName")
        version = prop("pluginVersion")

        // Marketplace description is rendered HTML file
        description =
            providers.fileContents(layout.projectDirectory.file("README.plugin.md"))
                .asText.map { markdownToHTML(it) }

        // Change-notes = the newest version block from CHANGES.md, rendered to HTML.
        // Only the latest "## ..." section ships (not the whole history); the section
        // header - including your "vX.Y.Z (date)" format and its release date - is kept
        // verbatim, so no external changelog parser reformats it.
        changeNotes =
            providers.fileContents(layout.projectDirectory.file("CHANGES.md"))
                .asText.map { md ->
                    val afterFirst = md.substringAfter("## ", "")
                    val latest =
                        if (afterFirst.isBlank()) md
                        else "## " + afterFirst.substringBefore("\n## ")
                    markdownToHTML(latest.trim())
                }

        ideaVersion {
            sinceBuild = prop("pluginSinceBuild")   // oldest supported; matches platformVersion
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    jvmToolchain(17)   // 2024.1 baseline: runs on JBR 17, bytecode must stay 17
}

// The generated build-info file ships as a classpath resource (/glowstick-build.properties).
sourceSets.named("main") {
    resources.srcDir(buildInfoDir)
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Name the distribution ZIP via ArchiveVersionSource (see above). Only the archive
    // name changes; signPlugin/publishPlugin follow buildPlugin.archiveFile automatically.
    buildPlugin {
        archiveVersion = archiveVersionSuffix
    }

    processResources {
        dependsOn(generateBuildInfo)
    }

    // Skip the slow/noisy headless-IDE step (only pre-indexes settings for Marketplace search).
    // The two downstream tasks must be disabled as well, otherwise a clean build fails looking
    // for the directory buildSearchableOptions would have produced.
    buildSearchableOptions { enabled = false }
    prepareJarSearchableOptions { enabled = false }
    jarSearchableOptions { enabled = false }
}
