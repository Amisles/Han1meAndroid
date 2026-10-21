import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // data 模块不需要 Compose UI
}

android {
    namespace = "app.amisles.hanime.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:common"))
    api(project(":domain:model"))

    implementation(libs.androidx.core.ktx)

    // 登录态 Cookie 加密存储
    implementation(libs.androidx.security.crypto)

    implementation(libs.okhttp)
    implementation(libs.jsoup)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    // Android 的 org.json 在 JVM 单测中是空壳（调用即抛 "not mocked"），
    // 引入真实实现才能验证 GitHub Releases 的 JSON 解析。
    testImplementation(libs.org.json)
}