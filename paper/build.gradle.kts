import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    `java-library`
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.243-stable")
    testImplementation("io.papermc.paper:paper-api:26.3.build.243-stable")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.3:4.119.0")
}

tasks.jar {
    archiveBaseName.set("planetoids")
    from(project(":core").sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

val pluginVersion = project.version.toString()
tasks.processResources {
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version.toString())
        id.set("planetoids")
        channel.set("Release")
        apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
        changelog.set(providers.gradleProperty("hangarChangelog").orElse("See the GitHub release notes."))

        platforms {
            register(Platforms.PAPER) {
                jar.set(
                    providers.gradleProperty("hangarJar")
                        .map { layout.projectDirectory.file(it) }
                        .orElse(tasks.jar.flatMap { it.archiveFile })
                )
                platformVersions.set(listOf("26.3"))
            }
        }
    }
}
