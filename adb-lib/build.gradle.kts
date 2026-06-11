plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.young.lib.adb"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.nobeginning"
                artifactId = "adb-lib"
                version = rootProject.findProperty("VERSION_NAME")?.toString() ?: "1.0.0"

                pom {
                    name.set("ADB Library")
                    description.set("Pure Kotlin ADB client library for Android")
                    url.set("https://github.com/nobeginning/ADBTools")
                }
            }
        }
    }
}
