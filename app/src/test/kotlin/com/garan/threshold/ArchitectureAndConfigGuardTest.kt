package com.garan.threshold

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ArchitectureAndConfigGuardTest {

    private val rootDir: File by lazy {
        val current = File(".").canonicalFile
        if (current.name == "app") current.parentFile else current
    }

    @Test
    fun composeStabilityConfig_existsAndCoversCoreDomainPackages() {
        val stabilityFile = File(rootDir, "app/compose_stability.conf")
        assertThat(stabilityFile.exists()).isTrue()

        val lines = stabilityFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
        assertThat(lines).containsAtLeast(
            "com.garan.threshold.core.model.**",
            "com.garan.threshold.core.port.**",
            "com.garan.threshold.core.search.**",
        )
    }

    @Test
    fun productionCode_neverCallsBlockingThreadSleep() {
        val sourceDirs = listOf(
            File(rootDir, "core/src/main/kotlin"),
            File(rootDir, "data/src/main/kotlin"),
            File(rootDir, "app/src/main/kotlin"),
        )

        val violations = mutableListOf<String>()

        for (dir in sourceDirs) {
            assertThat(dir.exists()).isTrue()
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (line.contains("Thread.sleep(")) {
                                violations.add("${file.relativeTo(rootDir)}:${index + 1} -> ${line.trim()}")
                            }
                        }
                    }
                }
        }

        assertThat(violations).isEmpty()
    }

    @Test
    fun buildGradle_defaultsToRoborazziVerifyWhenRecordPropertyNotSet() {
        val buildGradle = File(rootDir, "app/build.gradle.kts")
        assertThat(buildGradle.exists()).isTrue()
        val content = buildGradle.readText()
        assertThat(content).contains("roborazzi.test.verify")
        assertThat(content).contains("compose_stability.conf")
    }

    @Test
    fun manifest_configuresAdaptiveLauncherIconsAndStringLabel() {
        val manifestFile = File(rootDir, "app/src/main/AndroidManifest.xml")
        assertThat(manifestFile.exists()).isTrue()
        val manifest = manifestFile.readText()
        assertThat(manifest).contains("""android:icon="@mipmap/ic_launcher"""")
        assertThat(manifest).contains("""android:roundIcon="@mipmap/ic_launcher_round"""")
        assertThat(manifest).contains("""android:label="@string/app_name"""")

        val mipmapAnyDpi = File(rootDir, "app/src/main/res/mipmap-anydpi-v26")
        assertThat(File(mipmapAnyDpi, "ic_launcher.xml").exists()).isTrue()
        assertThat(File(mipmapAnyDpi, "ic_launcher_round.xml").exists()).isTrue()

        val drawableDir = File(rootDir, "app/src/main/res/drawable")
        assertThat(File(drawableDir, "ic_launcher_background.xml").exists()).isTrue()
        assertThat(File(drawableDir, "ic_launcher_foreground.xml").exists()).isTrue()
        assertThat(File(drawableDir, "ic_launcher_monochrome.xml").exists()).isTrue()
        assertThat(File(drawableDir, "ic_threshold_caret.xml").exists()).isTrue()

        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
            val dir = File(rootDir, "app/src/main/res/mipmap-$density")
            assertThat(File(dir, "ic_launcher.png").exists()).isTrue()
            assertThat(File(dir, "ic_launcher_round.png").exists()).isTrue()
        }
    }

    @Test
    fun uiScreens_neverDefineInlineHexColorsOrAsciiIconsAndUseNavigation3() {
        val appSourceDir = File(rootDir, "app/src/main/kotlin")
        val colorViolations = mutableListOf<String>()
        val asciiIconViolations = mutableListOf<String>()

        appSourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (!relPath.endsWith("ui/theme/Color.kt") && line.contains("Color(0x")) {
                            colorViolations.add("$relPath:${index + 1} -> ${line.trim()}")
                        }
                        if (line.contains("\"▲\"") || line.contains("\"▼\"") || line.contains("Manage →")) {
                            asciiIconViolations.add("$relPath:${index + 1} -> ${line.trim()}")
                        }
                    }
                }
            }

        assertThat(colorViolations).isEmpty()
        assertThat(asciiIconViolations).isEmpty()

        val mainActivityContent = File(appSourceDir, "com/garan/threshold/MainActivity.kt").readText()
        assertThat(mainActivityContent).contains("NavDisplay")
        assertThat(mainActivityContent).contains("rememberNavBackStack")
    }
}
