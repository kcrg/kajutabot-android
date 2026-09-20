plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val kajutaApiBaseUrl: String =
    providers.gradleProperty("KAJUTABOT_API_BASE_URL")
        .getOrElse("https://api.kajuta.tryniecki.eu")
val kajutaDiscordClientId: String =
    providers.gradleProperty("KAJUTABOT_DISCORD_CLIENT_ID")
        .getOrElse("")
val discordScheme =
    if (kajutaDiscordClientId.isNotBlank()) "discord-$kajutaDiscordClientId" else "discord-unconfigured"

// Release credentials come only from the build environment, never from project files.
val releaseSigningKeys = listOf(
    "KAJUTABOT_RELEASE_STORE_FILE",
    "KAJUTABOT_RELEASE_STORE_PASSWORD",
    "KAJUTABOT_RELEASE_KEY_ALIAS",
    "KAJUTABOT_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningKeys.associateWith { key ->
    providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
}
val suppliedSigningKeys = releaseSigningValues.filterValues { it != null }.keys
require(suppliedSigningKeys.isEmpty() || suppliedSigningKeys.size == releaseSigningKeys.size) {
    "Incomplete release signing environment: set all four KAJUTABOT_RELEASE_* variables or none."
}

android {
    namespace = "com.tryniecki.kajutabot"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tryniecki.kajutabot"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAJUTABOT_API_BASE_URL", "\"$kajutaApiBaseUrl\"")
        buildConfigField("String", "KAJUTABOT_DISCORD_CLIENT_ID", "\"$kajutaDiscordClientId\"")

        manifestPlaceholders["discordScheme"] = discordScheme
    }

    val releaseSigning = if (suppliedSigningKeys.isNotEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(releaseSigningValues.getValue("KAJUTABOT_RELEASE_STORE_FILE")!!)
            storePassword = releaseSigningValues.getValue("KAJUTABOT_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("KAJUTABOT_RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("KAJUTABOT_RELEASE_KEY_PASSWORD")
        }
    } else {
        logger.lifecycle("Release signing is not configured; assembleRelease will produce an unsigned APK.")
        null
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles("proguard-rules.pro")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":api"))
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.tabler.icons.outline.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
