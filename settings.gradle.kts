pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Aliyun mirrors as fallback (JitPack builds from overseas, needs standard repos first)
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Aliyun mirrors as fallback
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

rootProject.name = "ADBTools"
include(":app")
include(":adb-lib")
 