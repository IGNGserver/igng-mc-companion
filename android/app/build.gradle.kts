import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingProperties = Properties()
val signingFile = rootProject.file("keystore/keystore.properties")
if (signingFile.exists()) signingFile.inputStream().use(signingProperties::load)

android {
    namespace = "net.igng.mcstatus"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.igng.mcstatus"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val baseUrl = providers.gradleProperty("MC_STATUS_BASE_URL")
            .orElse("https://mc.igng.net")
            .get()
            .trimEnd('/')
        buildConfigField("String", "MC_STATUS_BASE_URL", "\"$baseUrl\"")
        val ssoBaseUrl = providers.gradleProperty("IGNG_SSO_BASE_URL")
            .orElse("https://sso.igng.net")
            .get()
            .trimEnd('/')
        buildConfigField("String", "IGNG_SSO_BASE_URL", "\"$ssoBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingProperties.isNotEmpty()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = rootProject.file("keystore/${signingProperties.getProperty("storeFile")}")
                    storePassword = signingProperties.getProperty("storePassword")
                    keyAlias = signingProperties.getProperty("keyAlias")
                    keyPassword = signingProperties.getProperty("keyPassword")
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
