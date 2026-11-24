pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ClassicGames"

// core   - game rules, rooms and the WebSocket protocol (plain Kotlin/JVM, used by both the PC server and the phone)
// server - the PC launcher: a tiny main() around core
// app    - the Android app (client, and it can also run core's server inside the phone)
include(":core", ":server", ":app")
