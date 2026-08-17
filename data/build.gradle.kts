import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // data 模块不需要 Compose UI
}

android {
    namespace = "app.amisles.hanime.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":domain:model"))

    implementation(libs.androidx.core.ktx)

    // EncryptedSharedPreferences（登录态 Cookie 加密存储）
    implementation(libs.androidx.security.crypto)
    
    // Network
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Test
    testImplementation(libs.junit)
}