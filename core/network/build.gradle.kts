import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    // core:network 不需要 Compose，只包含网络配置
}

android {
    namespace = "app.amisles.hanime.core.network"
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
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
}