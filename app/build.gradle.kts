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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
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