import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.googleServices) apply false
}

/**
 * Yarn resolutions forcing patched versions of transitive npm deps whose parents pin a vulnerable range.
 * Applied to both the js and the wasm lockfile. Drop an entry once its parent ships the fix.
 */
val yarnResolutions = mapOf(
    // firebase pins ~1.9 (node-only transport, not in the browser bundle)
    "@grpc/grpc-js" to "1.14.7",
    // Kotlin's bundled mocha 11 pins ^6
    "serialize-javascript" to "7.1.4",
    // Kotlin's bundled test tooling (mocha 11, karma-webpack 5): fixes only in newer majors
    "**/mocha/diff" to "8.0.4",
    "**/mocha/glob" to "14.0.6",
    "**/mocha/minimatch" to "11.0.8",
    "**/karma-webpack/minimatch" to "11.0.8",
)

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply { yarnResolutions.forEach { (path, version) -> resolution(path, version) } }
}
plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootExtension>().apply { yarnResolutions.forEach { (path, version) -> resolution(path, version) } }
}
// KGP doesn't track resolutions as input: without this an edit leaves the root package.json stale (UP-TO-DATE)
tasks.matching { it.name == "rootPackageJson" || it.name == "wasmRootPackageJson" }.configureEach {
    inputs.property("yarnResolutions", yarnResolutions)
}
