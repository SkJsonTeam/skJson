plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}


group = "cz.coffee"
version = "4.0.1"


repositories {
    mavenCentral()
    maven("https://repository.apache.org/content/repositories/snapshots/")
    maven("https://repo.bytecode.space/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.skriptlang.org/releases")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven(url = "https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.eclipse.jetty:jetty-client:12.0.1")
    compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
    compileOnly("com.github.SkriptLang:Skript:2.7.3")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.glassfish.jersey.core:jersey-server:2.34")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("de.tr7zw:item-nbt-api-plugin:2.14.1")
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        filteringCharset = "UTF-8"
        filesMatching(listOf("plugin.yml", "config.yml", "lang/default.lang")) {
            expand(
                "version" to project.version
            )
        }
    }

    shadowJar {
        archiveFileName.set("${project.name}-${project.version} (shaded).jar")
        minimize()
        relocate("de.tr7zw.changeme.nbtapi", "cz.coffee.api.nbt")

        relocate("org.bstats", "cz.coffee.api.bstats")
        exclude("META-INF/*.MF")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("LICENSE")
        exclude("META-INF/maven/de.tr7zw/functional-annotations/*")
    }

    register<Copy>("copyShadowJar") {
        dependsOn(shadowJar)
        from(shadowJar.get().archiveFile)
        into("/home/coffee/mc/plugins")
        rename { "SkJson.jar" }
    }
}
