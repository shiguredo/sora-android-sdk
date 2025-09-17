pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":sora-android-sdk")

val dirFile = file("./include_app_dir.txt")
if (dirFile.exists()) {
    val includeAppDirList = dirFile.readLines()
    includeAppDirList.forEach { includeAppDir ->
        if (includeAppDir.startsWith("#")) return@forEach

        logger.info("includeAppDir = $includeAppDir")
        file(includeAppDir).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()) {
                include(":${dir.name}")
                project(":${dir.name}").projectDir = dir
            }
        }
    }
}