import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val nonogramEnv = providers.gradleProperty("nonogram.env").getOrElse("dev")
val isProd = nonogramEnv == "prod"
val versionBase = providers.gradleProperty("nonogram.version").getOrElse("1.1")
val versionPatch = providers.gradleProperty("nonogram.versionPatch").getOrElse("0")

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        named("desktopMain") {
            kotlin.srcDir("src/$nonogramEnv/kotlin")
            dependencies {
                implementation(projects.shared)

                implementation(compose.desktop.currentOs)
                implementation(libs.compose.components.resources)
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.trainpaths.nonogram.desktop.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.trainpaths.nonogram.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = if (isProd) "nonogram" else "nonogram-dev"
            packageVersion = "$versionBase.$versionPatch"
            vendor = "trainpaths"
            // sqlite-jdbc needs java.sql; Firestore's grpc/netty stack reaches for Unsafe
            modules("java.sql", "jdk.unsupported")

            linux {
                iconFile.set(project.file("src/desktopMain/composeResources/drawable/icon.png"))
                debMaintainer = "dev@noahbachmann.ch"
                appCategory = "Game"
                menuGroup = "Games"
                shortcut = true
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                upgradeUuid = if (isProd) {
                    "8fd365e4-c993-44a9-bdf0-d5c674d38286"
                } else {
                    "3e781c5d-c42f-4999-90e1-6a32a681804c"
                }
                menuGroup = if (isProd) "Nonogram" else "Nonogram (dev)"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
            }
            macOS { iconFile.set(project.file("icons/icon.icns")) }
        }

        // Firestore's grpc stack under ProGuard is not worth the keep rules
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
