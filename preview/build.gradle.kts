// Renders the game's procedural art to PNG on a desktop JVM, so visuals can be reviewed
// without a phone:  ./gradlew :preview:run   →   preview/build/previews/*.png
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

val gameSrc = rootProject.file("app/src/main/java")

sourceSets {
    main {
        kotlin.srcDir(gameSrc)
        kotlin.include(
            "com/junglerun/game/preview/**",
            "com/junglerun/game/core/**",
            "com/junglerun/game/art/**",
            "com/junglerun/game/gfx/Gfx.kt",
            "com/junglerun/game/game/Entities.kt",
            "com/junglerun/game/game/WorldGen.kt",
            "com/junglerun/game/game/GameWorld.kt",
            "com/junglerun/game/game/GameRenderer.kt",
            "com/junglerun/game/game/AutoPilot.kt",
        )
    }
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.junglerun.game.preview.ScenePreviewKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

tasks.named<JavaExec>("run") {
    args(layout.buildDirectory.dir("previews").get().asFile.absolutePath)
}
