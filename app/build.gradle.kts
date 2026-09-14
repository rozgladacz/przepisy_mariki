import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}

android {
    namespace = "pl.local.przepisy"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.local.przepisy"
        minSdk = 33
        targetSdk = 36
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt()
            ?: appVersion.getProperty("versionCode").toInt()
        versionName = providers.gradleProperty("releaseVersionName").orNull
            ?: appVersion.getProperty("versionName")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"rozgladacz/przepisy_mariki\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(requireNotNull(keystoreProperties.getProperty("storeFile")))
                storePassword = requireNotNull(keystoreProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(keystoreProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(keystoreProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.jsoup)
    testImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("verifyReleaseApkSize") {
    dependsOn("assembleRelease")
    val releaseApkDirectory = layout.buildDirectory.dir("outputs/apk/release")
    inputs.dir(releaseApkDirectory)
    doLast {
        val apk = inputs.files.files.firstOrNull { it.extension == "apk" }
            ?: error("Nie znaleziono release APK")
        val maximumBytes = 25L * 1024L * 1024L
        check(apk.length() <= maximumBytes) {
            "Release APK ma ${apk.length()} bajtów i przekracza limit $maximumBytes bajtów"
        }
    }
}
