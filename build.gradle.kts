import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.security.MessageDigest

plugins { application; java }
group = "io.github.assafep.jobstein"
version = "0.1.0-SNAPSHOT"
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application { mainClass.set("io.github.assafep.jobstein.cli.Jobstein") }
tasks.test { useJUnitPlatform() }
tasks.withType<AbstractArchiveTask>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
tasks.register("distributionChecksums") {
    dependsOn("distZip", "distTar")
    doLast {
        val directory = layout.buildDirectory.dir("distributions").get().asFile
        directory.listFiles { file -> file.extension == "zip" || file.extension == "tar" }
            ?.sortedBy { it.name }
            ?.forEach { archive ->
                val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                archive.resolveSibling("${archive.name}.sha256").writeText("$digest  ${archive.name}\n")
            }
    }
}
