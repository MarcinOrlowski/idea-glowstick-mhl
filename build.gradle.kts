import org.gradle.process.ExecOperations
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
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
val pluginXmlFile = layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outFile = buildInfoDir.map { it.file("glowstick-build.properties") }
    val cleanVersion = prop("pluginVersion")
    val cleanName = prop("pluginName")
    val fullBuild = archiveVersionSuffix
    val xml = pluginXmlFile
    inputs.property("version", cleanVersion)
    inputs.property("name", cleanName)
    inputs.property("build", fullBuild)
    inputs.file(xml)
    outputs.file(outFile)
    doLast {
        // vendor + url live in the (structural) plugin.xml; name + version in gradle.properties.
        // Bundling them lets the settings banner read metadata from this resource instead of
        // the internal PluginManager.getPluginByClass API.
        val text = xml.asFile.readText()
        val url = Regex("""<idea-plugin[^>]*\burl="([^"]*)"""")
            .find(text)?.groupValues?.get(1).orEmpty()
        val vendor = Regex("""<vendor[^>]*>(.*?)</vendor>""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)?.trim().orEmpty()
        outFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    append("version=").append(cleanVersion.get()).append('\n')
                    append("name=").append(cleanName.get()).append('\n')
                    append("vendor=").append(vendor).append('\n')
                    append("url=").append(url).append('\n')
                    append("build=").append(fullBuild.get()).append('\n')
                }
            )
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
        // Make `verifyPlugin` FAIL locally on actionable categories the Marketplace reports
        // (by default only hard compatibility problems fail; the rest are merely written to the
        // report and easy to miss). Full report is always at build/reports/pluginVerifier/.
        //
        // INTERNAL_API_USAGES is deliberately NOT in this list: the only internal call left is
        // ProjectWindowCustomizerService.getProjectColorToCustomize - the per-project color
        // source, which has no public equivalent and IS the plugin's feature. It's still
        // reported (and the Marketplace flags it as a non-blocking warning), just not fatal.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            // Left commented on purpose (see above): re-enable to fail on internal-API usage
            // once JetBrains provides a public API for the per-project color, or to catch NEW
            // internal usages during development.
            // FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            FailureLevel.DEPRECATED_API_USAGES,
            FailureLevel.INVALID_PLUGIN,
        )
        ides {
            // Downloadable IntelliJ IDEA Community releases to verify against (via
            // intellij-plugin-verifier: `./gradlew verifyPlugin`). Pinned explicitly, not
            // select{}/recommended(). NOTE: 2025.3+ and 2026.x Community are NOT published to
            // the maven-style repo this (old, 2.3.0) IJP Gradle plugin resolves from
            // (ideaIC:2026.1 -> 404), so they can't be listed here - resolution fails for the
            // whole task on any 404. To cover those either upgrade
            // `org.jetbrains.intellij.platform`, or use the local install below. Prune any
            // version that 404s. First run downloads each IDE (several GB), cached afterwards.
            listOf("2024.1", "2024.2", "2024.3", "2025.1", "2025.2")
                .forEach { ide(IntelliJPlatformType.IntellijIdeaCommunity, it) }

            // Also verify against a locally-installed IDE (no download) when present - this is
            // how we cover recent builds (e.g. 2026.2, where the Marketplace findings appear)
            // that aren't downloadable above. Path is machine-specific; guarded so machines
            // without it just skip. Adjust to your install (Toolbox/snap/tarball) as needed.
            val localIde = file("/snap/intellij-idea/current")
            if (localIde.resolve("product-info.json").exists()) local(localIde)
        }
    }
}

kotlin {
    jvmToolchain(17)   // 2024.1 baseline: runs on JBR 17, bytecode must stay 17
    compilerOptions {
        // Use real JVM default methods instead of Kotlin's delegating bridges, so a class
        // implementing a Java interface (e.g. DynamicPluginListener) does NOT generate an
        // override for every default method - that dragged in the deprecated
        // `checkUnloadPlugin`. Also the JetBrains-recommended mode for platform plugins.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
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
