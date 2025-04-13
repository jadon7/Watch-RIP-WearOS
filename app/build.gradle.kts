plugins {
    // 使用 version catalog 管理插件版本
    alias(libs.plugins.android.application)  // Android 应用程序插件
    alias(libs.plugins.kotlin.android)       // Kotlin Android 插件
}

android {
    namespace = "com.example.watchview"      // 应用包名
    compileSdk = 35                          // 编译用的 SDK 版本

    defaultConfig {
        applicationId = "com.example.watchview"  // 应用 ID
        minSdk = 30                             // 最低支持的 Android 版本 (WearOS)
        targetSdk = 34                          // 目标 Android 版本
        versionCode = 2                         // 应用版本号
        versionName = "2.0.1"                     // 应用版本名称
        vectorDrawables {
            useSupportLibrary = true            // 启用矢量图支持
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false             // 是否开启代码混淆
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // 设置 Java 编译版本
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"                      // Kotlin 编译目标版本
    }
    buildFeatures {
        compose = true                         // 启用 Jetpack Compose
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"  // Compose 编译器版本
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"  // 排除一些不需要的许可文件
        }
    }
}

dependencies {
    // WearOS 相关依赖
    implementation(libs.play.services.wearable)  // Google Play 服务 WearOS 支持库
    implementation("androidx.wear:wear:1.3.0")   // WearOS 基础支持库
    
    // Compose 相关依赖
    implementation(platform(libs.compose.bom))   // Compose BOM (物料清单)
    implementation(libs.ui)                      // Compose UI 核心库
    implementation(libs.ui.tooling.preview)      // Compose 预览支持
    implementation("androidx.compose.material:material:1.2.1")  // Material Design 支持
    implementation(libs.compose.material)        // WearOS Material Design 支持
    implementation(libs.compose.foundation)      // Compose 基础组件
    
    // WearOS 开发工具
    implementation(libs.wear.tooling.preview)    // WearOS 预览工具
    implementation(libs.activity.compose)        // Compose Activity 支持
    implementation(libs.core.splashscreen)       // 启动画面支持
    
    // 测试相关依赖
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    
    // ExoPlayer 依赖
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    
    // 第三方库依赖
    implementation("com.google.accompanist:accompanist-pager:0.28.0")  // 分页组件
    implementation("app.rive:rive-android:9.13.10")                    // Rive 动画支持
    implementation("androidx.startup:startup-runtime:1.1.1")           // App Startup
}