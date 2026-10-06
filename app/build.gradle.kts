plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "ua.school.localmumble"
    compileSdk = 35
    defaultConfig {
        applicationId = "ua.school.localmumble"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0-beta1"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":server-core")) }
