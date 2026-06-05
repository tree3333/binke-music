plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 批 1：删除 org.jetbrains.kotlin.plugin.compose 插件（如有）
}

android {
    namespace = "com.binke.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.binke.music"
        minSdk = 19  // 批 1：降到 19 (Android 4.4 KitKat)
        targetSdk = 34
        versionCode = 22
        versionName = "1.0.22-android4"

        vectorDrawables {
            useSupportLibrary = true
        }

        // 只打真机 ABI (arm64 + armv7)，去掉 x86/x86_64 模拟器用，APK -9.5MB
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("/root/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    
    // 批 1：删除 buildFeatures.compose 和 composeOptions
    buildFeatures {
        viewBinding = true
    }
    
    // 批 1: ExoPlayer 2.x + TFLite 2.8 + Material 1.11 让 dex 突破 64K
    defaultConfig {
        multiDexEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")  // 批 1：替换 activity-compose
    implementation("com.google.android.material:material:1.11.0")  // 批 1：替换 material3
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")  // 批 1：替换 constraintlayout-compose
    
    // Lifecycle & ViewModel (批 1: lifecycle-viewmodel-ktx 替换 lifecycle-viewmodel-compose)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    
    // Fragment (批 1: navigation-fragment-ktx 替换 navigation-compose)
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")
    
    // ExoPlayer 2.x (批 1: 替换 media3，最低 16 兼容)
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-datasource:2.19.1")
    // 注: 2.19.1 已合并 datasource-okhttp 和 session 到主包，不再单独存在
    // ExoPlayer 2.x 旧 media session 用 androidx.media:media:1.7.0 (下面)
    
    // OkHttp for API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // AndroidX Media (用于 MediaSessionCompat — ExoPlayer 2.x 的锁屏/通知栏媒体控件)
    implementation("androidx.media:media:1.7.0")
    
    // Coil (批 1: 降到 1.4.0，最后支持 API 14 的版本)
    implementation("io.coil-kt:coil:1.4.0")
    
    // MultiDex support for API < 21
    implementation("androidx.multidex:multidex:1.0.3")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // 注: Android 平台自带 org.json，不需要单独引

    // TensorFlow Lite (批 1: 降到 2.8.0 支持 API 19)
    implementation("org.tensorflow:tensorflow-lite:2.8.0")
    
    // 批 1：删除 debugImplementation ui-tooling
}
