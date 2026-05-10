import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

fun quotedBuildConfig(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

fun optionalSecret(name: String): String {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

fun optionalBoolean(name: String, defaultValue: Boolean): Boolean {
    return providers.gradleProperty(name).orNull?.toBooleanStrictOrNull()
        ?: providers.environmentVariable(name).orNull?.toBooleanStrictOrNull()
        ?: defaultValue
}

val releaseStoreFilePath = optionalSecret("RELEASE_STORE_FILE")
val releaseStorePassword = optionalSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = optionalSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = optionalSecret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

android {
    namespace = "com.materialchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.materialchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 111
        versionName = "2.20.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "ANTIGRAVITY_CLIENT_ID",
            quotedBuildConfig(optionalSecret("ANTIGRAVITY_CLIENT_ID"))
        )
        buildConfigField(
            "String",
            "ADMOB_BANNER_AD_UNIT_ID",
            quotedBuildConfig(
                optionalSecret("ADMOB_BANNER_AD_UNIT_ID")
                    .ifBlank { "ca-app-pub-3940256099942544/6300978111" }
            )
        )
        buildConfigField(
            "String",
            "ADMOB_REWARDED_AD_UNIT_ID",
            quotedBuildConfig(
                optionalSecret("ADMOB_REWARDED_AD_UNIT_ID")
                    .ifBlank { "ca-app-pub-3940256099942544/5224354917" }
            )
        )
        buildConfigField(
            "String",
            "REMOVE_ADS_PRODUCT_ID",
            quotedBuildConfig(optionalSecret("REMOVE_ADS_PRODUCT_ID").ifBlank { "remove_ads" })
        )
        manifestPlaceholders["adMobApplicationId"] = optionalSecret("ADMOB_APP_ID")
            .ifBlank { "ca-app-pub-3940256099942544~3347511713" }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField(
                "boolean",
                "ADS_ENABLED",
                optionalBoolean("ADS_ENABLED", true).toString()
            )
            buildConfigField("boolean", "EXTERNAL_UPDATES_ENABLED", "false")
        }

        create("github") {
            dimension = "distribution"
            buildConfigField(
                "boolean",
                "ADS_ENABLED",
                optionalBoolean("ADS_ENABLED", false).toString()
            )
            buildConfigField("boolean", "EXTERNAL_UPDATES_ENABLED", "true")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
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
            isMinifyEnabled = false
        }
    }

    lint {
        // Workaround: NonNullableMutableLiveDataDetector crashes due to
        // Kotlin Analysis API incompatibility with AGP 8.7.x + Kotlin 2.1.x.
        // This app uses Compose + Flow, not LiveData, so the check is irrelevant.
        disable += "NullSafeMutableLiveData"
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
    }
}

val googleTestAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
val googleTestRewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"
val validatePlayAdsEnabled = optionalBoolean("ADS_ENABLED", true)
val validateAdMobAppId = optionalSecret("ADMOB_APP_ID")
val validateBannerAdUnitId = optionalSecret("ADMOB_BANNER_AD_UNIT_ID")
    .ifBlank { googleTestBannerAdUnitId }
val validateRewardedAdUnitId = optionalSecret("ADMOB_REWARDED_AD_UNIT_ID")
    .ifBlank { googleTestRewardedAdUnitId }
val hasInjectedReleaseSigning = listOf(
    optionalSecret("android.injected.signing.store.file"),
    optionalSecret("android.injected.signing.store.password"),
    optionalSecret("android.injected.signing.key.alias"),
    optionalSecret("android.injected.signing.key.password")
).all { it.isNotBlank() }
val hasPlayReleaseSigning = hasReleaseSigning || hasInjectedReleaseSigning
val hasRealAdMobAppId = validateAdMobAppId.isNotBlank() &&
    validateAdMobAppId != googleTestAdMobAppId
val hasRealBannerAdUnitId = validateBannerAdUnitId != googleTestBannerAdUnitId
val hasRealRewardedAdUnitId = validateRewardedAdUnitId != googleTestRewardedAdUnitId

tasks.register("validatePlayReleaseConfig") {
    group = "verification"
    description = "Fails Play release builds that still use AdMob test configuration."

    val adsEnabled = validatePlayAdsEnabled
    val releaseSigningConfigured = hasPlayReleaseSigning
    val realAdMobAppIdConfigured = hasRealAdMobAppId
    val realBannerAdUnitIdConfigured = hasRealBannerAdUnitId
    val realRewardedAdUnitIdConfigured = hasRealRewardedAdUnitId

    doLast {
        if (!adsEnabled) return@doLast

        check(releaseSigningConfigured) {
            "Play release builds require release signing. Set RELEASE_STORE_FILE, " +
                "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD."
        }
        check(realAdMobAppIdConfigured) {
            "Play release builds with ads enabled require a real ADMOB_APP_ID."
        }
        check(realBannerAdUnitIdConfigured) {
            "Play release builds with ads enabled require a real ADMOB_BANNER_AD_UNIT_ID."
        }
        check(realRewardedAdUnitIdConfigured) {
            "Play release builds with ads enabled require a real ADMOB_REWARDED_AD_UNIT_ID."
        }
    }
}

tasks.matching { it.name == "prePlayReleaseBuild" }.configureEach {
    dependsOn("validatePlayReleaseConfig")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase
    implementation(libs.firebase.messaging)

    // Monetization
    implementation(libs.play.services.ads)
    implementation(libs.play.billing)

    // Tink Encryption
    implementation(libs.tink.android)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.sse)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Rich Text / Markdown
    implementation(libs.richtext.commonmark)
    implementation(libs.richtext.ui.material3)

    // Coil Image Loading
    implementation(libs.coil.compose)

    // On-device AI
    implementation(libs.litert.lm.android)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mlkit.genai.prompt)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
