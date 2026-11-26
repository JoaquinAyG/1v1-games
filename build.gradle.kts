// Plugin versions live here once; the modules apply the plugins without a version.
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
    id("com.google.gms.google-services") version "4.6.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
