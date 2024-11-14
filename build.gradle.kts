plugins {
    kotlin("jvm") version "2.0.21"
}

group = "nl.endevelopment"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bytedeco:llvm:17.0.6-1.5.10")
    implementation("org.bytedeco:llvm-platform:17.0.6-1.5.10")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}