import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val nonogramEnv = providers.gradleProperty("nonogram.env").getOrElse("dev")

kotlin {
    jvm("desktop")

    sourceSets {
        named("desktopMain") {
            kotlin.srcDir("src/$nonogramEnv/kotlin")
            dependencies {
                implementation(projects.shared)

                implementation(compose.desktop.currentOs)
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.trainpaths.nonogram.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Nonogram"
            packageVersion = "1.0.7"
            // sqlite-jdbc needs java.sql; Firestore's grpc/netty stack reaches for Unsafe
            modules("java.sql", "jdk.unsupported")
        }

        // Firestore's grpc stack under ProGuard is not worth the keep rules
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
