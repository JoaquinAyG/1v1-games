plugins {
    id("com.android.application")
    // AGP 9 compiles Kotlin itself; these two plugins only add Compose + @Serializable support.
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.classicgames.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.classicgames.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Keep the APK named after the app (the module is called "app").
    base.archivesName.set("ClassicGames")

    packaging {
        resources {
            // The server libraries ship licence/index files that would otherwise collide when merged into the APK.
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

val ktorVersion = "3.2.4"

dependencies {
    // Game rules, protocol messages and the server itself (so the phone can host a server too).
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
}
