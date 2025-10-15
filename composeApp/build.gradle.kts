import org.gradle.kotlin.dsl.invoke
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersion: String by project

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.moko.resources)
}

kotlin {
    // Android-Ziel korrekt registrieren
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Desktop-Ziel
    jvm("desktop")

    // Deaktivierte Ziele (können später wieder aktiviert werden)
    /*
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    wasmJs {
        moduleName = "composeApp"
        browser()
    }
    */

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.android)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
//            implementation(compose.material3)
            implementation(libs.compose.material3.experimental)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended) // Korrekter Weg für Icons

            implementation(libs.kotlinx.datetime)

            // AndroidX Lifecycle
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Coil
            implementation(libs.coil.compose)
            implementation(libs.coil.network)

            // Supabase
            implementation(libs.supabase.postgrest)

            // Moko Resources
            implementation(libs.moko.resources)
            implementation(libs.moko.resources.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.moko.resources.test)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.cio) // Ktor Client für Desktop
        }
    }
}

android {
    namespace = "de.pantastix.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "de.pantastix.project.androidApp" // Eindeutige ID für die Android-App
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = appVersion
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "de.pantastix.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TCGM"
            packageVersion = appVersion

            vendor = "Pantastix"

            modules("java.sql")
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))


            windows {
                shortcut = true
                menu = true
                upgradeUuid = "2A721014-35A1-4A2E-83C8-A06175A384A9"
            }

            linux {
                shortcut = true
            }
        }
    }
}

sqldelight {
    databases {
        create("CardDatabase") {
            packageName.set("de.pantastix.project.db.cards")

            // Point to the dedicated source root for this database.
            // SQLDelight will look for .sq files inside this folder.
            srcDirs.setFrom("src/commonMain/sqldelight/cardDatabase")
            version = 1
        }
        create("SettingsDatabase") {
            // The package for the generated Kotlin API
            packageName.set("de.pantastix.project.db.settings")

            // Point to the dedicated source root for this database.
            srcDirs.setFrom("src/commonMain/sqldelight/settingsDatabase")
            version = 1
        }
    }
}

multiplatformResources{
    resourcesPackage.set("de.pantastix.project.shared.resources")
}