import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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

// Downsample in stages to avoid aliasing when reducing the detailed source icon.
val generateAppIcons by tasks.registering {
    val sourceFile = layout.projectDirectory.file("src/main/resources/icons/app-icon.png")
    val output = layout.buildDirectory.dir("generated/app-icons")
    inputs.file(sourceFile)
    outputs.dir(output)
    doLast {
        val source = ImageIO.read(sourceFile.asFile)
        for (size in listOf(16, 24, 32, 48, 64, 96, 128, 256, 512)) {
            var scaled = source
            while (scaled.width > size) {
                val nextSize = maxOf(size, scaled.width / 2)
                val next = BufferedImage(nextSize, nextSize, BufferedImage.TYPE_INT_ARGB_PRE)
                val graphics = next.createGraphics()
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    graphics.drawImage(scaled, 0, 0, nextSize, nextSize, null)
                } finally {
                    graphics.dispose()
                }
                scaled = next
            }
            val destination = output.get().file("icons/sizes/app-icon-$size.png").asFile
            destination.parentFile.mkdirs()
            check(ImageIO.write(scaled, "png", destination))
        }
    }
}

tasks.processResources {
    from(generateAppIcons)
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
        mainClass = "JavaSwitchManager"

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
        val iconName = "com.home.javaswitchmanager"
        val linuxIcon = layout.projectDirectory.file("packaging/icons/app-icon.png")
        val linuxMetadata = layout.projectDirectory.file("packaging/linux/com.home.javaswitchmanager.metainfo.xml")
        inputs.property("desktopDisplayName", displayName)
        inputs.property("desktopWindowClass", windowClass)
        dependsOn(generateAppIcons)
        inputs.files(generateAppIcons)
        inputs.file(linuxIcon)
        inputs.file(linuxMetadata)
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
                    .joinToString("\n") {
                        when {
                            it.startsWith("Name=") -> "Name=$displayName"
                            it.startsWith("Icon=") -> "Icon=$iconName"
                            else -> it
                        }
                    }
                    .trimEnd() + "\nStartupWMClass=$windowClass\n"
            )
            // Ship the launcher and icons as package files, so desktop environments and
            // package viewers can discover them without running the installation scripts.
            val installedDesktop = unpacked.resolve("usr/share/applications/${desktop.name}")
            installedDesktop.parentFile.mkdirs()
            desktop.copyTo(installedDesktop, overwrite = true)
            val installedMetadata = unpacked.resolve("usr/share/metainfo/${linuxMetadata.asFile.name}")
            installedMetadata.parentFile.mkdirs()
            linuxMetadata.asFile.copyTo(installedMetadata, overwrite = true)
            for (size in listOf(16, 24, 32, 48, 64, 96, 128, 256, 512)) {
                val sourceIcon = layout.buildDirectory.file("generated/app-icons/icons/sizes/app-icon-$size.png").get().asFile
                val iconFile = unpacked.resolve("usr/share/icons/hicolor/${size}x${size}/apps/$iconName.png")
                iconFile.parentFile.mkdirs()
                sourceIcon.copyTo(iconFile, overwrite = true)
            }
            val refreshDesktopCaches = """
                if command -v gtk-update-icon-cache >/dev/null 2>&1; then
                    gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
                fi
                if command -v update-desktop-database >/dev/null 2>&1; then
                    update-desktop-database -q /usr/share/applications || true
                fi
            """.trimIndent()
            for (scriptName in listOf("postinst", "postrm")) {
                val script = unpacked.resolve("DEBIAN/$scriptName")
                script.writeText(script.readText().replace("\nexit 0", "\n$refreshDesktopCaches\n\nexit 0"))
            }
            providers.exec {
                commandLine("dpkg-deb", "--build", "--root-owner-group", unpacked.absolutePath, deb.absolutePath)
            }.result.get().assertNormalExitValue()
            unpacked.deleteRecursively()
        }
    }
}
