plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val kajutaApiBaseUrl: String =
    providers.gradleProperty("KAJUTABOT_API_BASE_URL")
        .getOrElse("https://api.kajuta.tryniecki.eu")
val kajutaDiscordClientId: String =
    providers.gradleProperty("KAJUTABOT_DISCORD_CLIENT_ID")
        .getOrElse("")
val discordScheme =
    if (kajutaDiscordClientId.isNotBlank()) "discord-$kajutaDiscordClientId" else "discord-unconfigured"

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

    buildTypes {
        release {
            optimization {
                enable = false
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
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
