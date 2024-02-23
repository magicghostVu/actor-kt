import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    id("maven-publish")
}

group = "org.magicghostvu"
version = "1.0"

repositories {
    mavenCentral()
}

//
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    implementation("org.slf4j:slf4j-api:1.7.36")

    runtimeOnly("ch.qos.logback:logback-classic:1.2.11")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs = listOf(
        "-opt-in=kotlin.RequiresOptIn",
        "-Xno-call-assertions",
        "-Xno-receiver-assertions",
        "-Xno-param-assertions"
    )
}

/*tasks.create<Jar>("source_jar") {
    dependsOn("classes")
    archiveClassifier.set("source")
    from(sourceSets["main"].allSource)
}*/
java {
    withSourcesJar()
    //withJavadocJar()
}

publishing {
    repositories {
        maven {
            credentials {
                username = project.properties.getValue("reposilite.user") as String
                password = project.properties.getValue("reposilite.password") as String
            }
            url = uri("http://localhost:3001/releases")
            isAllowInsecureProtocol = true
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {

            groupId = "org.magicghostvu"
            artifactId = "actor-kt"
            version = "0.1.1-SNAPSHOT"

            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
        }
    }
}