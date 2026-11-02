plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.garan.threshold.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

abstract class CopyDummyApkTask : DefaultTask() {
    @get:InputFile
    abstract val apkFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().file("dummy-app.apk").asFile
        apkFile.get().asFile.copyTo(target, overwrite = true)
    }
}

val copyDummyApkForAndroidTest =
    tasks.register<CopyDummyApkTask>("copyDummyApkForAndroidTest") {
        dependsOn(":fixtures:dummy-app:assembleDebug")
        apkFile.set(
            project(":fixtures:dummy-app").layout.buildDirectory.file("outputs/apk/debug/dummy-app-debug.apk"),
        )
        outputDir.set(layout.buildDirectory.dir("generated/dummy-apk-assets"))
    }

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(
            copyDummyApkForAndroidTest,
            CopyDummyApkTask::outputDir,
        )
    }
}

// Temporary: Host machine runs OpenJDK 25 (class major version 69).
// Robolectric 4.14 bundles ASM 9.6 which only supports up to Java 22.
// Scoped strictly to unit tests; remove once Robolectric bundles ASM >= 9.7+ or on standard JDK 17/21 CI.
configurations.matching { it.name.contains("UnitTest") }.all {
    resolutionStrategy {
        force(
            "org.ow2.asm:asm:9.10.1",
            "org.ow2.asm:asm-commons:9.10.1",
        )
    }
}

dependencies {
    api(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
