buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("java")
    id("maven-publish")
    id("com.github.johnrengelman.shadow").version("7.1.0")
    id("io.github.gradle-nexus.publish-plugin").version("1.1.0")
}

group = "dev.ckateptb"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.jyraf.com/repository/maven-snapshots/")
}

dependencies {
    implementation("dev.ckateptb:Reflect:1.0.1-SNAPSHOT")

    compileOnly("org.projectlombok:lombok:+")
    annotationProcessor("org.projectlombok:lombok:+")
}

tasks {
    shadowJar {
        dependencies {
            exclude("META-INF/**")
        }
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
    publish {
        dependsOn(shadowJar)
    }
    withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(16)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.shadowJar.get().outputs.files.singleFile)
        }
    }
}

nexusPublishing {
    repositories {
        create("jyrafRepo") {
            nexusUrl.set(uri("https://repo.jyraf.com/"))
            snapshotRepositoryUrl.set(uri("https://repo.jyraf.com/repository/maven-snapshots/"))
            username.set(System.getenv("NEXUS_USERNAME"))
            password.set(System.getenv("NEXUS_PASSWORD"))
        }
    }
}