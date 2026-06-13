import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kotlinx.kover)
}

// Load local.properties safely
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val googleWebClientId = localProperties.getProperty("google_web_client_id") ?: ""
val facebookAppId = localProperties.getProperty("facebook_app_id") ?: ""
val facebookClientToken = localProperties.getProperty("facebook_client_token") ?: ""

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.rp.dedup"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rp.dedup"
        minSdk = 24
        targetSdk = 37
        versionCode = 15
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Manifest placeholder for Facebook SDK (ApplicationId must be in manifest)
        manifestPlaceholders["facebook_app_id"] = facebookAppId

        externalNativeBuild {
            cmake {
                cppFlags += ""
                // Inject secrets into the native .so — keeps them out of DEX/BuildConfig
                arguments += listOf(
                    "-DGOOGLE_WEB_CLIENT_ID=$googleWebClientId",
                    "-DFACEBOOK_APP_ID=$facebookAppId",
                    "-DFACEBOOK_CLIENT_TOKEN=$facebookClientToken"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            versionNameSuffix = "-dev"
            buildConfigField("Boolean", "IS_PROD", "false")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("Boolean", "IS_PROD", "true")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        register("baselineProfile") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        resValues = false
        shaders = false
    }

    packaging {
        jniLibs {
            // Support 16 KB page sizes by ensuring native libraries are uncompressed and aligned
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.database)
    implementation(libs.firebase.analytics)

    // Credential Manager & Google ID
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Facebook Login
    implementation(libs.facebook.login)

    // Glance for App Widgets
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // EXIF orientation correction
    implementation(libs.androidx.exifinterface)

    // Coil for Jetpack Compose
    implementation(libs.coil.compose)

    // ML Kit on-device image labeling (used by SmartJunkRepository)
    implementation(libs.image.labeling)

    // MediaPipe Text Embedder — on-device semantic search
    implementation(libs.mediapipe.tasks.text)
    implementation(libs.face.detection)

    // Intro showcase / first-run tutorial
    implementation(libs.introshowcaseview)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(enforcedPlatform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.sqlcipher)
    implementation(libs.play.integrity)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.core.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(enforcedPlatform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
