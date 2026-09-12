plugins {
    alias(libs.plugins.android.application)
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
        viewBinding = true
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}