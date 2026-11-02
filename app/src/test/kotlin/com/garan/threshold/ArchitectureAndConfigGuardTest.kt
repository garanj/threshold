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
}
