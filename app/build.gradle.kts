plugins {
    alias(libs.plugins.android.application)
    // Compose 编译器插件（AGP 9 已内置 Kotlin，切勿再加 org.jetbrains.kotlin.android）
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.Skyhelp.tasker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.Skyhelp.tasker"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 只打包 arm64-v8a：
            // - 《光·遇》本体不支持 32 位设备
            // - libvkbd.so 只有 arm64-v8a 一份，其它 ABI 本来就跑不起来
            // - 模拟器也用不上（本 App 依赖真实 HID 设备）
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // 已移除 externalNativeBuild：原 src/main/cpp 是 Studio 模板残留，
    // 编出的 libtasker.so 无任何代码引用（已随 cpp 目录一并删除）。
    // 项目现在唯一的原生产物是预编译的 libvkbd.so，直接放在 jniLibs/ 下。
    buildFeatures {
        // 主界面已改用 Jetpack Compose，不再需要 viewBinding
        compose = true
    }
    packaging {
        jniLibs {
            // libvkbd.so 必须真实解压到 nativeLibraryDir —— Shizuku 要以 shell/root
            // 身份直接 exec 它，留在 APK 里 mmap 是跑不了的
            useLegacyPackaging = true
            // vkbd 是可执行文件不是共享库，跳过 strip，防止构建报错或产物被改坏
            keepDebugSymbols.add("**/libvkbd.so")
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Shizuku：免 root 的 shell 能力桥（测试机走 SUI，最终用户走真 Shizuku + 无线调试）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // ---- Jetpack Compose（版本由 BOM 统一管理）----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // 图标库（material3 不传递依赖它，必须显式声明，否则 Icons.* 全报 Unresolved reference）
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}