pluginManagement {
    repositories {
        // 阿里云镜像 - Google Maven
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 阿里云镜像 - Gradle 插件
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 阿里云镜像 - Maven Central (含 jcenter / public)
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 阿里云镜像 - Maven Central 直连
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像 - Google Maven
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 阿里云镜像 - Maven Central (含 jcenter / public)
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 阿里云镜像 - Maven Central 直连
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

rootProject.name = "ADBTools"
include(":app")
include(":adb-lib")
 