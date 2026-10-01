plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2"

stonecutter parameters {
    swaps["experiment_click_guard"] = if (current.parsed >= "26.3") {
        "if (currentHandler != null) {"
    } else {
        "if (currentHandler != null && mc.screen is AbstractContainerScreen<*>) {"
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds DancingSquared for every supported Minecraft version."
    dependsOn(stonecutter.tasks.named("build"))
}
