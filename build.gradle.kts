plugins {
    kotlin("jvm") version "2.0.21"
    antlr
}

group = "nl.endevelopment"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.antlr:antlr4-runtime:4.12.0") // ANTLR runtime
    antlr("org.antlr:antlr4:4.12.0") // ANTLR tool for generating parser
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

tasks.generateGrammarSource {
    // Specify that we want to generate a visitor
    arguments.plusAssign(listOf("-visitor", "-package", "nl.endevelopment.parser"))
    // Specify the output directory for generated sources
    outputDirectory = file("build/generated-src/antlr/main/nl/endevelopment/parser")
}

sourceSets.main{
    java {
        // Include the generated ANTLR sources
        srcDir("build/generated-src/antlr/main")
    }
    kotlin {
        // Ensure Kotlin also looks into the generated sources if needed
        srcDir("build/generated-src/antlr/main")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    // Ensure Kotlin compilation depends on ANTLR source generation
    dependsOn("generateGrammarSource")
}