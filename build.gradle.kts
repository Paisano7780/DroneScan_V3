// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Alias para el plugin de aplicación de Android, definido en libs.versions.toml
    alias(libs.plugins.android.application) apply false
    // Alias para el plugin de Kotlin para Android, definido en libs.versions.toml
    alias(libs.plugins.kotlin.android) apply false
}