@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val nonogramEnv = providers.gradleProperty("nonogram.env").getOrElse("dev")

kotlin {
    js {
        browser()
        binaries.executable()
        // Firebase externals are @JsModule-only, which UMD can't link
        useEsModules()
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)

            implementation(libs.compose.ui)
        }
        webMain {
            kotlin.srcDir("src/$nonogramEnv/kotlin")
            dependencies {
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
                implementation(npm("@sqlite.org/sqlite-wasm", "3.50.4-build1"))
                implementation(devNpm("copy-webpack-plugin", "14.0.0"))
            }
        }
    }
}
