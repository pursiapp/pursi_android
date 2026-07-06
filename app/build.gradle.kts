plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

val signingEnv = System.getenv()
val signingProps = try {
    val p = Properties()
    val pf = rootProject.file("local.properties")
    if (pf.exists()) FileInputStream(pf).use { p.load(it) }
    p
} catch (_: Exception) {
    Properties()
}

val ksPath = signingEnv["SIGNING_KEYSTORE_PATH"]
    ?: signingProps.getProperty("signing.keystore.path", "")

android {
    namespace = "app.pursi"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pursi"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "0.5.2"

        val buildTimestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss z", Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val props = Properties()
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) {
            FileInputStream(propsFile).use { props.load(it) }
        }
        val umamiUrl = props.getProperty("umami.url", "https://CHANGE_ME")
        val umamiId = props.getProperty("umami.website.id", "CHANGE_ME")
        if (umamiUrl == "https://CHANGE_ME") {
            logger.warn(":app warning — umami.url not set in local.properties, analytics disabled")
        }
        buildConfigField("String", "UMAMI_URL", "\"$umamiUrl\"")
        buildConfigField("String", "UMAMI_WEBSITE_ID", "\"$umamiId\"")
    }

    signingConfigs {
        create("release").apply {
            if (ksPath.isNotEmpty()) {
                storeFile = file(ksPath)
                storePassword = signingEnv["SIGNING_KEYSTORE_PASSWORD"]
                    ?: signingProps.getProperty("signing.keystore.password", "")
                keyAlias = signingEnv["SIGNING_KEY_ALIAS"]
                    ?: signingProps.getProperty("signing.key.alias", "")
                keyPassword = signingEnv["SIGNING_KEY_PASSWORD"]
                    ?: signingProps.getProperty("signing.key.password", "")
                logger.info(":app — release signing configured via local.properties / env")
            } else {
                logger.warn(":app — signing.keystore.path not set, release build will not be signed")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

    lintOptions {
        disable("NullSafeMutableLiveData")
    }
}



dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // MapLibre Native Android
    implementation("org.maplibre.gl:android-sdk:13.2.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")


    // PMTiles reader library
    implementation("ch.poole.geo.pmtiles-reader:Reader:0.3.6")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.dagger:hilt-android-testing:2.54")
    kspTest("com.google.dagger:hilt-compiler:2.54")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
