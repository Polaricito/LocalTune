import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun String.asBuildConfigString(): String =
    "\"${
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }\""

val releaseKeystoreFile = file("keystore/release.keystore")
// Stable self-signed key committed to the repository so every build keeps the
// same signature and existing installations can be updated in place.
val releaseStorePassword = "archivetune"
val releaseKeyAlias = "archivetune"
val releaseKeyPassword = "archivetune"
val hasReleaseSigningConfig = releaseKeystoreFile.isFile
android {
    namespace = "moe.rukamori.archivetune"
    compileSdk = 37
    ndkVersion = "30.0.16248370"
    compileSdkMinor = 2

    defaultConfig {
    applicationId = "moe.rukamori.archivetune"
        minSdk = 26
        targetSdk = 37
        versionCode = 146
        versionName = "15.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val nightlyBuildHash =
            (
                localProperties.getProperty("NIGHTLY_BUILD_HASH")
                    ?: System.getenv("NIGHTLY_BUILD_HASH")
                    ?: ""
                ).trim()
        buildConfigField("String", "NIGHTLY_BUILD_HASH", "\"$nightlyBuildHash\"")

        val nightlyVersionName =
            providers.gradleProperty("nightlyVersionName").orNull?.trim().orEmpty()
        if (nightlyVersionName.isNotEmpty()) {
            versionName = nightlyVersionName
        }
        buildConfigField("String", "DISTRIBUTION", "\"gms\"")
    }

    flavorDimensions += listOf("distribution", "device", "abi")
    productFlavors {
        create("gms") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("String", "DISTRIBUTION", "\"gms\"")
        }
        create("foss") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
        }
        create("mobile") {
            dimension = "device"
            buildConfigField("String", "DEVICE", "\"mobile\"")
        }
        create("tv") {
            dimension = "device"
            buildConfigField("String", "DEVICE", "\"tv\"")
        }
        create("automotive") {
            dimension = "device"
            minSdk = 28
            buildConfigField("String", "DEVICE", "\"automotive\"")
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("boolean", "GATEKEEPER_ENABLED", "false")
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        create("nightly") {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            applicationIdSuffix = ".nightly"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "LEAK_CANARY_TOGGLE_AVAILABLE", "true")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = false
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
        }
    }

}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.work.runtime)
    implementation("androidx.browser:browser:1.10.0")

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    compileOnly("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)
    add("nightlyImplementation", libs.leakcanary.android)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.material3)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.palette)
    implementation(libs.androidsvg)
    implementation(libs.aboutlibraries.core)
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    implementation(libs.shimmer)

    // Glance Widget support
    implementation("androidx.glance:glance:1.2.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-ui-compose:${libs.versions.media3.get()}")

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.re2j)
    annotationProcessor(libs.kotlin.metadata.jvm)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    implementation("com.materialkolor:material-kolor:5.0.1")

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.4.0-alpha03")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        // Suppress warnings
        suppressWarnings.set(true)
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.compose.runtime",
            "androidx.compose.foundation",
            "androidx.compose.ui",
            "androidx.compose.animation" -> {
                useVersion(libs.versions.compose.get())
                because("Keep Compose aligned with the RectList alignment-placement fix")
            }
        }
    }
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlinMetadata.get()}",
    )
}
