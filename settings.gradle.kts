pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hc_app"

include(":core")

// Das Android-Modul braucht SDK und Android-Gradle-Plugin. Wo beides fehlt (CI ohne SDK, Container),
// lässt sich :core trotzdem bauen und testen.
val androidVorhanden = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
if (androidVorhanden) {
    include(":app")
}
