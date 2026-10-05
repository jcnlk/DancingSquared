plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

tasks.register("buildAll") {
    group = "build"
    description = "Builds DancingSquared for every supported Minecraft version."
    dependsOn(stonecutter.tasks.named("build"))
}
