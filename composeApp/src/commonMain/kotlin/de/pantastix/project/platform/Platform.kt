package de.pantastix.project.platform

enum class Platform {
    Windows, Linux, Mac, Android, iOS, WasmJs
}

expect fun getPlatform(): Platform