plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.okhttp)
    implementation(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

compose.desktop {
    application {
        mainClass = "io.github.lastnyx.hermesclient.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
            )
            packageName = "HermesClient"
            description = "Hermes client: every Hermes gateway in one window"
            vendor = "LastNyx"
            modules("java.net.http", "jdk.crypto.ec")
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                upgradeUuid = "6f1c2b8e-4d1a-4f3b-9a2e-7c5d0e8b1a47"
            }
            packageVersion = (System.getenv("APP_VERSION")?.removePrefix("v") ?: "0.1.0")
                .split('-').first()
        }
    }
}
