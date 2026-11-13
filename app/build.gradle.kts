import com.android.build.api.variant.HasUnitTestBuilder
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing: read from keystore.properties (never committed). See claude/RELEASE.md.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.mathieu.youcandoit"
    compileSdk = 36

    defaultConfig {
        // Permanent once uploaded to Google Play. The display name (app_name) can change freely.
        applicationId = "mathieu.dev.youcandoit"
        minSdk = 26
        // Google Play requires API 36 for new apps and updates since 2026-08-31.
        targetSdk = 36
        // Bump versionCode by 1 for every upload to Play; versionName is what testers see.
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            if (!keystoreProperties.isEmpty) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!keystoreProperties.isEmpty) signingConfig = signingConfigs.getByName("release")
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
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

androidComponents {
    // Unit tests (incl. Compose UI tests on Robolectric) run against the debug build only:
    // they need debug-only test helpers, and running them twice adds nothing.
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as HasUnitTestBuilder).enableUnitTest = false
    }
}

room {
    // Commit this folder: it's what makes future migrations testable.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended) // R8 strips the unused icons in release
    implementation(libs.reorderable)
    implementation(libs.play.app.update.ktx)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Android unit tests run on JUnit 4 (Robolectric's runner); the domain module stays on JUnit 5.
    testImplementation(testFixtures(project(":domain"))) // in-memory fakes + fixtures
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest) // hosts composables under test
}
