import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}
ktlint { version.set("1.8.0") }

// Keep signing credentials outside the repository. Paths can be overridden
// with Gradle properties or environment variables for other build machines.
// The properties file is consumed by Gradle and is never copied into outputs.
val signingPropertiesPath =
    providers
        .gradleProperty("lavaConnectSigningProperties")
        .orElse(providers.environmentVariable("LAVA_CONNECT_SIGNING_PROPERTIES"))
        .orElse("${System.getProperty("user.home")}/.android/release-signing.properties")
        .get()
val releaseSigningFile = file(signingPropertiesPath)
val keystorePath =
    providers
        .gradleProperty("lavaConnectKeystore")
        .orElse(providers.environmentVariable("LAVA_CONNECT_KEYSTORE"))
        .orElse("${System.getProperty("user.home")}/.android/my-release-key.jks")
        .get()
val releaseSigning =
    Properties().apply {
        if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
    }

fun signingProperty(vararg names: String): String? =
    names.firstNotNullOfOrNull { name -> releaseSigning.getProperty(name)?.takeIf(String::isNotBlank) }

android {
    namespace = "com.lavacrafter.connect"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "com.lavacrafter.connect"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            if (releaseSigningFile.isFile) {
                signingConfig =
                    signingConfigs.create("userRelease").apply {
                        storeFile = file(keystorePath)
                        storePassword = signingProperty("storePassword", "keystorePassword")
                            ?: error("Missing storePassword in ~/.android/release-signing.properties")
                        keyAlias = signingProperty("keyAlias", "alias")
                            ?: error("Missing keyAlias in ~/.android/release-signing.properties")
                        keyPassword = signingProperty("keyPassword")
                            ?: error("Missing keyPassword in ~/.android/release-signing.properties")
                    }
            }
        }
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
