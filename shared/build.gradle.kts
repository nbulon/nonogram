@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    /*listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }*/

    // Chromium's sandbox can't start inside the CI docker container
    val useNoSandbox = providers.environmentVariable("CI").isPresent

    js {
        browser {
            testTask {
                useKarma {
                    if (useNoSandbox) useChromeHeadlessNoSandbox() else useChromeHeadless()
                }
            }
        }
        // Compose MP 1.12+ requires an executable binary so browser tests bundle the Skiko runtime (CMP-4906)
        binaries.executable()
        // Firebase externals are @JsModule-only, which UMD can't link
        useEsModules()
    }

    wasmJs {
        browser {
            testTask {
                useKarma {
                    if (useNoSandbox) useChromeHeadlessNoSandbox() else useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    jvm("desktop")

    android {
        namespace = "com.trainpaths.nonogram.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    // Manual dependsOn edges would otherwise switch the template (and so webMain) off
    applyDefaultHierarchyTemplate()

    sourceSets {
        // Android + desktop: everything written against gitlive Firebase / kmpauth / Compose rather than an Android API
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kmpauth.firebase)
                implementation(libs.gitlive.firebase.auth)
                implementation(libs.gitlive.firebase.firestore)
            }
        }
        androidMain {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.koin.android)
                implementation(libs.koin.androidx.compose)

                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.auth)
                implementation(libs.firebase.firestore)
            }
        }
        named("desktopMain") {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.navigation.compose)

            implementation(libs.multiplatform.settings)
            implementation(libs.kmpauth.google)
            implementation(libs.kmpauth.uihelper)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.sqldelight.web.worker.driver)
            implementation(npm("firebase", "12.13.0"))
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

sqldelight {
    databases {
        create("NonogramDb") {
            packageName.set("com.trainpaths.nonogram.cache")
            generateAsync.set(true)
        }
    }
}