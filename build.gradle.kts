plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.grgit)
    alias(libs.plugins.dokka)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.maven) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// 不安定バージョンを除外する設定
fun isNonStable(version: String): Boolean {
    val qualifiers = listOf("alpha", "beta", "rc", "M")
    return qualifiers.any { qualifier ->
        version.matches(Regex("(?i).*[.-]$qualifier[.\\d-]*"))
    }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    resolutionStrategy {
        componentSelection {
            all {
                if (isNonStable(candidate.version)) {
                    reject("Release candidate")
                }
            }
        }
    }
}