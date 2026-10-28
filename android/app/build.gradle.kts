import java.util.Properties

plugins {
    id("com.android.application")
}

// Optional release signing: android/keystore.properties (gitignored) with
// storeFile, storePassword, keyAlias, keyPassword. Without it, release builds
// fall back to the debug key so they can still be sideloaded.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.pogopaste.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.pogopaste.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/**", "kotlin/**", "**.kotlin_builtins", "DebugProbesKt.bin")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// The website's data files are the single source of truth for symbols and
// search strings; they ship as assets so the site and keyboard never drift.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(rootProject.file("../src/_data").absolutePath)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20180813")
}
