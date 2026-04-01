import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rp.dedup"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject secrets into BuildConfig for Kotlin code access
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "FACEBOOK_APP_ID", "\"$facebookAppId\"")
        buildConfigField("String", "FACEBOOK_CLIENT_TOKEN", "\"$facebookClientToken\"")

        // Manifest placeholders for AndroidManifest.xml access
        manifestPlaceholders["facebook_app_id"] = facebookAppId
        manifestPlaceholders["facebook_client_token"] = facebookClientToken
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
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        resValues = false
        shaders = false
    }

    packaging {
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
    implementation("com.google.firebase:firebase-analytics")

    // Credential Manager & Google ID
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Facebook Login
    implementation("com.facebook.android:facebook-login:18.2.3")

    // Glance for App Widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Coil for Jetpack Compose
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ML Kit on-device image labeling
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Intro showcase / first-run tutorial
    implementation("com.canopas.intro-showcase-view:introshowcaseview:2.0.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
