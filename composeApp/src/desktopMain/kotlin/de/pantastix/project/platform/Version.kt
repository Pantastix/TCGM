package de.pantastix.project.platform

actual fun getAppVersion(): String {
    return try {
        val mainClass = Class.forName("de.pantastix.project.MainKt")
        mainClass.getPackage().implementationVersion ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }
}