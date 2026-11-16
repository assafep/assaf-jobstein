plugins { application; java }
group = "io.github.assafep.jobstein"
version = "0.1.0-SNAPSHOT"
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
dependencies {
    implementation("info.picocli:picocli:4.7.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application { mainClass.set("io.github.assafep.jobstein.cli.Jobstein") }
tasks.test { useJUnitPlatform() }
