import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    id("net.fabricmc.fabric-loom") version "1.16.2"
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

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    inputs.property("noammaddons_version", project.property("noammaddons_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft_version" to project.property("minecraft_version")!!, "loader_version" to project.property("loader_version")!!, "kotlin_loader_version" to project.property("kotlin_loader_version")!!, "noammaddons_version" to project.property("noammaddons_version")!!)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
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
        isIdeConfigGenerated = true
        vmArg("-XX:+AllowEnhancedClassRedefinition")
    }

    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

afterEvaluate {
    loom.runs.named("client") {
        vmArg("-javaagent:${configurations.compileClasspath.get().find { "sponge-mixin" in it.name }}")
    }
}