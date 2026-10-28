import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Apply the Compose Compiler plugin
    alias(libs.plugins.kotlin.compose)

    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")

}

// Read config from local.properties and environment variables (keeps secrets out of version control)
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) load(localPropsFile.inputStream())
}

fun getSecureProperty(key: String, vararg envVars: String, default: String = ""): String {
    for (envVar in envVars) {
        val envVal = System.getenv(envVar)
        if (!envVal.isNullOrBlank()) return envVal
    }
    val propVal = localProperties.getProperty(key)
    if (!propVal.isNullOrBlank()) return propVal

    val projectProp = project.findProperty(key)?.toString()
    if (!projectProp.isNullOrBlank()) return projectProp

    return default
}

val acraUrl: String = getSecureProperty("acra.url", "ACRA_URL")
val acraToken: String = getSecureProperty("acra.token", "ACRA_TOKEN")
val tmdbApiKey: String = getSecureProperty("tmdb.api_key", "TMDB_API_KEY")
val traktClientId: String = getSecureProperty("TRAKT_CLIENT_ID", "TRAKT_CLIENT_ID")
val traktClientSecret: String = getSecureProperty("TRAKT_CLIENT_SECRET", "TRAKT_CLIENT_SECRET")

android {
    namespace = "com.lumera.app"
    compileSdk = 36


    defaultConfig {
        applicationId = "com.lumerax.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.2.0"

        // GitHub repository for auto-update system
        buildConfigField("String", "GITHUB_OWNER", "\"bruno33223\"")
        buildConfigField("String", "GITHUB_REPO", "\"LumeraX\"")

        // Local development config: set to true to enable the "Reinstall Current Version" button
        val debugUserMode = project.findProperty("lumera.debug_user_mode")?.toString() ?: "false"
        buildConfigField("boolean", "DEBUG_USER_MODE", debugUserMode)

        // ACRA crash reporting (loaded from local.properties)
        buildConfigField("String", "ACRA_URL", "\"$acraUrl\"")
        buildConfigField("String", "ACRA_TOKEN", "\"$acraToken\"")

        // TMDB API (loaded from local.properties)
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")

        // Trakt API (loaded from local.properties)
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$traktClientSecret\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        val keystorePath = getSecureProperty("release.keystore.path", "KEYSTORE_PATH", "RELEASE_KEYSTORE_PATH", default = "lumera-release.jks")
        val keystoreFile = if (File(keystorePath).isAbsolute) {
            File(keystorePath)
        } else {
            val rootFile = rootProject.file(keystorePath)
            if (rootFile.exists()) rootFile else project.file(keystorePath)
        }
        val storePassword = getSecureProperty("release.store.password", "STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
        val keyAlias = getSecureProperty("release.key.alias", "KEY_ALIAS", "RELEASE_KEY_ALIAS")
        val keyPassword = getSecureProperty("release.key.password", "KEY_PASSWORD", "RELEASE_KEY_PASSWORD")

        val isReleaseSigningConfigured = keystoreFile.exists() &&
            storePassword.isNotBlank() &&
            keyAlias.isNotBlank() &&
            keyPassword.isNotBlank()

        if (isReleaseSigningConfigured) {
            create("release") {
                storeFile = keystoreFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {

            applicationIdSuffix = ".test"
            resValue("string", "app_name", "LumeraX Test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig != null) {
                signingConfig = releaseConfig
            } else {
                logger.warn("AVISO: Chaves de assinatura de release não configuradas no signingConfigs.")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

gradle.taskGraph.whenReady {
    val isCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    if (isCi) {
        val requiresReleaseSigning = allTasks.any { task ->
            task.name.contains("Release", ignoreCase = true) &&
                (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
        }
        if (requiresReleaseSigning && android.signingConfigs.findByName("release") == null) {
            throw GradleException("FATAL: Tentativa de compilar build de Release em ambiente CI sem chaves de assinatura válidas configuradas. Abortando.")
        }
    }
}

// Compose Compiler configuration for optimal performance
composeCompiler {
    featureFlags.set(setOf(
        org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.StrongSkipping,
        org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.IntrinsicRemember
    ))

    // Stability configuration: tells the compiler which classes are effectively immutable
    // so it can skip recomposition when their instances haven't changed
    stabilityConfigurationFile = project.layout.projectDirectory.file("compose_stability_config.conf")
}

dependencies {
    // 0. ASS/SSA subtitle renderer
    implementation(project(":assrender"))

    // 1. Android TV UI (Compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)


    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // 2. Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 4. Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // 5. Database
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    implementation(libs.androidx.compose.animation.core)
    kapt("androidx.room:room-compiler:2.7.0")

    // 6. Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // 7. Video Player
    implementation(project(":playbackcore"))
    implementation(files("../playbackcore/libs/lib-exoplayer-release.aar"))
    implementation(files("../playbackcore/libs/lib-decoder-av1-release.aar"))
    implementation(files("../playbackcore/libs/lib-decoder-ffmpeg-release.aar"))
    implementation(files("../playbackcore/libs/lib-decoder-iamf-release.aar"))
    implementation(files("../playbackcore/libs/lib-decoder-mpegh-release.aar"))

    // 8. Testing & Debugging
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")

    // OkHttp is already available via Retrofit, but declare explicitly for TorrServer API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- LOCAL WEB SERVER (used by remote input hub) ---
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // --- QR CODE GENERATION ---
    implementation("com.google.zxing:core:3.5.2")

    // --- ENCRYPTED SHARED PREFERENCES ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- CRASH REPORTING (ACRA) ---
    implementation("ch.acra:acra-http:5.11.4")
    implementation("ch.acra:acra-toast:5.11.4")

    // 9. Backup & Cloud Sync

    // Testing
    testImplementation("junit:junit:4.13.2")
}
