import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            // Ktor engine for Android
            implementation(libs.ktor.client.okhttp)
            // Background scheduling for favorite-on-plan reminders
            implementation(libs.androidx.work.runtime.ktx)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Supabase
            implementation(project.dependencies.platform("io.github.jan-tennert.supabase:bom:3.2.0"))
            implementation(libs.supabase.postgrest.kt)
            // Ktor
            implementation(libs.ktor.client.core)
            // Date/Time
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
            // Compose Navigation (KMP)
            implementation(libs.androidx.navigation.compose)
            // Image loading (Kamel)
            implementation(libs.kamel.image)
            // Multiplatform Settings for persistence
            implementation("com.russhwolf:multiplatform-settings:1.1.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // JS / WASM
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// CI (see .github/workflows/release-play-store.yml) overrides these; local builds use the defaults.
val appVersionCode = (findProperty("mensa.versionCode") as String?)?.toInt() ?: 15
val appVersionName = (findProperty("mensa.versionName") as String?) ?: "2.7"

android {
    namespace = "com.lkaesberg.mensaapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lkaesberg.mensaapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // Only wired up when the keystore is present (CI, or a local release build).
        // Debug builds and CI jobs that don't need signing are unaffected.
        val keystoreFile = System.getenv("MENSA_KEYSTORE_FILE")
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("MENSA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MENSA_KEY_ALIAS")
                keyPassword = System.getenv("MENSA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // Workaround for lint crash with NonNullableMutableLiveDataDetector when using Kotlin ≥ 2.0
        disable.add("NullSafeMutableLiveData")
        checkDependencies = false
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

