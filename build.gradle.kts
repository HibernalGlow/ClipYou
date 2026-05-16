plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

tasks.register("installGitHooks") {
    group = "verification"
    description = "Configures Git to use repository hooks from .githooks."
    notCompatibleWithConfigurationCache("Uses external git process and mutable repository state.")

    doLast {
        val hooksDir = rootProject.file(".githooks")
        if (!hooksDir.exists()) {
            return@doLast
        }

        if (!rootProject.file(".git").exists()) {
            return@doLast
        }

        val process = ProcessBuilder("git", "config", "core.hooksPath", ".githooks")
            .directory(rootProject.projectDir)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode == 0) {
            val preCommit = hooksDir.resolve("pre-commit")
            if (preCommit.exists()) {
                preCommit.setExecutable(true)
            }
        }
    }
}