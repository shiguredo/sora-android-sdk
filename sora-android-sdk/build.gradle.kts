import org.ajoberstar.grgit.Grgit

plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "com.github.shiguredo"

val grgit = Grgit.open(mapOf("currentDir" to rootProject.projectDir))
val libwebrtcVersion = rootProject.extra["libwebrtc_version"] as String

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        buildConfigField("String", "REVISION", "\"${grgit.head().abbreviatedId}\"")
        buildConfigField("String", "LIBWEBRTC_VERSION", "\"$libwebrtcVersion\"")
    }

    lint {
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
        targetSdk = 36
        unitTests.isIncludeAndroidResources = true
    }

    // AGP 8.0 からモジュールレベルの build script 内に namespace が必要になった
    // 参考: https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes#namespace-dsl
    namespace = "jp.shiguredo.sora.sdk"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    finalizedBy("ktlintFormat")
    kotlinOptions {
        jvmTarget = "1.8"
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
    version.set("0.45.2")
    android.set(false)
    outputToConsole.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    ignoreFailures.set(false)
}

val kotlinVersion = rootProject.extra["kotlin_version"] as String

dependencies {
    api("com.github.shiguredo:shiguredo-webrtc-android:$libwebrtcVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    // required by "signaling" part
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // kotlinx.coroutines requires kotlin 2.1.0
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // required by "rtc" part
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.15.1") {
        exclude(group = "com.google.auto.service", module = "auto-service")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
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
