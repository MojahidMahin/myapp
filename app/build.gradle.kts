plugins {
    id("com.google.gms.google-services")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
}

android {
    namespace = "com.localllm.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.localllm.myapplication"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    
    kapt {
        correctErrorTypes = true
        useBuildCache = true
        arguments {
            arg("kapt.include.compile.classpath", "false")
        }
    }
    
    lint {
        abortOnError = false
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
// or latest
    implementation("com.google.firebase:firebase-auth-ktx:22.1.1")
// or latest
    

    // MediaPipe for LLM - upgraded to latest version with Gemma 3N support
    implementation("com.google.mediapipe:tasks-genai:0.10.25")
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
    
    // Additional MediaPipe for comprehensive AI features
    implementation("com.google.mediapipe:tasks-text:0.10.15")
    
    // WorkManager for background processing
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // FCM for push notifications
    implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")
    
    // Image handling
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // TensorFlow Lite for fallback LLM inference
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    
    // Google ML Kit for OCR (text recognition)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    
    // Additional dependencies for new AI features
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // Gmail API integration
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.api-client:google-api-client-gson:2.2.0")
    
    // HTTP client for Telegram Bot API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON handling for API responses
    implementation("org.json:json:20230618")
    
    // Note: activity-result is included in activity-compose, no separate dependency needed
    
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // JavaMail for Gmail integration  
    implementation("javax.mail:mail:1.4.7")
    
    // OkHttp for Telegram API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON parsing
    implementation("org.json:json:20240303")

    // by mazenul
    implementation("com.squareup.okhttp3:okhttp:4.12.0")          // or your version
    implementation("com.squareup.retrofit2:retrofit:2.9.0")       // or your version
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // or your version


}