pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "amber-apple"

include(":shared")

include(":innertubex")
project(":innertubex").projectDir = file("vendor/innertubex")

include(":quackjs")
project(":quackjs").projectDir = file("vendor/quackjs")
