plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("java")
    id("io.ktor.plugin") version "2.3.8"
    id("io.github.goooler.shadow") version "8.1.8"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("dev.onvoid.webrtc:webrtc-java:0.14.0")
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:0.14.0:linux-x86_64")
    runtimeOnly("dev.onvoid.webrtc:webrtc-java:0.14.0:windows-x86_64")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-server-core:2.3.8")
    implementation("io.ktor:ktor-server-netty:2.3.8")
    implementation("io.ktor:ktor-server-websockets:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-client-cio:2.3.8")       // Silnik klienta
    implementation("io.ktor:ktor-client-websockets:2.3.8") // Obsługa WebSockets
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

group = "org.lewapnoob.KapeLuz"
val MainClass = "org.lewapnoob.KapeLuz.KapeLuzKt"

application {
    mainClass.set(MainClass)
    applicationName = "KapeLuz"
}

tasks {
    shadowJar {
        archiveFileName.set("KapeLuz.jar")
        mergeServiceFiles()
        manifest {
            attributes["Main-Class"] = MainClass
        }
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = MainClass
    }
}

tasks.jar {
    archiveBaseName.set("KapeLuz_RAW")
    archiveVersion.set("")
    archiveClassifier.set("")
}