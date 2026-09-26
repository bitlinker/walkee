// Pure Kotlin/JVM module: projection math, tile arithmetic and the fog storage. No Android
// dependencies, so its tests run on the JVM in seconds (ADR 0005).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.test {
    useJUnitPlatform()
}
