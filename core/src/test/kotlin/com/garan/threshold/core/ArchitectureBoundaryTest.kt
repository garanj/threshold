package com.garan.threshold.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {

    @Test
    fun `core module must contain zero android imports`() {
        val coreSourceDir = File("src/main/kotlin")
        assertThat(coreSourceDir.exists()).isTrue()

        val kotlinFiles = coreSourceDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertThat(kotlinFiles).isNotEmpty()

        val offendingImports = mutableListOf<String>()

        kotlinFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import android.")) {
                        offendingImports.add("${file.name}:${index + 1} -> $trimmed")
                    }
                }
            }
        }

        assertThat(offendingImports).isEmpty()
    }
}
