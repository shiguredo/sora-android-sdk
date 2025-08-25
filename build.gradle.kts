plugins {
    id("com.github.ben-manes.versions") version "0.46.0"
    id("org.ajoberstar.grgit") version "5.3.2"
    id("org.jetbrains.dokka") version "1.9.20"
}

buildscript {
    extra["kotlin_version"] = "1.9.25"
    extra["libwebrtc_version"] = "138.7204.0.2"
    extra["dokka_version"] = "1.9.20"

    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("com.github.dcendents:android-maven-gradle-plugin:2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
        classpath("org.ajoberstar.grgit:grgit-gradle:5.3.2")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.20")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.46.0")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:11.3.1")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    resolutionStrategy {
        componentSelection {
            all {
                val rejected = listOf("alpha", "beta", "rc", "M").any { qualifier ->
                    candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-]*"))
                }
                if (rejected) {
                    reject("Release candidate")
                }
            }
        }
    }
}

