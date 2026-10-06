plugins {
    id("com.android.application")
}

android {
    namespace = "ua.school.localmumble"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "ua.school.localmumble"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-beta1"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += "**/libumurmur_server.so"
        resources.excludes += setOf("META-INF/**")
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("nativeJni"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Android only permits exec of packaged code, not binaries extracted into filesDir.
// Package a real PIE executable under lib/<ABI>/*.so so PackageManager extracts it
// into nativeLibraryDir. The suffix is for packaging; it is not a JNI library.
val nativeTasks = listOf("arm64-v8a", "armeabi-v7a", "x86_64").map { abi ->
    tasks.register("buildServer_${abi.replace('-', '_')}") {
        val source = file("src/main/cpp")
        val work = layout.buildDirectory.dir("nativeBuild/$abi")
        val output = layout.buildDirectory.dir("nativeJni/$abi")
        inputs.dir(source)
        outputs.file(output.map { it.file("libumurmur_server.so") })
        doLast {
            val sdk = androidComponents.sdkComponents.sdkDirectory.get().asFile
            val ndk = androidComponents.sdkComponents.ndkDirectory.get().asFile
            val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
            val cmake = File(sdk, "cmake/3.22.1/bin/cmake$suffix")
            val ninja = File(sdk, "cmake/3.22.1/bin/ninja$suffix")
            check(cmake.isFile) { "Install CMake 3.22.1 in Android Studio SDK Manager" }
            exec {
                commandLine(cmake, "-S", source, "-B", work.get().asFile,
                    "-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=$ninja",
                    "-DCMAKE_TOOLCHAIN_FILE=${File(ndk, "build/cmake/android.toolchain.cmake")}",
                    "-DANDROID_ABI=$abi", "-DANDROID_PLATFORM=android-26",
                    "-DCMAKE_BUILD_TYPE=Release", "-DSERVER_OUTPUT_DIR=${output.get().asFile}")
            }
            exec {
                commandLine(cmake, "--build", work.get().asFile, "--target", "umurmur_server", "-j", "4")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(nativeTasks) }
