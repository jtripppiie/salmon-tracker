plugins {
    id("com.android.application")
}

import java.util.Properties

android {
    namespace = "com.tripperdee.salmontracker"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.tripperdee.salmontracker"
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    val localSigning = Properties()
    val localSigningFile = rootProject.file("signing.local.properties")
    if (localSigningFile.exists()) {
        localSigningFile.inputStream().use(localSigning::load)
    }
    val releaseStoreFile = providers.environmentVariable("SALMON_UPLOAD_STORE_FILE").orNull
        ?: localSigning.getProperty("storeFile")
    val releaseStorePassword = providers.environmentVariable("SALMON_UPLOAD_STORE_PASSWORD").orNull
        ?: localSigning.getProperty("storePassword")
    val releaseKeyAlias = providers.environmentVariable("SALMON_UPLOAD_KEY_ALIAS").orNull
        ?: localSigning.getProperty("keyAlias")
    val releaseKeyPassword = providers.environmentVariable("SALMON_UPLOAD_KEY_PASSWORD").orNull
        ?: localSigning.getProperty("keyPassword")

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null &&
            releaseKeyAlias != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
