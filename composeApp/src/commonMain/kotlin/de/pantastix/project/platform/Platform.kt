package de.pantastix.project.platform

enum class Platform {
    Desktop, Android, iOS, WasmJs
}

expect fun getPlatform(): Platform