import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.20"
    id("net.fabricmc.fabric-loom") version "1.17.19"
    id("maven-publish")
    id ("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

repositories {
    maven { url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") }
    maven("https://repo.essential.gg/repository/maven-public") {
        name = "Essential"
        content {
            includeGroup("gg.essential")
        }
    }
    maven("https://jitpack.io") {
        name = "JitPack"
        content {
            includeGroup("com.github.Noamm9")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    include("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.github.Noamm9:NoammAddons:${project.property("noammaddons_version")}:${project.property("noammaddons_type")}")
}

val resourceProperties = mapOf(
    "version" to project.version,
    "minecraft_version" to project.property("minecraft_version"),
    "loader_version" to project.property("loader_version"),
    "kotlin_loader_version" to project.property("kotlin_loader_version"),
    "noammaddons_version" to project.property("noammaddons_version")
)

tasks.processResources {
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(resourceProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.named<JavaExec>("runClient") {
    classpath = classpath.filter { it.exists() }
}

tasks.jar {
    from("LICENSE") {
        rename { "LICENSE_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}

loom {
    runConfigs.named("client") {
        generateRunConfig.set(true)
        jvmArguments.add("-XX:+AllowEnhancedClassRedefinition")
    }

    runConfigs.named("server") {
        generateRunConfig.set(false)
    }
}

afterEvaluate {
    loom.runs.named("client") {
        jvmArguments.add("-javaagent:${configurations.compileClasspath.get().find { "sponge-mixin" in it.name }}")
    }
}