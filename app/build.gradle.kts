import java.io.RandomAccessFile
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Local-only signing configuration. keystore.properties and *.jks are ignored by git.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.ogrchatai.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ogrchatai.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // The bundled sherpa-onnx runtime and llama.cpp both ship 64-bit libraries.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// ---------------------------------------------------------------------------
// android-ai-kit 1.0.0 calls common_chat_format_single(..., use_jinja=false)
// for every message. Its legacy formatter rejects the Gemma 4 Jinja template
// with an exception that escapes through JNI and aborts the process. The two
// patches below change the corresponding boolean argument to true before the
// AAR native libraries are packaged. The byte context is verified so a future
// AiKit update fails loudly instead of silently reintroducing the crash.
// ---------------------------------------------------------------------------
val aiKitJinjaPatches: Map<String, Triple<Long, ByteArray, ByteArray>> = mapOf(
    // mov w24, w4  ->  movz w24, #1
    "arm64-v8a" to Triple(
        0x26029cL,
        byteArrayOf(
            0xf8.toByte(), 0x03, 0x04, 0x2a,
            0xf5.toByte(), 0x03, 0x03, 0x2a
        ),
        byteArrayOf(
            0x38, 0x00, 0x80.toByte(), 0x52,
            0xf5.toByte(), 0x03, 0x03, 0x2a
        )
    ),
    // mov ebx, r9d  ->  push 1; pop rbx
    "x86_64" to Triple(
        0x26b434L,
        byteArrayOf(
            0x44, 0x89.toByte(), 0xcb.toByte(),
            0x45, 0x89.toByte(), 0xc7.toByte()
        ),
        byteArrayOf(
            0x6a, 0x01, 0x5b,
            0x45, 0x89.toByte(), 0xc7.toByte()
        )
    )
)

tasks.matching {
    it.name == "mergeDebugNativeLibs" || it.name == "mergeReleaseNativeLibs"
}.configureEach {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    doLast {
        val mergedRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs")
            .get()
            .asFile
        val libraries = mergedRoot
            .walkTopDown()
            .filter { it.isFile && it.name == "libllama-common.so" }
            .toList()
        if (libraries.isEmpty()) {
            throw GradleException("AiKit patch: libllama-common.so was not merged")
        }

        var changed = 0
        var alreadyPatched = 0
        libraries.forEach { file ->
            val abi = file.parentFile?.name ?: return@forEach
            val patch = aiKitJinjaPatches[abi] ?: return@forEach
            val (offset, expected, replacement) = patch
            RandomAccessFile(file, "rw").use { raf ->
                if (raf.length() < offset + expected.size) {
                    throw GradleException(
                        "AiKit patch: ${file.name} ($abi) is shorter than expected"
                    )
                }
                raf.seek(offset)
                val actual = ByteArray(expected.size)
                raf.readFully(actual)
                when {
                    actual.contentEquals(replacement) -> alreadyPatched++
                    actual.contentEquals(expected) -> {
                        raf.seek(offset)
                        raf.write(replacement)
                        changed++
                    }
                    else -> throw GradleException(
                        "AiKit patch: unexpected bytes in ${file.name} ($abi). " +
                            "The AiKit version or ABI layout changed."
                    )
                }
            }
        }

        if (changed + alreadyPatched == 0) {
            throw GradleException("AiKit patch: no supported ABI library was found")
        }
        logger.lifecycle(
            "AiKit Jinja patch: $changed library file(s) changed, " +
                "$alreadyPatched already patched"
        )
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security
    implementation(libs.androidx.security.crypto)

    // On-device LLM inference (llama.cpp)
    implementation("io.github.its-hazratbilal:android-ai-kit-chat:1.0.0") {
        exclude(group = "androidx.core", module = "core-ktx")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    // On-device neural TTS (Supertonic 3 through sherpa-onnx)
    implementation(files("libs/sherpa-onnx-2.0.1-min.aar"))

    // DocumentFile
    implementation(libs.androidx.documentfile)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
