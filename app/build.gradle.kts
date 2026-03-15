plugins {
    id("com.android.application")
}

android {
    namespace = "com.rulerhao.media_protector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rulerhao.media_protector"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release-keystore.jks")
            storePassword = "z24236002"
            keyAlias = "rulerhao"
            keyPassword = "z24236002"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":media-crypto"))
    // No external dependencies
}
