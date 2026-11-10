pluginManagement {
    val dependencyMirror = providers.gradleProperty("dependencyMirror").orElse("official").get()
    repositories {
        when (dependencyMirror) {
            "aliyun" -> {
                maven("https://maven.aliyun.com/repository/google")
                maven("https://maven.aliyun.com/repository/central")
                maven("https://maven.aliyun.com/repository/gradle-plugin")
            }
            "huawei" -> maven("https://repo.huaweicloud.com/repository/maven/")
            "official" -> {
                google {
                    content {
                        includeGroupByRegex("com\\.android.*")
                        includeGroupByRegex("com\\.google.*")
                        includeGroupByRegex("androidx.*")
                    }
                }
                mavenCentral()
                gradlePluginPortal()
            }
            else -> throw GradleException("Unknown dependencyMirror: $dependencyMirror. Use official, aliyun or huawei.")
        }
    }
}

dependencyResolutionManagement {
    val dependencyMirror = providers.gradleProperty("dependencyMirror").orElse("official").get()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        when (dependencyMirror) {
            "aliyun" -> {
                maven("https://maven.aliyun.com/repository/google")
                maven("https://maven.aliyun.com/repository/central")
            }
            "huawei" -> maven("https://repo.huaweicloud.com/repository/maven/")
            "official" -> {
                google()
                mavenCentral()
            }
            else -> throw GradleException("Unknown dependencyMirror: $dependencyMirror. Use official, aliyun or huawei.")
        }
    }
}

rootProject.name = "QBili"
include(":app")
