import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 纯 Kotlin 领域模型模块。
 *
 * 此前为 Android 库并用 KSP 引入 Room 编译器来承载 @Entity 注解（M-18）。持久化实体已迁入
 * `:data` 的 `app.amisles.hanime.data.local.entity`，本模块不再需要 Android 插件与 Room：
 * - 领域层不再感知持久化框架，方向依赖保持干净；
 * - 单测无需 Android 环境，构建与测试更快；
 * - 编译期即禁止引入任何 Android / 基础设施 API。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Test
    testImplementation(libs.junit)
}
