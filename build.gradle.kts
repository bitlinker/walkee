// Root build script: only pins plugin versions. Kotlin for Android modules is built into AGP 9;
// `kotlin.android` is declared here (apply false) solely to pin the Kotlin Gradle plugin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
