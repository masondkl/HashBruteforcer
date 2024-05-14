plugins {
    kotlin("jvm").version("1.9.0")
}

group = "me.mason"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.github.masondkl:Plinth:master-SNAPSHOT")
}