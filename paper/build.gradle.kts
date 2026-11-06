plugins {
    `java-library`
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
