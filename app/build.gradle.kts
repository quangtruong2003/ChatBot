plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ahmedapps.geminichatbot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ahmedapps.geminichatbot"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2025.04.20"
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField ("String", "API_KEY", "\"AIzaSyAA7Vmx2Gpf7lXl_VhbAE5sKJN0U6Sv1bg\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isDebuggable = true
            // Ngăn Gradle tự thêm android:testOnly="true"
            isTestCoverageEnabled = false
        }
    }
    
//    // Tạo các APK riêng cho từng kiến trúc CPU
//    splits {
//        abi {
//            isEnable = true
//            reset()
//            include("arm64-v8a", "x86_64")
//            isUniversalApk = false
//        }
//    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation("androidx.compose.ui:ui-text")
    // Core và Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose BOM để quản lý phiên bản Compose
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.animation:animation")


    // Firebase BOM để quản lý phiên bản Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

//    // Firebase App Check với Play Integrity
//    implementation ("com.google.firebase:firebase-appcheck-playintegrity:18.0.0")
//    // Firebase App Check Debug Provider
//    implementation ("com.google.firebase:firebase-appcheck-debug:18.0.0")

    // Firebase Auth và Firestore
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.1")

    // Room dependencies
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt dependencies
    implementation("com.google.dagger:hilt-android:2.49")
    kapt("com.google.dagger:hilt-android-compiler:2.49")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coil Image Loader
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Generative AI (nếu cần)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Play Services Auth
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Các dependencies khác
    implementation("androidx.ink:ink-brush-android:1.0.0-alpha02")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.accompanist:accompanist-insets:0.23.1")
    implementation("com.google.accompanist:accompanist-insets-ui:0.23.1")
    implementation("androidx.compose.foundation:foundation:1.8.0")

    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.2")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0-alpha13")

    // Dùng iText để xử lý PDF
    implementation("com.itextpdf:itextg:5.5.10")
    
    // Thư viện đọc file TXT sẵn có trong Android
    
    // Dùng Document4J cho DOC/DOCX nếu cần
    implementation("org.docx4j:docx4j:6.1.2") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // Thêm thư viện desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val appcompat_version = "1.7.0"
    implementation("androidx.appcompat:appcompat:$appcompat_version")
    // For loading and tinting drawables on older versions of the platform
    implementation("androidx.appcompat:appcompat-resources:$appcompat_version")

    implementation ("net.java.dev.jna:jna:5.13.0@aar")
    // implementation ("com.alphacephei:vosk-android:0.3.47") // Thư viện nhận dạng giọng nói

    // Audio processing
    implementation("org.jcodec:jcodec:0.2.5")
    implementation("org.jcodec:jcodec-android:0.2.5")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.arthenica:ffmpeg-kit-full:6.0-2.LTS") // FFmpeg đầy đủ, rất nặng

}

