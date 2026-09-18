import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "com.home.javaswitchmanager"
version = file("VERSION").readText().trim()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.processResources {
    from("VERSION") {
        rename { "app-version.txt" }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.home.javaswitchmanager.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "JavaSwitchManager"
            packageVersion = project.version.toString()
            description = "Cross-platform JDK discovery and Java environment switcher"
            vendor = "Java Switch Manager"
            includeAllModules = true

            windows {
                iconFile.set(project.file("packaging/icons/app-icon.ico"))
                menuGroup = "Java Switch Manager"
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6D045B22-33EC-49B1-8571-D6BD36D6B6FD"
            }

            macOS {
                iconFile.set(project.file("packaging/icons/app-icon.icns"))
                bundleID = "com.home.javaswitchmanager"
                packageName = "Java Switch Manager"
                dockName = "Java Switch Manager"
                minimumSystemVersion = "13.0"
                appCategory = "public.app-category.developer-tools"
            }

            linux {
                iconFile.set(project.file("packaging/icons/app-icon.png"))
                packageName = "java-switch-manager"
                menuGroup = "Development"
                appCategory = "Development"
            }
        }
    }
}

// Compose owns jpackage's resource directory, so update the generated DEB desktop entry.
tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) {
        val displayName = "Java Switch Manager"
        val windowClass = "JavaSwitchManager"
        inputs.property("desktopDisplayName", displayName)
        inputs.property("desktopWindowClass", windowClass)
        doLast {
            val deb = destinationDir.get().asFile.listFiles().orEmpty().single { it.extension == "deb" }
            val unpacked = temporaryDir.resolve("desktop-entry")
            unpacked.deleteRecursively()
            providers.exec {
                commandLine("dpkg-deb", "--raw-extract", deb.absolutePath, unpacked.absolutePath)
            }.result.get().assertNormalExitValue()
            val desktop = unpacked.walkTopDown().single { it.isFile && it.extension == "desktop" }
            desktop.writeText(
                desktop.readLines()
                    .filterNot { it.startsWith("StartupWMClass=") }
                    .joinToString("\n") { if (it.startsWith("Name=")) "Name=$displayName" else it }
                    .trimEnd() + "\nStartupWMClass=$windowClass\n"
            )
            providers.exec {
                commandLine("dpkg-deb", "--build", "--root-owner-group", unpacked.absolutePath, deb.absolutePath)
            }.result.get().assertNormalExitValue()
            unpacked.deleteRecursively()
        }
    }
}
