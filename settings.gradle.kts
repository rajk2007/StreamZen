// https://developer.android.com/build#settings-file
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://jitpack.io")
            credentials {
                username = System.getenv("JITPACK_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "NovaCast"
include(":app", ":library", ":docs")
