plugins {
    id("com.android.library")
}

android {
    namespace = "com.lumera.playbackcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "MissingTranslation"
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    api(libs.androidx.media3.common)
    api(libs.androidx.media3.container)
    api(libs.androidx.media3.datasource)
    api(libs.androidx.media3.datasource.okhttp)
    api(libs.androidx.media3.decoder)
    api(libs.androidx.media3.exoplayer.dash) {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api(libs.androidx.media3.exoplayer.hls) {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api(libs.androidx.media3.exoplayer.rtsp) {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api(libs.androidx.media3.exoplayer.smoothstreaming) {
        exclude(group = "androidx.media3", module = "media3-exoplayer")
    }
    api(libs.androidx.media3.extractor)
    api(libs.androidx.media3.session)
    api(libs.androidx.media3.ui)
}
