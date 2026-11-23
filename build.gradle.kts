import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import java.security.MessageDigest

plugins { application; java }
group = "io.github.assafep.jobstein"
version = "0.1.0-SNAPSHOT"
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

val java21Launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
val runtimeImage = layout.buildDirectory.dir("runtime")
// The Google token endpoint needs elliptic-curve TLS support from SunEC.
val runtimeModules = "java.base,java.desktop,java.net.http,jdk.httpserver,jdk.crypto.ec"

val createRuntimeImage = tasks.register<Exec>("runtimeImage") {
    inputs.files(tasks.named("jar"), configurations.runtimeClasspath)
    inputs.property("runtimeModules", runtimeModules)
    outputs.dir(runtimeImage)
    dependsOn(tasks.named("jar"))
    doFirst {
        val javaHome = java21Launcher.get().metadata.installationPath.asFile
        val jlink = javaHome.resolve("bin/jlink")
        val output = runtimeImage.get().asFile
        delete(output)
        commandLine(
            jlink,
            "--add-modules", runtimeModules,
            "--output", output,
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=zip-6"
        )
    }
    doLast {
        // jlink intentionally creates read-only legal-notice files. Make the image
        // copyable by installDist when a developer rebuilds without clean.
        runtimeImage.get().asFile.walkTopDown()
            .filter { it.isFile }
            .forEach { it.setWritable(true) }
    }
}
dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application {
    mainClass.set("io.github.assafep.jobstein.cli.Jobstein")
    // PDFBox uses a JDK-internal cleaner when available. Keep the opening
    // narrowly scoped to java.nio and only to the unnamed application module.
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

distributions {
    main {
        contents {
            from(runtimeImage) {
                into("runtime")
                filePermissions { unix("rwxr-xr-x") }
            }
        }
    }
}

tasks.withType<CreateStartScripts>().configureEach {
    doLast {
        val unix = unixScript
        unix.writeText(unix.readText().replace(Regex("(?s)# Determine the Java command to use to start the JVM\\..*?\\n# Increase the maximum file descriptors")) { _ -> """
# Determine the Java command to use to start the JVM. Prefer the bundled Java 21 runtime.
if [ -x "${'$'}APP_HOME/runtime/bin/java" ] ; then
    JAVACMD="${'$'}APP_HOME/runtime/bin/java"
elif [ -n "${'$'}JAVA_HOME" ] ; then
    JAVACMD="${'$'}JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1 ; then
    JAVACMD=java
else
    die "ERROR: the bundled Java runtime is missing and no Java installation was found."
fi

# Increase the maximum file descriptors""".trimIndent() })

        val windows = windowsScript
        windows.writeText(windows.readText().replace(Regex("(?s)@rem Find java\\.exe.*?:execute")) { _ -> """
@rem Find Java, preferring the bundled Java 21 runtime.
if exist "%APP_HOME%\\runtime\\bin\\java.exe" (
    set JAVA_EXE=%APP_HOME%\\runtime\\bin\\java.exe
    goto execute
)
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
1>&2 echo ERROR: the bundled Java runtime is missing and no Java installation was found.
"%COMSPEC%" /c exit 1
goto exitWithErrorLevel

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
1>&2 echo ERROR: JAVA_HOME does not point to a valid Java installation.
"%COMSPEC%" /c exit 1
goto exitWithErrorLevel

:execute""".trimIndent() })
    }
}

tasks.named<Sync>("installDist") {
    dependsOn(createRuntimeImage)
    doFirst { delete(destinationDir.resolve("runtime")) }
}
tasks.named("distZip") { dependsOn(createRuntimeImage) }
tasks.named("distTar") { dependsOn(createRuntimeImage) }
tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}
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
