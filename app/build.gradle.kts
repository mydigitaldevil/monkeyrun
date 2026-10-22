import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firebase (Analytics + Crashlytics) is enabled only when google-services.json is present,
// so the project builds out of the box without any secrets.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// Release signing comes from keystore.properties (never committed) or CI env vars.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

// Ad unit / app IDs. Defaults are Google's official TEST ids — replace via gradle.properties
// or CI env vars before shipping (e.g. -PADMOB_APP_ID=ca-app-pub-xxx~yyy).
fun cfg(name: String, default: String): String =
    (project.findProperty(name) as String?) ?: System.getenv(name) ?: default

android {
    namespace = "com.junglerun.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.junglerun.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        manifestPlaceholders["admobAppId"] = cfg("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${cfg("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/6300978111")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${cfg("ADMOB_INTERSTITIAL_ID", "ca-app-pub-3940256099942544/1033173712")}\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${cfg("ADMOB_REWARDED_ID", "ca-app-pub-3940256099942544/5224354917")}\"")
        buildConfigField("String", "ADMOB_REWARDED_INTERSTITIAL_ID", "\"${cfg("ADMOB_REWARDED_INTERSTITIAL_ID", "ca-app-pub-3940256099942544/5354046379")}\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"${cfg("ADMOB_APP_OPEN_ID", "ca-app-pub-3940256099942544/9257395921")}\"")
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"${cfg("ADMOB_NATIVE_ID", "ca-app-pub-3940256099942544/2247696110")}\"")
        // Meta Audience Network placements: "IMG_16_9_APP_INSTALL#<placement>" is Meta's test prefix.
        buildConfigField("String", "META_BANNER_ID", "\"${cfg("META_BANNER_ID", "IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID")}\"")
        buildConfigField("String", "META_INTERSTITIAL_ID", "\"${cfg("META_INTERSTITIAL_ID", "IMG_16_9_APP_INSTALL#YOUR_PLACEMENT_ID")}\"")
        buildConfigField("String", "META_REWARDED_ID", "\"${cfg("META_REWARDED_ID", "VID_HD_16_9_15S_APP_INSTALL#YOUR_PLACEMENT_ID")}\"")
        buildConfigField("String", "ONLINE_SERVER_URL", "\"${cfg("ONLINE_SERVER_URL", "ws://10.0.2.2:8080")}\"")
        buildConfigField("boolean", "FIREBASE_ENABLED", hasFirebaseConfig.toString())
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "JR_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "JR_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "JR_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "JR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val release = signingConfigs.getByName("release")
            if (release.storeFile != null) signingConfig = release
        }
        debug {
            // No applicationIdSuffix: google-services.json must match the package name exactly.
            versionNameSuffix = "-debug"
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    // Ads: Google AdMob (primary + mediation) and Meta Audience Network (direct + via AdMob mediation)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.meta.audience.network)
    implementation(libs.meta.mediation.adapter)

    // In-app purchases
    implementation(libs.billing.ktx)

    // Online multiplayer transport
    implementation(libs.okhttp)

    // Analytics / crash reporting (inert until google-services.json is added)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (android.jar only has stubs)
    testImplementation(libs.org.json)
}
