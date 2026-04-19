plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<JavaCompile>().configureEach {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}

val bqApiJar by tasks.registering(org.gradle.jvm.tasks.Jar::class) {
    group = "build"
    description = "Builds an API-only jar without @Mod bootstrap and mixin implementation classes."
    archiveClassifier.set("runtime-api")

    from(sourceSets.main.get().output)

    // Keep the default mod jar intact; this classified jar is compile/jar-in-jar API surface.
    exclude("com/hfstudio/bqapi/BQApiMod.class")
    exclude("com/hfstudio/bqapi/mixins/**")
    exclude("mixins.bqapi.json")
    exclude("mixins.bqapi.late.json")
}

tasks.named("build") {
    dependsOn(bqApiJar)
}

artifacts {
    add("archives", bqApiJar)
}
