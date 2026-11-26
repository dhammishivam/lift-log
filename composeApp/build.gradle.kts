import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// Crashlytics turns on once composeApp/google-services.json exists. CI decrypts it from google-services.json.enc
// (Firebase project lift-log-f7d17); for local builds, put the plain file there (git-ignored). It lists both
// com.krishiv.liftlog and com.krishiv.liftlog.dev, so dev crashes land in their own Firebase app, not the Play one.
val firebase = file("google-services.json").exists()
if (firebase) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Store screenshots: real Compose screens rendered on the JVM by Robolectric.
        val androidUnitTest by getting {
            dependencies {
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.compose.ui:ui-test-junit4:1.8.2")
                implementation("androidx.test:core:1.6.1")
                implementation("junit:junit:4.13.2")
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.exifinterface)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqldelight.android)
            implementation(libs.ktor.client.okhttp)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
            // Firebase pulls in an old Fragment through Play services; activity-result APIs need 1.3+.
            implementation("androidx.fragment:fragment:1.8.5")
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "app.liftlog"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.krishiv.liftlog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI passes its run number so every uploaded build has a higher version code.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
    }
    buildFeatures { buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    // Release signing comes from CI secrets; without them the release build is unsigned (fine for checks).
    val ks = System.getenv("UPLOAD_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ks != null) create("upload") {
            storeFile = ks
            storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "COACH", "true")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The AI coach stays out of Play builds until it has an in-app report button (Play's AI-generated content policy).
            buildConfigField("boolean", "COACH", "false")
            if (ks != null) signingConfig = signingConfigs.getByName("upload")
        }
        // The build we test with: same optimised code as release, but its own package (com.krishiv.liftlog.dev),
        // name and orange icon, so it installs next to the Play version and its crashes stay out of the Play dashboard.
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            matchingFallbacks += "release"
            buildConfigField("boolean", "COACH", "true")
            manifestPlaceholders["appLabel"] = "Lift Log Dev"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_dev"
            signingConfig = if (ks != null) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("LiftDb") {
            packageName.set("app.liftlog.db")
        }
    }
}

// Print a one-line test summary in CI logs and show failures in full.
tasks.withType<Test>().configureEach {
    systemProperty("liftlog.screenshots", (project.findProperty("screenshots") ?: "false").toString())
    testLogging {
        events("started", "passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
        if (desc.parent == null) println("Unit tests: ${result.testCount} run, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
    }))
}

dependencies {
    // Registers the empty test activity that Compose UI tests render into.
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.2")
}
