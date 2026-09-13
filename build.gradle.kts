// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Compose 编译器插件（根工程只声明，不应用）
    alias(libs.plugins.compose.compiler) apply false
}
