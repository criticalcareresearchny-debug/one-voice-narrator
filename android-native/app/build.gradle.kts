plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.onevoice.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.onevoice.nativeapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-beta"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }
    ndkVersion = "26.3.11579264"
    androidResources { noCompress += listOf("onnx", "bin") }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
