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

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
