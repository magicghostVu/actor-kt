import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
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
    //runtimeOnly("ch.qos.logback:logback-classic:1.2.11")
    //testImplementation(kotlin("test"))
}

/*tasks.test {
    useJUnitPlatform()
}*/

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
            credentials(HttpHeaderCredentials::class.java) {
                name = "Private-Token"
                value = project.properties.getValue("deploy.token") as String
            }
            url = uri("")
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {

            groupId = "org.m.magicghostvu"
            artifactId = "actor-kt"
            version = "0.1.2-SNAPSHOT"

            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
        }
    }
}