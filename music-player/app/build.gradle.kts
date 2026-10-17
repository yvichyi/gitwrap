import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from keystore.properties next to settings.gradle.kts (never committed;
// storeFile, storePassword, keyAlias, keyPassword) or the LANYIN_KEYSTORE, LANYIN_KEYSTORE_PASSWORD,
// LANYIN_KEY_ALIAS and LANYIN_KEY_PASSWORD environment variables. With neither, release builds come
// out unsigned and the preview build is signed with the debug key as before.
val releaseKeys: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    val keys = if (file.isFile) {
        Properties().apply { file.inputStream().use { load(it) } }.let { p -> p.stringPropertyNames().associateWith { p.getProperty(it).trim() } }
    } else {
        mapOf(
            "storeFile" to "LANYIN_KEYSTORE", "storePassword" to "LANYIN_KEYSTORE_PASSWORD",
            "keyAlias" to "LANYIN_KEY_ALIAS", "keyPassword" to "LANYIN_KEY_PASSWORD",
        ).mapNotNull { (key, env) -> providers.environmentVariable(env).orNull?.let { key to it } }.toMap()
    }
    keys.takeIf { k -> listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !k[it].isNullOrEmpty() } }
}

android {
    namespace = "io.lanyin.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.lanyin.player"
        minSdk = 29
        targetSdk = 36
        versionCode = 15
        versionName = "0.15.0-m14"
    }

    signingConfigs {
        if (releaseKeys != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeys.getValue("storeFile"))
                storePassword = releaseKeys.getValue("storePassword")
                keyAlias = releaseKeys.getValue("keyAlias")
                keyPassword = releaseKeys.getValue("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Shrunk like release but debug-signed, for small side-loadable test builds.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs real resources to render Compose screenshots on the JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric fetches its android-all runtime itself; let rate-limited environments point it at a mirror.
    providers.environmentVariable("ROBOLECTRIC_REPO_URL").orNull?.let {
        systemProperty("robolectric.dependency.repo.url", it)
    }
    // Test runs aren't worth keeping in the build cache: hundreds of screenshots a run, and a
    // rerun is what's wanted anyway.
    outputs.cacheIf { false }
    // Backdrops follow the local time; screenshots are taken at a fixed one, in the evening.
    systemProperty("lanyin.fixedHour", "18.5")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.lyricon.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
