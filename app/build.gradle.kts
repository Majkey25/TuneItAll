plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStorePath = providers.environmentVariable("TUNEITALL_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("TUNEITALL_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TUNEITALL_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("TUNEITALL_KEY_PASSWORD").orNull
val previewStorePath = providers.environmentVariable("TUNEITALL_PREVIEW_KEYSTORE_PATH").orNull
val releaseSigningValues = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningEnabled = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningValues.none { !it.isNullOrBlank() } || releaseSigningEnabled) {
    "Set all TuneItAll release signing environment variables or none of them"
}

android {
    namespace = "com.tuneitall.tuner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tuneitall.tuner"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.0-alpha.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!previewStorePath.isNullOrBlank()) {
            getByName("debug") {
                storeFile = file(previewStorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningEnabled) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20260719")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
