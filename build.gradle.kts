import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
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
