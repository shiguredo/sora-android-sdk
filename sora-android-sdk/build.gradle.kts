import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
}

group = "com.github.shiguredo"

val gitRevision = runCatching {
    val stdout = ByteArrayOutputStream()
    val result = project.exec {
        workingDir = rootProject.projectDir
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
        isIgnoreExitValue = true
    }

    if (result.exitValue == 0) {
        stdout.toString(Charsets.UTF_8.name()).trim()
    } else {
        logger.warn("Failed to resolve Git revision, exit code ${result.exitValue}")
        "unknown"
    }
}.getOrElse { error ->
    logger.warn("Failed to resolve Git revision", error)
    "unknown"
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        buildConfigField("String", "REVISION", "\"$gitRevision\"")
        buildConfigField("String", "LIBWEBRTC_VERSION", "\"${libs.versions.libwebrtc.get()}\"")
    }

    lint {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaCompatibility.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaCompatibility.get())
    }

    buildFeatures {
        // AGP 8.0 からデフォルトで false になった
        // このオプションが true でないと、defaultConfig に含まれている
        // buildConfigField オプションが無効になってしまうため、true に設定する
        // 参考: https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes#default-changes
        buildConfig = true
    }

    buildTypes {
        // Android Studio でのデバッグビルドタイプはデフォルトで debuggable true としてビルドされるため
        // AGP Upgrade Assistant によって debug ブロックは削除された。
        getByName("release") {
        }
    }

    testOptions {
        targetSdk = libs.versions.targetSdk.get().toInt()
        unitTests.isIncludeAndroidResources = true
    }

    // AGP 8.0 からモジュールレベルの build script 内に namespace が必要になった
    // 参考: https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes#namespace-dsl
    namespace = "jp.shiguredo.sora.sdk"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    finalizedBy("ktlintFormat")
    kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()
    }
}

tasks.dokkaHtml.configure {
    // デフォルトの出力先は "${buildDir}/dokka". 変更したいときにコメントアウトを行う.
    // outputDirectory.set(File("${buildDir}/dokka"))
    moduleName.set("sora-android-sdk")
    // "default" を指定すると $USER_HOME/.cache/dokka を使用するとあるが実際には "${projectDir}/default" を見てしまうのでコメントアウトしている.
    // cacheRoot.set(file("default"))

    dokkaSourceSets {
        named("main") {
            reportUndocumented.set(true)
            includes.from(files("packages.md"))

            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(uri("https://github.com/shiguredo/sora-android-sdk/tree/master/sora-android-sdk/src/main/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

ktlint {
    // ktlint バージョンは以下の理由によりハードコーディングしている
    // - Gradleの設計上の制限: プラグイン設定の評価タイミングが早すぎる
    // - ktlint-gradleプラグインの仕様: 動的な値の解決に対応していない
    // - Version Catalogの制約: プラグイン設定フェーズでは利用不可
    version.set("0.45.2")
    android.set(false)
    outputToConsole.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    ignoreFailures.set(false)
}

dependencies {
    // Kotlin BOMで整合性を保証
    implementation(platform(libs.kotlin.bom))

    api(libs.shiguredo.webrtc.android)

    implementation(libs.kotlin.reflect)

    // required by "signaling" part
    implementation(libs.gson)
    implementation(libs.okhttp)
    // kotlinx.coroutines requires kotlin 2.1.0
    implementation(libs.kotlinx.coroutines.android)

    // required by "rtc" part
    implementation(libs.bundles.reactive)

    testImplementation(libs.bundles.testBase)
    testImplementation(libs.robolectric) {
        exclude(group = "com.google.auto.service", module = "auto-service")
    }
}

configurations.all {
    resolutionStrategy {
        cacheDynamicVersionsFor(0, "seconds")
        cacheChangingModulesFor(0, "seconds")
    }
}

tasks.register<Jar>("sourcesJar") {
    // classifier は archiveClassifier に置き換えられた
    // https://docs.gradle.org/7.6/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:classifier
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
}

tasks.whenTaskAdded {
    // kotlin 1.9 に上げたタイミングで、JitPack で generateMetadataFileForSora-android-sdkPublication が
    // sourcesJar より先に実行されるようになってしまい ビルドエラーが発生した。
    // 、generateMetadataFileForSora-android-sdkPublication は sourcesJar の出力を使用するためである。
    // この問題に対処するために、generateMetadataFileForSora-android-sdkPublication が sourcesJar に依存していることを明示的に宣言する。
    if (name == "generateMetadataFileForSora-android-sdkPublication") {
        dependsOn("sourcesJar")
    }
}

artifacts {
    archives(tasks.getByName("sourcesJar"))
}
