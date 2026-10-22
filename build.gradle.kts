// Die Kotlin-Plugins werden hier einmal geladen (ohne sie anzuwenden); die Module nutzen sie per id ohne Version.
// Sonst meldet Gradle „Kotlin Gradle plugin was loaded multiple times in different subprojects".
// Das Android-Gradle-Plugin steht bewusst nicht hier: es wird nur von :app gebraucht, und :core soll sich
// auch ohne Android-Werkzeuge bauen lassen.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
