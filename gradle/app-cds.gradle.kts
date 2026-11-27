// AppCDS for the macOS app (desktop/AppCds.kt): after the app image is made and libVLC's plugin
// cache is in it, the app is started once in training mode and an archive of its classes is added,
// so it starts about a second sooner. The dmg is built from that app image. If anything fails,
// the app ships without the archive.
if (System.getProperty("os.name").startsWith("Mac")) {
    for (type in listOf("", "Release")) {
        val appDir = layout.buildDirectory.dir(if (type == "") "compose/binaries/main/app" else "compose/binaries/main-release/app")
        val cds = tasks.register<JavaExec>("appCds$type") {
            description = "Adds an AppCDS archive to the ${type.ifEmpty { "debug" }} app, so it starts faster."
            classpath = project.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
            mainClass.set("com.ks.jaiplayer.desktop.AppCdsKt")
            args(appDir.get().asFile.path)
            dependsOn("create${type}Distributable")
        }
        // After the plugin cache (it re-seals the app too), before any installer.
        tasks.matching { it.name == "vlcPluginCache$type" }.configureEach { cds.get().dependsOn(this) }
        tasks.matching { it.name.startsWith("package$type") && it.name != "package${type}UberJarForCurrentOS" && (type != "" || !it.name.startsWith("packageRelease")) }
            .configureEach { dependsOn(cds) }
    }
}
