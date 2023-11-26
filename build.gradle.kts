buildscript {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
            url= uri("https://maven.google.com")
        }
        gradlePluginPortal()
    }
    dependencies {
        classpath ("com.android.tools.build:gradle:8.1.4")
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
        classpath ("com.google.dagger:hilt-android-gradle-plugin:2.47")
        classpath ("com.google.gms:google-services:4.4.0")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:10.2.0")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
